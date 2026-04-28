package com.talon.index12306.orderservice.dao.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.talon.index12306.database.base.BaseDO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.io.Serializable;


import java.util.Date;

/**
* @author talon
* @description 订单明细表
* @Date 2025-09-19
*/

@Data
@TableName ("t_order_item")
@Schema(name = "OrderItem")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderItem extends BaseDO  {

    /**
     * id
     */
    private Long id;

    /**
     * 订单号
     */
    private String orderSn;

    /**
     * 用户id
     */
    private String userId;

    /**
     * 用户名
     */
    private String username;

    /**
     * 列车id
     */
    private Long trainId;

    /**
     * 车厢号
     */
    private String carriageNumber;

    /**
     * 座位类型
     */
    private Integer seatType;

    /**
     * 座位号
     */
    private String seatNumber;

    /**
     * 真实姓名
     */
    private String realName;

    /**
     * 证件类型
     */
    private Integer idType;

    /**
     * 证件号
     */
    private String idCard;

    /**
     * 手机号
     */
    private String phone;

    /**
     * 订单状态
     */
    private Integer status;

    /**
     * 订单金额
     */
    private Integer amount;

    /**
     * 车票类型
     */
    private Integer ticketType;

}
