package com.talon.index12306.userservice.dao.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.talon.index12306.database.base.BaseDO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.io.Serializable;


import java.util.Date;

/**
* @author talon
* @description 用户邮箱表
* @Date 2025-09-09
*/

@Data
@TableName ("t_user_mail")
@Schema(name = "UserMail")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserMail extends BaseDO  {

    /**
     * id
     */
    private Long id;

    /**
     * 用户名
     */
    private String username;

    /**
     * 手机号
     */
    private String mail;

    /**
     * 注销时间戳
     */
    private Long deletionTime;
}
