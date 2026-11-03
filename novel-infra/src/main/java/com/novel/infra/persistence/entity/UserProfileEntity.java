package com.novel.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** {@code user_profile} 表映射。主键是 user_id，不是自增 id。 */
@Data
@TableName("user_profile")
public class UserProfileEntity {

    @TableId(value = "user_id", type = IdType.INPUT)
    private Long userId;

    private String nickname;

    private String avatarKey;

    private String bio;

    private Integer gender;

    private LocalDate birthday;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
