package com.talon.index12306.orderservice.service;

import com.talon.index12306.orderservice.dto.domain.OrderItemStatusReversalDTO;
import com.talon.index12306.orderservice.dto.req.TicketOrderItemQueryReqDTO;
import com.talon.index12306.orderservice.dto.resp.TicketOrderPassengerDetailRespDTO;
import com.talon.index12306.orderservice.dao.entity.OrderItem;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
* @author talon
* @description 订单明细表Service
* @Date 2025-09-19
*/
public interface OrderItemService extends IService<OrderItem> {

    /**
     * 子订单状态反转
     *
     * @param requestParam 请求参数
     */
    void orderItemStatusReversal(OrderItemStatusReversalDTO requestParam);

    /**
     * 根据子订单记录id查询车票子订单详情
     *
     * @param requestParam 请求参数
     */
    List<TicketOrderPassengerDetailRespDTO> queryTicketItemOrderById(TicketOrderItemQueryReqDTO requestParam);
}
