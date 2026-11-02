package com.novel.core.storage;

/**
 * 上传业务分类。
 *
 * <p>用枚举而不是让前端随便传字符串，是为了把 key 的第一段锁死。
 * 否则前端传个 {@code ../../etc} 就能把文件写到别人的目录里去。
 */
public enum FileBizType {

    /** 小说封面 */
    COVER("cover"),
    /** 用户头像 */
    AVATAR("avatar"),
    /** 社区帖子配图 */
    POST("post"),
    /** 导入的 TXT/EPUB 原始文件 */
    IMPORT("import"),
    ;

    private final String path;

    FileBizType(String path) {
        this.path = path;
    }

    public String path() {
        return path;
    }

    public static FileBizType from(String value) {
        for (FileBizType type : values()) {
            if (type.name().equalsIgnoreCase(value) || type.path.equalsIgnoreCase(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("不支持的上传类型: " + value);
    }
}
