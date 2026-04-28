package com.talon.index12306.orderservice.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.talon.index12306.orderservice.dao.entity.OrderItemPassenger;
import com.talon.index12306.orderservice.service.OrderItemPassengerService;
import com.talon.index12306.orderservice.dao.mapper.OrderItemPassengerMapper;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;

/**
* @author talon
* @description 乘车人订单关系表Service
* @Date 2025-09-19
*/
@Service
@RequiredArgsConstructor
public class OrderItemPassengerServiceImpl extends ServiceImpl<OrderItemPassengerMapper, OrderItemPassenger>implements OrderItemPassengerService{

    private final  OrderItemPassengerMapper orderItemPassengerMapper;



}




