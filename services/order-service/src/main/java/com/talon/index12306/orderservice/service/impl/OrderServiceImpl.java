package com.talon.index12306.orderservice.service.impl;

import cn.crane4j.annotation.AutoOperate;
import cn.hutool.core.collection.ListUtil;
import cn.hutool.core.text.StrBuilder;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.talon.index12306.common.util.BeanUtil;
import com.talon.index12306.convention.exception.ClientException;
import com.talon.index12306.convention.exception.ServiceException;
import com.talon.index12306.convention.page.PageResponse;
import com.talon.index12306.convention.result.Result;
import com.talon.index12306.database.util.PageUtil;
import com.talon.index12306.framework.user.core.UserContext;
import com.talon.index12306.orderservice.common.enums.OrderCanalErrorCodeEnum;
import com.talon.index12306.orderservice.common.enums.OrderStatusEnum;
import com.talon.index12306.orderservice.dto.domain.OrderStatusReversalDTO;
import com.talon.index12306.orderservice.dto.req.*;
import com.talon.index12306.orderservice.dto.resp.TicketOrderDetailRespDTO;
import com.talon.index12306.orderservice.dto.resp.TicketOrderDetailSelfRespDTO;
import com.talon.index12306.orderservice.dao.entity.Order;
import com.talon.index12306.orderservice.dao.entity.OrderItem;
import com.talon.index12306.orderservice.dao.entity.OrderItemPassenger;
import com.talon.index12306.orderservice.dao.mapper.OrderItemMapper;
import com.talon.index12306.orderservice.dao.mapper.OrderMapper;
import com.talon.index12306.orderservice.mapstruct.OrderConverter;
import com.talon.index12306.orderservice.mq.event.DelayCloseOrderEvent;
import com.talon.index12306.orderservice.mq.event.PayResultCallbackOrderEvent;
import com.talon.index12306.orderservice.mq.produce.DelayCloseOrderSendProduce;
import com.talon.index12306.orderservice.remote.UserRemoteService;
import com.talon.index12306.orderservice.remote.dto.UserQueryActualRespDTO;
import com.talon.index12306.orderservice.service.OrderItemPassengerService;
import com.talon.index12306.orderservice.service.OrderItemService;
import com.talon.index12306.orderservice.service.OrderService;
import com.talon.index12306.orderservice.service.orderid.OrderIdGeneratorManager;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static com.talon.index12306.orderservice.common.enums.OrderStatusEnum.PENDING_PAYMENT;

