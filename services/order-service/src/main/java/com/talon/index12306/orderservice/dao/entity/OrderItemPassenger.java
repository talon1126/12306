package com.talon.index12306.orderservice.dao.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.talon.index12306.database.base.BaseDO;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.io.Serializable;


import java.util.Date;

/**
* @author talon
* @description 乘车人订单关系表
* @Date 2025-09-19
*/

@Data
@TableName ("t_order_item_passenger")
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderItemPassenger extends BaseDO  {


    /**
     * id
     */
    private Long id;

    /**
     * 订单号
     */
    private String orderSn;

    /**
     * 证件类型
     */
    private Integer idType;

    /**
     * 证件号
     */
    private String idCard;

}
