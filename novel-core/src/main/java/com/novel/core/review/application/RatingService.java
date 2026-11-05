package com.novel.core.review.application;

import com.novel.common.exception.BizException;
import com.novel.common.result.ResultCode;
import com.novel.core.event.NovelChangeType;
import com.novel.core.event.NovelEventPublisher;
import com.novel.core.novel.domain.Novel;
import com.novel.core.novel.port.NovelRepository;
import com.novel.core.review.application.view.RatingSummaryView;
import com.novel.core.review.port.RatingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;

/**
 * 评分应用服务。
 *
 * <p>核心是"增量维护":明细表 {@code novel_rating} 记谁打了多少分，
 * 聚合值 {@code rating_sum / rating_count / avg_rating} 冗余在 {@code novel} 上。
 * 每次打分只做一次加法和一次减法，永远不跑 {@code AVG()} 全表扫描。
 *
 * <p>算错的兜底是定时对账任务（见 infra 的评分重算任务）：
 * 增量在并发、异常回滚、手工改库之后都可能漂移，每天从明细重算一次把它拉回来。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RatingService {

    private final RatingRepository ratingRepository;
    private final NovelRepository novelRepository;
    private final NovelEventPublisher eventPublisher;

    public RatingSummaryView summary(long novelId, Long currentUserId) {
        Novel novel = requireNovel(novelId);
        Integer myScore = currentUserId == null ? null
                : ratingRepository.findScore(novelId, currentUserId).orElse(null);
        return new RatingSummaryView(novel.avgRating(), novel.ratingCount(),
                myScore, ratingRepository.distribution(novelId));
    }

    /**
     * 打分。同一用户第二次调用是**改分**，不会重复计入人数。
     *
     * <p>隔离级别显式降到 READ COMMITTED 是为了并发首评：
     * 默认的 REPEATABLE READ 下，事务第一次查询就固定了快照，
     * 撞唯一键之后重新查也**看不见**对方刚提交的那行，会再插一次、再撞一次、最后 500。
     * READ COMMITTED 每条语句取新快照，兜底重读才能看到真实数据。
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public RatingSummaryView rate(long novelId, long userId, int score) {
        validateScore(score);
        requireNovel(novelId);

        Optional<Integer> previous = ratingRepository.findScore(novelId, userId);
        if (previous.isEmpty()) {
            try {
                ratingRepository.save(novelId, userId, score);
                novelRepository.addRating(novelId, score, 1);
            } catch (DuplicateKeyException e) {
                // 两个请求同时首次打分，后到的那个撞唯一键：退化成改分逻辑
                int old = ratingRepository.findScore(novelId, userId).orElse(score);
                ratingRepository.save(novelId, userId, score);
                novelRepository.addRating(novelId, (long) score - old, 0);
            }
            notifyChanged(novelId);
        } else if (previous.get() != score) {
            ratingRepository.save(novelId, userId, score);
            // 改分只动总分，人数不变
            novelRepository.addRating(novelId, (long) score - previous.get(), 0);
            notifyChanged(novelId);
        }
        log.info("评分 novelId={} userId={} score={} 原值={}", novelId, userId, score, previous.orElse(null));
        return summary(novelId, userId);
    }

    /** 取消评分。没打过分时静默成功，保持幂等。 */
    @Transactional
    public RatingSummaryView remove(long novelId, long userId) {
        Optional<Integer> previous = ratingRepository.findScore(novelId, userId);
        if (previous.isPresent()) {
            ratingRepository.delete(novelId, userId);
            novelRepository.addRating(novelId, -previous.get(), -1);
            notifyChanged(novelId);
        }
        return summary(novelId, userId);
    }

    /** 发书评时顺带打的星也走这里，保证"评分"只有一个数据来源。 */
    @Transactional
    public void rateQuietly(long novelId, long userId, Integer score) {
        if (score != null) {
            rate(novelId, userId, score);
        }
    }

    private void validateScore(int score) {
        if (score < 1 || score > 5) {
            throw new BizException(ResultCode.BAD_REQUEST, "评分必须是 1 到 5 星");
        }
    }

    private Novel requireNovel(long novelId) {
        return novelRepository.findById(novelId)
                .orElseThrow(() -> new BizException(ResultCode.NOVEL_NOT_FOUND));
    }

    /**
     * 供对账任务复用：把明细重算结果覆盖回 novel。
     *
     * <p>只有"重算值和库里的不一致"时才写库并发事件。对账跑在每天 03:30，索引重建跑在 02:00，
     * 如果这里不补一刀，对账修正过的平均分要等到下一次有人评分、或者次日重建才进索引——
     * 而这正是"搜索页 4.5 分、详情页 4.2 分"的来源。
     *
     * <p>比较的是总分和人数，不是平均分：四舍五入后的平均分可能"两个人都改了一分、均值不变"，
     * 拿它判变化会漏掉真实的修正。
     */
    @Transactional
    public void recalculate(long novelId) {
        RatingRepository.Aggregate aggregate = ratingRepository.aggregate(novelId);
        Novel current = novelRepository.findById(novelId).orElse(null);
        if (current == null) {
            // 书已经被物理删掉了，聚合值没有归宿
            return;
        }
        if (current.ratingSum() == aggregate.sum() && current.ratingCount() == aggregate.count()) {
            // 绝大多数书每天都走到这里：明细和聚合一致，什么都不用做，也就不会产生任何消息
            return;
        }
        novelRepository.setRatingStats(novelId, aggregate.sum(), aggregate.count());
        notifyChanged(novelId);
    }

    /**
     * 评分只影响索引里的 {@code avgRating / ratingCount}，但这两个字段会出现在搜索结果里，
     * 落后一天用户就会发现"搜索页 4.5 分、详情页 4.2 分"。所以评分变化也走同一条同步链路。
     */
    private void notifyChanged(long novelId) {
        eventPublisher.novelChanged(novelId, NovelChangeType.UPDATED);
    }

    public Map<Integer, Long> distribution(long novelId) {
        return ratingRepository.distribution(novelId);
    }
}