/**
 * @author talon
 * @description 订单表Service
 * @Date 2025-09-19
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderServiceImpl extends ServiceImpl<OrderMapper, Order> implements OrderService {

    private final OrderMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final OrderItemService orderItemService;
    private final OrderItemPassengerService orderPassengerRelationService;
    private final RedissonClient redissonClient;
    private final DelayCloseOrderSendProduce delayCloseOrderSendProduce;
    private final UserRemoteService userRemoteService;
    private final OrderConverter orderConverter;

    @Override
    public TicketOrderDetailRespDTO queryTicketOrderByOrderSn(String orderSn) {
        //order->TicketOrderDetailRespDTO
        Order order = getOne(Wrappers.lambdaQuery(Order.class)
                .eq(Order::getOrderSn, orderSn)
        );
        if (order == null) {
            throw new ClientException(String.format("[%s] 订单号不存在", orderSn));
        }
        TicketOrderDetailRespDTO res = orderConverter.order2Resp(order);
        //orderItem->TicketOrderPassengerDetailRespDTO
        res.setPassengerDetails(orderConverter.orderItems2PassengerResp(orderItemMapper.selectList(Wrappers.lambdaQuery(OrderItem.class)
                .eq(OrderItem::getOrderSn, orderSn))));
        return res;

    }


    /**
     * <p> {@linkplain AutoOperate } 返回值自动填充</p>
     * <p>""->  {@linkplain  com.talon.index12306.convention.result.Result Result&lt;PageResponse&lt;TicketOrderDetailRespDTO&gt;&gt;}</p>
     * <p>data->{@linkplain  PageResponse PageResponse&lt;TicketOrderDetailRespDTO&gt;}</p>
     */
    @AutoOperate(type = TicketOrderDetailRespDTO.class, on = "data.records")
    @Override
    public PageResponse<TicketOrderDetailRespDTO> pageTicketOrder(TicketOrderPageQueryReqDTO requestParam) {
        //IPage<Order>
        IPage<Order> page = page(PageUtil.convert(requestParam), Wrappers.lambdaQuery(Order.class)
                .eq(Order::getUserId, requestParam.getUserId())
                .in(Order::getStatus, buildOrderStatusList(requestParam))
                .orderByDesc(Order::getOrderTime)
        );
        return PageUtil.convert(page, order -> {
            //IPage<Order> -> TicketOrderDetailRespDTO
            TicketOrderDetailRespDTO res = orderConverter.order2Resp(order);
            //getOrderItemByOrderSn
            //TicketOrderDetailRespDTO.setPassengerDetails
            res.setPassengerDetails(orderConverter.orderItems2PassengerResp(orderItemMapper.selectList(Wrappers.lambdaQuery(OrderItem.class)
                    .eq(OrderItem::getOrderSn, order.getOrderSn())
            )));
            return res;
        });
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public String createTicketOrder(TicketOrderCreateReqDTO requestParam) {
        // 通过基因法将用户 ID 融入到订单号
        String orderSn = OrderIdGeneratorManager.generateId(requestParam.getUserId());
        Order order = Order.builder().orderSn(orderSn)
                .orderTime(requestParam.getOrderTime())
                .departure(requestParam.getDeparture())
                .departureTime(requestParam.getDepartureTime())
                .ridingDate(requestParam.getRidingDate())
                .arrivalTime(requestParam.getArrivalTime())
                .trainNumber(requestParam.getTrainNumber())
                .arrival(requestParam.getArrival())
                .trainId(requestParam.getTrainId())
                .source(requestParam.getSource())
                .status(PENDING_PAYMENT.getStatus())
                .username(requestParam.getUsername())
                .userId(String.valueOf(requestParam.getUserId()))
                .build();
        orderMapper.insert(order);
        List<TicketOrderItemCreateReqDTO> ticketOrderItems = requestParam.getTicketOrderItems();
        List<OrderItem> orderItemList = new ArrayList<>();
        List<OrderItemPassenger> orderPassengerRelationList = new ArrayList<>();
        ticketOrderItems.forEach(each -> {
            OrderItem orderItem = OrderItem.builder()
                    .trainId(requestParam.getTrainId())
                    .seatNumber(each.getSeatNumber())
                    .carriageNumber(each.getCarriageNumber())
                    .realName(each.getRealName())
                    .orderSn(orderSn)
                    .phone(each.getPhone())
                    .seatType(each.getSeatType())
                    .username(requestParam.getUsername()).amount(each.getAmount()).carriageNumber(each.getCarriageNumber())
                    .idCard(each.getIdCard())
                    .ticketType(each.getTicketType())
                    .idType(each.getIdType())
                    .userId(String.valueOf(requestParam.getUserId()))
                    .status(0)
                    .build();
            orderItemList.add(orderItem);
            OrderItemPassenger orderPassengerRelation = OrderItemPassenger.builder()
                    .idType(each.getIdType())
                    .idCard(each.getIdCard())
                    .orderSn(orderSn)
                    .build();
            orderPassengerRelationList.add(orderPassengerRelation);
        });
        orderItemService.saveBatch(orderItemList);
        orderPassengerRelationService.saveBatch(orderPassengerRelationList);
        try {
            // 发送 RocketMQ 延时消息，指定时间后取消订单
            DelayCloseOrderEvent delayCloseOrderEvent = DelayCloseOrderEvent.builder()
                    .trainId(String.valueOf(requestParam.getTrainId()))
                    .departure(requestParam.getDeparture())
                    .arrival(requestParam.getArrival())
                    .orderSn(orderSn)
                    .trainPurchaseTicketResults(requestParam.getTicketOrderItems())
                    .build();
            SendResult sendResult = delayCloseOrderSendProduce.sendMessage(delayCloseOrderEvent);
            if (!Objects.equals(sendResult.getSendStatus(), SendStatus.SEND_OK)) {
                throw new ServiceException("投递延迟关闭订单消息队列失败");
            }
        } catch (Throwable ex) {
            log.error("延迟关闭订单消息队列发送错误，请求参数：{}", JSON.toJSONString(requestParam), ex);
            throw ex;
        }
        return orderSn;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public boolean closeTickOrder(CancelTicketOrderReqDTO requestParam) {
        return cancelTickOrder(requestParam);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public boolean cancelTickOrder(CancelTicketOrderReqDTO requestParam) {
        //TODO 是否可以使用幂等注解
        //校验order
        String orderSn = requestParam.getOrderSn();
        Order order = getOne(Wrappers.lambdaQuery(Order.class)
                .eq(Order::getOrderSn, orderSn)
        );
        if (order == null) {
            throw new ServiceException(OrderCanalErrorCodeEnum.ORDER_CANAL_UNKNOWN_ERROR);
        } else if (order.getStatus() != OrderStatusEnum.PENDING_PAYMENT.getStatus()) {
            throw new ServiceException(OrderCanalErrorCodeEnum.ORDER_CANAL_STATUS_ERROR);
        }
        //分布式锁
        RLock lock = redissonClient.getLock(StrBuilder.create("order:canal:order_sn_").append(orderSn).toString());
        if (!lock.tryLock()) {
            throw new ClientException(OrderCanalErrorCodeEnum.ORDER_CANAL_REPETITION_ERROR);
        }
        try {
            //更新order orderItem
            order.setStatus(OrderStatusEnum.CLOSED.getStatus());
            if (!updateById(order)) {
                throw new ServiceException(OrderCanalErrorCodeEnum.ORDER_CANAL_ERROR);
            }
            OrderItem orderItem = orderItemMapper.selectOne(Wrappers.lambdaQuery(OrderItem.class)
                    .eq(OrderItem::getOrderSn, orderSn)
            );
            orderItem.setStatus(OrderStatusEnum.CLOSED.getStatus());
            if (orderItemMapper.updateById(orderItem) <= 0) {
                throw new ServiceException(OrderCanalErrorCodeEnum.ORDER_CANAL_ERROR);
            }
        } finally {
            lock.unlock();
        }
        return true;
    }

    @Override
    public void statusReversal(OrderStatusReversalDTO requestParam) {
        String orderSn = requestParam.getOrderSn();
        Order order = orderMapper.selectOne(Wrappers.lambdaQuery(Order.class)
                .eq(Order::getOrderSn, orderSn)
        );
        if (order == null) {
            throw new ServiceException(OrderCanalErrorCodeEnum.ORDER_CANAL_UNKNOWN_ERROR);
        } else if (order.getStatus() != OrderStatusEnum.PENDING_PAYMENT.getStatus()) {
            throw new ServiceException(OrderCanalErrorCodeEnum.ORDER_CANAL_STATUS_ERROR);
        }
        RLock lock = redissonClient.getLock(StrBuilder.create("order:status-reversal:order_sn_").append(requestParam.getOrderSn()).toString());
        if (!lock.tryLock()) {
            log.warn("订单重复修改状态，状态反转请求参数：{}", JSON.toJSONString(requestParam));
        }
        try {
            order.setStatus(requestParam.getOrderStatus());
            if (orderMapper.updateById(order) <= 0) {
                throw new ServiceException(OrderCanalErrorCodeEnum.ORDER_STATUS_REVERSAL_ERROR);
            }
            OrderItem orderItem = orderItemMapper.selectOne(Wrappers.lambdaQuery(OrderItem.class)
                    .eq(OrderItem::getOrderSn, orderSn)
            );
            orderItem.setStatus(requestParam.getOrderItemStatus());
            if (orderItemMapper.updateById(orderItem) <= 0) {
                throw new ServiceException(OrderCanalErrorCodeEnum.ORDER_STATUS_REVERSAL_ERROR);
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void payCallbackOrder(PayResultCallbackOrderEvent requestParam) {
        Order order = new Order();
        order.setPayTime(requestParam.getGmtPayment());
        order.setPayType(requestParam.getChannel());
        if (orderMapper.update(order, Wrappers.lambdaUpdate(Order.class)
                .eq(Order::getOrderSn, requestParam.getOrderSn())) <= 0) {
            throw new ServiceException(OrderCanalErrorCodeEnum.ORDER_STATUS_REVERSAL_ERROR);
        }
    }

    @Override
    public PageResponse<TicketOrderDetailSelfRespDTO> pageSelfTicketOrder(TicketOrderSelfPageQueryReqDTO requestParam) {
        //通过username 拿到 IdCard
        Result<UserQueryActualRespDTO> userQueryActualRespDTOResult = userRemoteService.queryActualUserByUsername(UserContext.getUsername());
        //乘车人车票分页查询结果
        IPage<OrderItemPassenger> page = orderPassengerRelationService.page(PageUtil.convert(requestParam), Wrappers.lambdaQuery(OrderItemPassenger.class)
                .eq(OrderItemPassenger::getIdCard, userQueryActualRespDTOResult.getData().getIdCard())
                .orderByDesc(OrderItemPassenger::getCreateTime)
        );
        //拿到orderSn
        //Order -> TicketOrderDetailSelfRespDTO
        return PageUtil.convert(page, each -> {
            Order order = orderMapper.selectOne(Wrappers.lambdaQuery(Order.class)
                    .eq(Order::getOrderSn, each.getOrderSn())
            );
            TicketOrderDetailSelfRespDTO res = orderConverter.order2SelfResp(order);
            OrderItem orderItem = orderItemMapper.selectOne(Wrappers.lambdaQuery(OrderItem.class)
                    .eq(OrderItem::getOrderSn, each.getOrderSn())
                    .eq(OrderItem::getIdCard, each.getIdCard())
            );
            BeanUtil.convertIgnoreNullAndBlank(orderItem,res);
           return res;
        });
    }

    private List<Integer> buildOrderStatusList(TicketOrderPageQueryReqDTO requestParam) {
        List<Integer> result = new ArrayList<>();
        switch (requestParam.getStatusType()) {
            case 0 -> result = ListUtil.of(
                    PENDING_PAYMENT.getStatus()
            );
            case 1 -> result = ListUtil.of(
                    OrderStatusEnum.ALREADY_PAID.getStatus(),
                    OrderStatusEnum.PARTIAL_REFUND.getStatus(),
                    OrderStatusEnum.FULL_REFUND.getStatus()
            );
            case 2 -> result = ListUtil.of(
                    OrderStatusEnum.COMPLETED.getStatus()
            );
        }
        return result;
    }

}




