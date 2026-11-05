-- ============================================================================
-- 回填 novel.review_count
--
-- 起因：这一列从 V1 建表起就没有任何写路径——发/删书评只动了 user_stat.review_count
-- 和榜单的实时计数，书级的 review_count 一直是 0。
-- M5 的总榜按它算书评权重，于是日/周/月榜把书评算了进去，总榜却恒为 0，两张榜两套刻度。
--
-- 修法有两半：代码里补上写路径（ReviewService 发/删书评时像 favorite_count 一样加减），
-- 已经存在的数据用这条一次性回填。软删除的书评不算数，与 delete 的扣减语义一致。
--
-- 这条在新装的库上是空操作（novel_review 里没有行），可以放心执行。
-- ============================================================================
UPDATE novel
SET review_count = (SELECT COUNT(*)
                    FROM novel_review r
                    WHERE r.novel_id = novel.id
                      AND r.deleted = 0);
