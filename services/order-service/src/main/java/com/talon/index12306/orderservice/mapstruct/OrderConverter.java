package com.talon.index12306.orderservice.mapstruct;

import com.talon.index12306.orderservice.dto.resp.TicketOrderDetailRespDTO;
import com.talon.index12306.orderservice.dto.resp.TicketOrderDetailSelfRespDTO;
import com.talon.index12306.orderservice.dto.resp.TicketOrderPassengerDetailRespDTO;
import com.talon.index12306.orderservice.dao.entity.Order;
import com.talon.index12306.orderservice.dao.entity.OrderItem;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface OrderConverter {

    TicketOrderDetailRespDTO order2Resp(Order order);

    TicketOrderDetailSelfRespDTO order2SelfResp(Order order);

    List<TicketOrderPassengerDetailRespDTO> orderItems2PassengerResp(List<OrderItem> orderItems);


}
