package com.novel.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * {@code user} 表映射。
 *
 * <p>{@code @TableName} 里带反引号：{@code user} 在 MySQL 里是关键字，
 * 虽然多数语境下能裸写，但一旦出现在某些 JOIN / 别名场景就会报语法错误。
 * MyBatis-Plus 会原样把这段字符串拼进 SQL，反引号能一劳永逸。
 */
@Data
@TableName("`user`")
public class UserEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    private String passwordHash;

    private String phone;

    private String email;

    private Integer status;

    @TableLogic
    private Integer deleted;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
