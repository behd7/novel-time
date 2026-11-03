package com.novel.core.user.port;

/**
 * 密码哈希端口。
 *
 * <p>core 只要求"能哈希、能校验"，具体是 BCrypt、Argon2 还是 PBKDF2 由 infra 决定。
 * 这也是给未来留的口子：BCrypt 有 72 字节截断问题，哪天要换 Argon2id，
 * 只要新增一个实现并做双哈希迁移，业务代码零改动。
 */
public interface PasswordHasher {

    String hash(String rawPassword);

    boolean matches(String rawPassword, String hashedPassword);
}
