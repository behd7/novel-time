import type { Review } from '@/api/types';

type ReviewSeed = {
  novelId: number;
  nickname: string;
  score: number;
  content: string;
  likeCount: number;
  replyCount: number;
  createdAt: string;
};

const SEEDS: ReviewSeed[] = [
  {
    novelId: 1, nickname: '青杉', score: 5,
    content: '读到最后才明白，书里记的不是死人，是活过的人。合上书的时候，窗外正好下雨。',
    likeCount: 428, replyCount: 12, createdAt: '2026-11-04T22:18:00',
  },
  {
    novelId: 1, nickname: '阿箬', score: 5,
    content: '作者的句子很短，像刀。但每一刀都落在该落的地方。',
    likeCount: 311, replyCount: 6, createdAt: '2026-11-03T09:42:00',
  },
  {
    novelId: 1, nickname: '老周', score: 4,
    content: '前半本慢，后半本停不下来。长安的烟火气写得好，人物稍多，偶尔要往回翻。',
    likeCount: 156, replyCount: 9, createdAt: '2026-10-31T20:05:00',
  },
  {
    novelId: 1, nickname: '林间', score: 5,
    content: '今年读过最好的历史文。没有之一。',
    likeCount: 502, replyCount: 21, createdAt: '2026-10-28T16:30:00',
  },
  {
    novelId: 2, nickname: '晚风', score: 5,
    content: '车厢那段看得后背发凉。作者很会写「不该存在的东西」，不靠鬼，靠细节。',
    likeCount: 233, replyCount: 8, createdAt: '2026-11-05T01:12:00',
  },
  {
    novelId: 2, nickname: '灯下客', score: 4,
    content: '追到三十章，节奏稳。就是更新太慢，建议养肥。',
    likeCount: 98, replyCount: 3, createdAt: '2026-11-02T23:40:00',
  },
  {
    novelId: 2, nickname: '拾页', score: 5,
    content: '结尾那句「别回头」，我反复看了三遍。',
    likeCount: 187, replyCount: 5, createdAt: '2026-10-30T21:26:00',
  },
  {
    novelId: 3, nickname: '南方有雨', score: 5,
    content: '修仙文里少见的克制。没有一路开挂，主角每一步都在失去。',
    likeCount: 276, replyCount: 10, createdAt: '2026-11-04T18:50:00',
  },
  {
    novelId: 3, nickname: '不系舟', score: 4,
    content: '文笔好，但前期有点闷。熬过第二十章会好很多。',
    likeCount: 112, replyCount: 4, createdAt: '2026-11-01T12:08:00',
  },
  {
    novelId: 3, nickname: '山月', score: 5,
    content: '师父那条线写得太好了。断指那段直接破防。',
    likeCount: 302, replyCount: 15, createdAt: '2026-10-27T19:33:00',
  },
  {
    novelId: 4, nickname: '观棋', score: 5,
    content: '书店的设定很温柔。看完想去找一家这样的店坐一下午。',
    likeCount: 198, replyCount: 7, createdAt: '2026-10-29T15:20:00',
  },
  {
    novelId: 4, nickname: '白露', score: 4,
    content: '感情线慢热，但每一处伏笔都有回收。适合雨天读。',
    likeCount: 87, replyCount: 2, createdAt: '2026-10-26T10:11:00',
  },
  {
    novelId: 6, nickname: '长夜', score: 4,
    content: '作为悬疑文，逻辑基本自洽。最后一章的翻转有点用力，但可以接受。',
    likeCount: 164, replyCount: 11, createdAt: '2026-11-02T22:46:00',
  },
  {
    novelId: 6, nickname: '旧书', score: 5,
    content: '看的时候一直猜凶手，猜到一半发现自己被作者带偏了。服气。',
    likeCount: 289, replyCount: 18, createdAt: '2026-10-30T23:58:00',
  },
  {
    novelId: 9, nickname: '江上', score: 5,
    content: '归墟的设定太美了。「被时间遗忘的名字」这个意象可以记很久。',
    likeCount: 341, replyCount: 14, createdAt: '2026-11-05T20:02:00',
  },
  {
    novelId: 9, nickname: '阿箬', score: 4,
    content: '信息密度很高，读起来需要专心。不适合通勤看。',
    likeCount: 76, replyCount: 3, createdAt: '2026-11-03T13:27:00',
  },
  {
    novelId: 10, nickname: '老周', score: 5,
    content: '写的是拆迁，写的又不只是拆迁。看到最后一段的时候，给我妈打了个电话。',
    likeCount: 412, replyCount: 23, createdAt: '2026-10-25T21:15:00',
  },
  {
    novelId: 10, nickname: '拾页', score: 5,
    content: '很短的篇幅，很长的余味。',
    likeCount: 120, replyCount: 4, createdAt: '2026-10-24T09:36:00',
  },
  {
    novelId: 5, nickname: '林间', score: 5,
    content: '深空的孤独感写出来了。摇篮曲那段是神来之笔。',
    likeCount: 145, replyCount: 5, createdAt: '2026-11-01T01:05:00',
  },
  {
    novelId: 5, nickname: '不系舟', score: 4,
    content: '科幻设定扎实，人物稍弱。期待后面展开。',
    likeCount: 63, replyCount: 2, createdAt: '2026-10-28T22:44:00',
  },
  {
    novelId: 11, nickname: '南方有雨', score: 4,
    content: '末世文看多了，这本的父女线是少见的暖。',
    likeCount: 88, replyCount: 6, createdAt: '2026-11-04T19:12:00',
  },
  {
    novelId: 12, nickname: '白露', score: 5,
    content: '古言的糖不腻人。将军和嫡女都长了嘴，这点很加分。',
    likeCount: 176, replyCount: 8, createdAt: '2026-10-30T17:40:00',
  },
  {
    novelId: 13, nickname: '长夜', score: 4,
    content: '雾都的潮湿感写得好，适合夜里看。',
    likeCount: 54, replyCount: 1, createdAt: '2026-11-02T02:18:00',
  },
  {
    novelId: 7, nickname: '灯下客', score: 5,
    content: '江湖气很足。灯这个意象贯穿全书，越读越有味道。',
    likeCount: 209, replyCount: 9, createdAt: '2026-11-03T20:55:00',
  },
  {
    novelId: 8, nickname: '观棋', score: 4,
    content: '种田文爱好者狂喜。看主角修灶台我都能看十页。',
    likeCount: 91, replyCount: 4, createdAt: '2026-11-01T18:22:00',
  },
  {
    novelId: 14, nickname: '山月', score: 4,
    content: '剑修文里的一股清流。打戏不水，人物不飘。',
    likeCount: 72, replyCount: 3, createdAt: '2026-11-05T11:09:00',
  },
];

