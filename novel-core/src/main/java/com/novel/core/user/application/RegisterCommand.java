package com.novel.core.user.application;

/** 注册命令。校验注解放在 api 层 DTO 上，core 只接收已清洗过的值。 */
public record RegisterCommand(String username, String password, String nickname) {
}
