package com.talon.index12306.orderservice.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.talon.index12306.convention.page.PageResponse;
import com.talon.index12306.orderservice.dto.domain.OrderStatusReversalDTO;
import com.talon.index12306.orderservice.dto.req.CancelTicketOrderReqDTO;
import com.talon.index12306.orderservice.dto.req.TicketOrderCreateReqDTO;
import com.talon.index12306.orderservice.dto.req.TicketOrderPageQueryReqDTO;
import com.talon.index12306.orderservice.dto.req.TicketOrderSelfPageQueryReqDTO;
import com.talon.index12306.orderservice.dto.resp.TicketOrderDetailRespDTO;
import com.talon.index12306.orderservice.dto.resp.TicketOrderDetailSelfRespDTO;
import com.talon.index12306.orderservice.dao.entity.Order;
import com.talon.index12306.orderservice.mq.event.PayResultCallbackOrderEvent;

/**
* @author talon
* @description 订单表Service
* @Date 2025-09-19
*/
public interface OrderService extends IService<Order> {

    /**
     * 跟据订单号查询车票订单
     *
     * @param orderSn 订单号
     * @return 订单详情
     */
    TicketOrderDetailRespDTO queryTicketOrderByOrderSn(String orderSn);

    /**
     * 跟据用户名分页查询车票订单
     *
     * @param requestParam 跟据用户 ID 分页查询对象
     * @return 订单分页详情
     */
    PageResponse<TicketOrderDetailRespDTO> pageTicketOrder(TicketOrderPageQueryReqDTO requestParam);

    /**
     * 创建火车票订单
     *
     * @param requestParam 商品订单入参
     * @return 订单号
     */
    String createTicketOrder(TicketOrderCreateReqDTO requestParam);

    /**
     * 关闭火车票订单
     *
     * @param requestParam 关闭火车票订单入参
     */
    boolean closeTickOrder(CancelTicketOrderReqDTO requestParam);

    /**
     * 取消火车票订单
     *
     * @param requestParam 取消火车票订单入参
     */
    boolean cancelTickOrder(CancelTicketOrderReqDTO requestParam);

    /**
     * 订单状态反转
     *
     * @param requestParam 请求参数
     */
    void statusReversal(OrderStatusReversalDTO requestParam);

    /**
     * 支付结果回调订单
     *
     * @param requestParam 请求参数
     */
    void payCallbackOrder(PayResultCallbackOrderEvent requestParam);

    /**
     * 查询本人车票订单
     *
     * @param requestParam 请求参数
     * @return 本人车票订单集合
     */
    PageResponse<TicketOrderDetailSelfRespDTO> pageSelfTicketOrder(TicketOrderSelfPageQueryReqDTO requestParam);
}
