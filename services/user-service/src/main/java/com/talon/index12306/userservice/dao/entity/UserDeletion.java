package com.talon.index12306.userservice.dao.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.talon.index12306.database.base.BaseDO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.io.Serializable;


import java.util.Date;

/**
* @author talon
* @description 用户注销表
* @Date 2025-09-12
*/

@Data
@TableName ("t_user_deletion")
@Schema(name = "UserDeletion")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserDeletion extends BaseDO  {

    /**
     * id
     */
    private Long id;

    /**
     * 证件类型
     */
    private Integer idType;

    /**
     * 证件号
     */
    private String idCard;

}
