-- ============================================================================
-- V1 初始化表结构
--
-- 全局约定
--   1. utf8mb4 / utf8mb4_0900_ai_ci：中文比较与 emoji 都靠它，别用 utf8（那是 utf8mb3）
--   2. 主键统一 BIGINT UNSIGNED AUTO_INCREMENT，对外只暴露 id，不暴露含义
--   3. 计数类字段（点赞数、评论数、评分总数）冗余在主体表，首页不做 join 聚合
--   4. 软删除统一 deleted TINYINT，配合 MyBatis-Plus 逻辑删除
--   5. 时间统一 DATETIME + 应用侧 Asia/Shanghai，不用 TIMESTAMP（跨时区迁移时它更麻烦）
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 用户域
-- ---------------------------------------------------------------------------
CREATE TABLE `user`
(
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    username      VARCHAR(32)     NOT NULL COMMENT '登录名',
    password_hash VARCHAR(100)    NOT NULL COMMENT 'BCrypt 哈希，绝不存明文',
    phone         VARCHAR(20)              DEFAULT NULL,
    email         VARCHAR(128)             DEFAULT NULL,
    status        TINYINT         NOT NULL DEFAULT 1 COMMENT '1=正常 0=封禁',
    deleted       TINYINT         NOT NULL DEFAULT 0,
    created_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username),
    UNIQUE KEY uk_phone (phone),
    KEY idx_email (email),
    KEY idx_created_at (created_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='用户认证主体';

CREATE TABLE user_profile
(
    user_id    BIGINT UNSIGNED NOT NULL,
    nickname   VARCHAR(32)     NOT NULL,
    avatar_key VARCHAR(255)             DEFAULT NULL COMMENT 'MinIO 对象键，不是完整 URL',
    bio        VARCHAR(255)             DEFAULT NULL,
    gender     TINYINT         NOT NULL DEFAULT 0 COMMENT '0=未知 1=男 2=女',
    birthday   DATE                     DEFAULT NULL,
    created_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id),
    KEY idx_nickname (nickname),
    CONSTRAINT fk_profile_user FOREIGN KEY (user_id) REFERENCES `user` (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='用户展示信息';

CREATE TABLE user_stat
(
    user_id        BIGINT UNSIGNED NOT NULL,
    bookshelf_count INT UNSIGNED   NOT NULL DEFAULT 0,
    review_count   INT UNSIGNED    NOT NULL DEFAULT 0,
    post_count     INT UNSIGNED    NOT NULL DEFAULT 0,
    like_received  INT UNSIGNED    NOT NULL DEFAULT 0,
    updated_at     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id),
    CONSTRAINT fk_stat_user FOREIGN KEY (user_id) REFERENCES `user` (id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='用户统计冗余表，避免主页 count(*)';

-- ---------------------------------------------------------------------------
-- 小说域
-- ---------------------------------------------------------------------------
CREATE TABLE novel_category
(
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    name       VARCHAR(32)     NOT NULL,
    parent_id  BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '0=顶级分类，两级封顶',
    sort_no    INT             NOT NULL DEFAULT 0,
    icon_key   VARCHAR(255)             DEFAULT NULL,
    created_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_parent_name (parent_id, name),
    KEY idx_sort (sort_no)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='小说分类（两级）';

CREATE TABLE novel
(
    id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    title            VARCHAR(128)    NOT NULL,
    author_name      VARCHAR(64)     NOT NULL,
    cover_key        VARCHAR(255)             DEFAULT NULL,
    category_id      BIGINT UNSIGNED NOT NULL,
    status           TINYINT         NOT NULL DEFAULT 0 COMMENT '0=连载中 1=已完结',
    intro            TEXT                     DEFAULT NULL,
    word_count       INT UNSIGNED    NOT NULL DEFAULT 0,
    chapter_count    INT UNSIGNED    NOT NULL DEFAULT 0,
    latest_chapter_id BIGINT UNSIGNED         DEFAULT NULL,
    latest_update_at DATETIME                 DEFAULT NULL,
    -- 评分：存 sum + count，平均分读时算或写冗余列，永远不要 AVG() 全表扫
    rating_sum       INT UNSIGNED    NOT NULL DEFAULT 0,
    rating_count     INT UNSIGNED    NOT NULL DEFAULT 0,
    avg_rating       DECIMAL(3, 2)   NOT NULL DEFAULT 0.00,
    view_count       BIGINT UNSIGNED NOT NULL DEFAULT 0,
    favorite_count   INT UNSIGNED    NOT NULL DEFAULT 0,
    review_count     INT UNSIGNED    NOT NULL DEFAULT 0,
    hot_score        DECIMAL(12, 4)  NOT NULL DEFAULT 0.0000 COMMENT '热度分，由定时任务算好',
    deleted          TINYINT         NOT NULL DEFAULT 0,
    created_at       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_category_status_update (category_id, status, latest_update_at DESC),
    KEY idx_hot (hot_score DESC),
    KEY idx_author (author_name),
    KEY idx_word_count (word_count)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='小说主表';

CREATE TABLE novel_tag
(
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    name       VARCHAR(32)     NOT NULL,
    created_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_name (name)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='标签字典';

CREATE TABLE novel_tag_rel
(
    novel_id BIGINT UNSIGNED NOT NULL,
    tag_id   BIGINT UNSIGNED NOT NULL,
    PRIMARY KEY (novel_id, tag_id),
    KEY idx_tag (tag_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='小说-标签 多对多';

CREATE TABLE chapter
(
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    novel_id   BIGINT UNSIGNED NOT NULL,
    seq        INT UNSIGNED    NOT NULL COMMENT '章节序号，从 1 开始连续',
    title      VARCHAR(255)    NOT NULL,
    word_count INT UNSIGNED    NOT NULL DEFAULT 0,
    is_free    TINYINT         NOT NULL DEFAULT 1,
    created_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_novel_seq (novel_id, seq),
    KEY idx_novel (novel_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='章节目录（不含正文）';

-- 正文单独一张表：一是避免目录查询把 MB 级 TEXT 拖进 buffer pool，
-- 二是将来章节正文要分库/冷存时，只需要动这一张表。
CREATE TABLE chapter_content
(
    chapter_id BIGINT UNSIGNED NOT NULL,
    novel_id   BIGINT UNSIGNED NOT NULL,
    content    MEDIUMTEXT      NOT NULL,
    PRIMARY KEY (chapter_id),
    KEY idx_novel (novel_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='章节正文';

-- ---------------------------------------------------------------------------
-- 阅读域：书架 = 收藏 + 进度
-- ---------------------------------------------------------------------------
CREATE TABLE bookshelf_group
(
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id    BIGINT UNSIGNED NOT NULL,
    name       VARCHAR(32)     NOT NULL,
    sort_no    INT             NOT NULL DEFAULT 0,
    is_default TINYINT         NOT NULL DEFAULT 0,
    created_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_name (user_id, name),
    KEY idx_user (user_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='书架分组';

CREATE TABLE bookshelf
(
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id         BIGINT UNSIGNED NOT NULL,
    novel_id        BIGINT UNSIGNED NOT NULL,
    group_id        BIGINT UNSIGNED          DEFAULT NULL,
    sort_no         INT             NOT NULL DEFAULT 0,
    last_chapter_seq INT UNSIGNED   NOT NULL DEFAULT 0 COMMENT '读到第几章',
    last_read_at    DATETIME                 DEFAULT NULL,
    deleted         TINYINT         NOT NULL DEFAULT 0,
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_novel (user_id, novel_id),
    KEY idx_user_read (user_id, last_read_at DESC)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='书架（收藏+阅读进度）';

-- ---------------------------------------------------------------------------
-- 评分 / 书评域
-- ---------------------------------------------------------------------------
CREATE TABLE novel_rating
(
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    novel_id   BIGINT UNSIGNED NOT NULL,
    user_id    BIGINT UNSIGNED NOT NULL,
    score      TINYINT         NOT NULL COMMENT '1-5 星',
    created_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    -- 一人一票的硬约束：业务代码再怎么写错，数据库也不会出现两条评分
    UNIQUE KEY uk_novel_user (novel_id, user_id),
    KEY idx_user (user_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='小说评分（一人一票）';

CREATE TABLE novel_review
(
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    novel_id    BIGINT UNSIGNED NOT NULL,
    user_id     BIGINT UNSIGNED NOT NULL,
    content     VARCHAR(2000)   NOT NULL,
    score       TINYINT                  DEFAULT NULL COMMENT '发书评时顺带打的星，可空',
    like_count  INT UNSIGNED    NOT NULL DEFAULT 0,
    reply_count INT UNSIGNED    NOT NULL DEFAULT 0,
    status      TINYINT         NOT NULL DEFAULT 0 COMMENT '0=正常 1=被隐藏',
    deleted     TINYINT         NOT NULL DEFAULT 0,
    created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_novel_created (novel_id, created_at DESC),
    KEY idx_user_created (user_id, created_at DESC)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='书级评论区主楼';

CREATE TABLE review_reply
(
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    review_id       BIGINT UNSIGNED NOT NULL,
    root_id         BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '所属主楼回复，0=直接回复主楼',
    parent_reply_id BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '直接父回复，用于展示"回复 @谁"',
    user_id         BIGINT UNSIGNED NOT NULL,
    content         VARCHAR(1000)   NOT NULL,
    like_count      INT UNSIGNED    NOT NULL DEFAULT 0,
    status          TINYINT         NOT NULL DEFAULT 0,
    deleted         TINYINT         NOT NULL DEFAULT 0,
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_review_created (review_id, created_at),
    KEY idx_root (root_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='书评回复（只渲染两层）';

-- 点赞统一表：书评、帖子、帖子评论共用
CREATE TABLE like_record
(
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id     BIGINT UNSIGNED NOT NULL,
    target_type VARCHAR(16)     NOT NULL COMMENT 'REVIEW / POST / POST_COMMENT',
    target_id   BIGINT UNSIGNED NOT NULL,
    created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_target_user (target_type, target_id, user_id),
    KEY idx_user (user_id, target_type)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='点赞记录（幂等靠唯一键）';

-- ---------------------------------------------------------------------------
-- 社区域
-- ---------------------------------------------------------------------------
CREATE TABLE post
(
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id       BIGINT UNSIGNED NOT NULL,
    title         VARCHAR(120)    NOT NULL,
    content       TEXT            NOT NULL COMMENT '正文，引用小说处存占位符 [[ref:0]]',
    cover_key     VARCHAR(255)             DEFAULT NULL,
    view_count    INT UNSIGNED    NOT NULL DEFAULT 0,
    like_count    INT UNSIGNED    NOT NULL DEFAULT 0,
    comment_count INT UNSIGNED    NOT NULL DEFAULT 0,
    hot_score     DECIMAL(12, 4)  NOT NULL DEFAULT 0.0000,
    status        TINYINT         NOT NULL DEFAULT 0 COMMENT '0=正常 1=被隐藏',
    deleted       TINYINT         NOT NULL DEFAULT 0,
    created_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_created (created_at DESC),
    KEY idx_hot (hot_score DESC),
    KEY idx_user_created (user_id, created_at DESC)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='社区帖子';

-- 结构化引用：正文里只留 [[ref:0]] 占位符，真实关系落这张表。
-- 好处：小说改名/换封面自动同步、链接不会失效、也不会被伪造。
CREATE TABLE post_novel_ref
(
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    post_id    BIGINT UNSIGNED NOT NULL,
    novel_id   BIGINT UNSIGNED NOT NULL,
    sort_no    INT             NOT NULL DEFAULT 0,
    ref_text   VARCHAR(64)     NOT NULL COMMENT '正文占位符，如 [[ref:0]]',
    created_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_post_sort (post_id, sort_no),
    KEY idx_novel (novel_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='帖子引用的结构化小说卡片';

CREATE TABLE post_comment
(
    id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    post_id           BIGINT UNSIGNED NOT NULL,
    user_id           BIGINT UNSIGNED NOT NULL,
    root_id           BIGINT UNSIGNED NOT NULL DEFAULT 0,
    parent_comment_id BIGINT UNSIGNED NOT NULL DEFAULT 0,
    content           VARCHAR(1000)   NOT NULL,
    like_count        INT UNSIGNED    NOT NULL DEFAULT 0,
    status            TINYINT         NOT NULL DEFAULT 0,
    deleted           TINYINT         NOT NULL DEFAULT 0,
    created_at        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_post_created (post_id, created_at),
    KEY idx_root (root_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='帖子评论';

-- ---------------------------------------------------------------------------
-- 榜单域
-- ---------------------------------------------------------------------------
CREATE TABLE novel_rank
(
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    period     VARCHAR(8)      NOT NULL COMMENT 'daily / weekly / monthly / all',
    rank_date  DATE            NOT NULL COMMENT '榜单日期',
    novel_id   BIGINT UNSIGNED NOT NULL,
    rank_no    INT UNSIGNED    NOT NULL,
    score      DECIMAL(12, 4)  NOT NULL DEFAULT 0.0000,
    created_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_period_date_novel (period, rank_date, novel_id),
    -- 榜单读取只走这个索引：WHERE period=? AND rank_date=? ORDER BY rank_no
    KEY idx_read (period, rank_date, rank_no)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='榜单快照';

-- ---------------------------------------------------------------------------
-- 基础设施域
-- ---------------------------------------------------------------------------
-- 本地消息表（Outbox）：业务写入与事件写入同一个事务，投递任务再异步发出。
-- 这是"不要在事务里直接发 MQ"的落地实现。
CREATE TABLE outbox
(
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    message_key    VARCHAR(64)     NOT NULL COMMENT '全局唯一，消费端幂等键',
    event_type     VARCHAR(64)     NOT NULL COMMENT 'novel.updated / rating.changed ...',
    aggregate_type VARCHAR(32)     NOT NULL,
    aggregate_id   BIGINT UNSIGNED NOT NULL,
    payload        JSON            NOT NULL,
    status         TINYINT         NOT NULL DEFAULT 0 COMMENT '0=待投递 1=已投递 2=投递失败',
    retry_count    INT             NOT NULL DEFAULT 0,
    next_retry_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_message_key (message_key),
    KEY idx_poll (status, next_retry_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='本地消息表';

CREATE TABLE mq_consume_log
(
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    message_key VARCHAR(64)    NOT NULL,
    consumer   VARCHAR(64)     NOT NULL,
    created_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    -- 重复投递时这条插入会失败，消费者据此直接跳过，实现幂等
    UNIQUE KEY uk_msg_consumer (message_key, consumer)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='消息消费幂等记录';