/** novelId 只用于前端分组，接口返回的 Review 里没有这个字段。 */
export type MockReview = Review & { novelId: number };

export const reviews: MockReview[] = SEEDS.map((seed, index) => ({
  id: index + 1,
  novelId: seed.novelId,
  userId: 100 + index,
  nickname: seed.nickname,
  avatarKey: null,
  content: seed.content,
  score: seed.score,
  likeCount: seed.likeCount,
  replyCount: seed.replyCount,
  liked: false,
  createdAt: seed.createdAt,
}));

/** 每本书的星级分布（比例），用于详情页的评分条。 */
const DISTRIBUTION: Record<number, [number, number, number, number, number]> = {
  1: [2100, 1100, 430, 150, 62],
  2: [560, 380, 180, 60, 26],
  3: [1320, 720, 270, 88, 33],
  4: [1680, 760, 300, 96, 43],
  5: [430, 320, 140, 50, 24],
  6: [2600, 980, 380, 110, 50],
  7: [890, 560, 270, 110, 43],
  8: [280, 260, 130, 50, 22],
  9: [1750, 830, 300, 90, 42],
  10: [820, 440, 200, 70, 34],
  11: [520, 320, 160, 60, 38],
  12: [1150, 600, 240, 80, 33],
  13: [260, 200, 100, 36, 16],
  14: [880, 540, 220, 90, 34],
};

export function distributionOf(novelId: number): Record<string, number> {
  const values = DISTRIBUTION[novelId] ?? [10, 8, 5, 2, 1];
  return {
    '5': values[0],
    '4': values[1],
    '3': values[2],
    '2': values[3],
    '1': values[4],
  };
}

export function reviewsOf(novelId: number): Review[] {
  // MockReview 比 Review 多一个分组字段，结构上仍满足 Review[]
  return reviews.filter((review) => review.novelId === novelId);
}
