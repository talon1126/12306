package com.talon.index12306.userservice.dao.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.talon.index12306.database.base.BaseDO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;


import java.util.Date;

/**
* @author talon
* @description 用户号码表
* @Date 2025-09-09
*/

@Data
@TableName ("t_user_phone")
@Schema(name = "UserPhone")
@NoArgsConstructor
@AllArgsConstructor
public class UserPhone extends BaseDO {

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
    private String phone;

    /**
     * 注销时间戳
     */
    private Long deletionTime;

}
