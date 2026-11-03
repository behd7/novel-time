package com.novel.infra.security;

import com.novel.core.user.port.PasswordHasher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * BCrypt 实现。
 *
 * <p>为什么是 BCrypt 而不是 MD5/SHA256：
 * <ul>
 *   <li>自带随机盐，同一个密码每次哈希结果都不同，彩虹表直接失效</li>
 *   <li>故意算得慢（本机约 100ms），把离线爆破的成本抬高几个数量级</li>
 * </ul>
 *
 * <p>strength=10 是默认值：单次约 50~100ms。调到 12 更安全但登录会明显变慢，
 * 调到 4 是给单元测试用的（别在生产用）。
 *
 * <p>注意 BCrypt 只取密码前 72 字节，超长密码会被截断 —— 这是它的已知限制，
 * 也正因如此业务层限制了密码最大长度，避免"密码后半段无效"这种反直觉行为。
 */
public class BCryptPasswordHasher implements PasswordHasher {

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(10);

    @Override
    public String hash(String rawPassword) {
        return encoder.encode(rawPassword);
    }

    @Override
    public boolean matches(String rawPassword, String hashedPassword) {
        if (rawPassword == null || hashedPassword == null || hashedPassword.isBlank()) {
            return false;
        }
        return encoder.matches(rawPassword, hashedPassword);
    }
}
