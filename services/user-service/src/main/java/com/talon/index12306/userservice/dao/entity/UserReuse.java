package com.talon.index12306.userservice.dao.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.talon.index12306.database.base.BaseDO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;


import java.util.Date;

/**
* @author talon
* @description 用户名复用表
* @Date 2025-09-25
*/

@Data
@TableName ("t_user_reuse")
@Schema(name = "UserReuse")
@AllArgsConstructor
@NoArgsConstructor
public class UserReuse extends BaseDO  {

    /**
     * 用户名
     */
    private String username;

}
