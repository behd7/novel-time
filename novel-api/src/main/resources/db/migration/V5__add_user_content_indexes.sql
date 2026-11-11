-- 个人主页的"我的书评 / 我的帖子"按 user_id 查、按 created_at 倒序。
-- 原表只有按 novel_id / post_id 的索引，按用户查会走全表扫描。
ALTER TABLE novel_review
    ADD KEY idx_review_user_created (user_id, created_at, id);

ALTER TABLE post
    ADD KEY idx_post_user_created (user_id, created_at, id);
