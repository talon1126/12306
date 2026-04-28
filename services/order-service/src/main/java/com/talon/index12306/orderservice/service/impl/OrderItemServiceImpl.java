package com.talon.index12306.orderservice.service.impl;

import cn.hutool.core.text.StrBuilder;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.talon.index12306.convention.exception.ServiceException;
import com.talon.index12306.orderservice.common.enums.OrderCanalErrorCodeEnum;
import com.talon.index12306.orderservice.dto.domain.OrderItemStatusReversalDTO;
import com.talon.index12306.orderservice.dto.req.TicketOrderItemQueryReqDTO;
import com.talon.index12306.orderservice.dto.resp.TicketOrderPassengerDetailRespDTO;
import com.talon.index12306.orderservice.dao.entity.Order;
import com.talon.index12306.orderservice.dao.entity.OrderItem;
import com.talon.index12306.orderservice.dao.mapper.OrderMapper;
import com.talon.index12306.orderservice.mapstruct.OrderConverter;
import com.talon.index12306.orderservice.service.OrderItemService;
import com.talon.index12306.orderservice.dao.mapper.OrderItemMapper;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import org.springframework.util.CollectionUtils;

import java.util.List;

/**
 * @author talon
 * @description 订单明细表Service
 * @Date 2025-09-19
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderItemServiceImpl extends ServiceImpl<OrderItemMapper, OrderItem> implements OrderItemService {

    private final OrderItemMapper orderItemMapper;
    private final OrderMapper orderMapper;
    private final RedissonClient redissonClient;
    private final OrderConverter orderConverter;


    @Override
    public void orderItemStatusReversal(OrderItemStatusReversalDTO requestParam) {
        Order order = orderMapper.selectOne(Wrappers.lambdaQuery(Order.class)
                .eq(Order::getOrderSn, requestParam.getOrderSn())
        );
        if (order == null) {
            throw new ServiceException(OrderCanalErrorCodeEnum.ORDER_CANAL_UNKNOWN_ERROR);
        }
        RLock lock = redissonClient.getLock(StrBuilder.create("order:status-reversal:order_sn_").append(requestParam.getOrderSn()).toString());
        if (!lock.tryLock()) {
            log.warn("订单重复修改状态，状态反转请求参数：{}", JSON.toJSONString(requestParam));
        }
        try {
            Order orderUpdate = new Order();
            orderUpdate.setStatus(requestParam.getOrderStatus());
            if (orderMapper.update(orderUpdate, Wrappers.lambdaUpdate(Order.class)
                    .eq(Order::getOrderSn, requestParam.getOrderSn())) <= 0) {
                throw new ServiceException(OrderCanalErrorCodeEnum.ORDER_STATUS_REVERSAL_ERROR);
            }
            List<OrderItem> orderItemList = requestParam.getOrderItemDOList();
            if (!CollectionUtils.isEmpty(orderItemList)) {
                for (OrderItem orderItem : orderItemList) {
                    orderItem.setStatus(requestParam.getOrderItemStatus());
                    if (orderItemMapper.update(orderItem, Wrappers.lambdaUpdate(OrderItem.class)
                            .eq(OrderItem::getOrderSn, requestParam.getOrderSn())
                            .eq(OrderItem::getRealName, orderItem.getRealName())) <= 0) {
                        throw new ServiceException(OrderCanalErrorCodeEnum.ORDER_ITEM_STATUS_REVERSAL_ERROR);
                    }
                }
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public List<TicketOrderPassengerDetailRespDTO> queryTicketItemOrderById(TicketOrderItemQueryReqDTO requestParam) {
        return orderConverter.orderItems2PassengerResp(orderItemMapper.selectList(Wrappers.lambdaQuery(OrderItem.class)
                .eq(OrderItem::getOrderSn,requestParam.getOrderSn())
                .in(OrderItem::getId,requestParam.getOrderItemRecordIds())
        ));
    }
}




