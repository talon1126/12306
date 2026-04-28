package com.talon.index12306.ticketservice.mq.consumer;

import cn.hutool.core.util.ObjUtil;
import com.alibaba.fastjson2.JSON;
import com.talon.index12306.convention.exception.ServiceException;
import com.talon.index12306.framework.user.core.UserContext;
import com.talon.index12306.framework.user.core.UserInfoDTO;
import com.talon.index12306.idempotent.annotation.Idempotent;
import com.talon.index12306.idempotent.enums.IdempotentSceneEnum;
import com.talon.index12306.idempotent.enums.IdempotentTypeEnum;
import com.talon.index12306.ticketservice.common.constant.TicketRocketMQConstant;
import com.talon.index12306.ticketservice.common.enums.PurchaseTicketsErrorCodeEnum;
import com.talon.index12306.ticketservice.dao.entity.OrderTrackingDO;
import com.talon.index12306.ticketservice.dao.mapper.OrderTrackingMapper;
import com.talon.index12306.ticketservice.dto.req.PurchaseTicketReqDTO;
import com.talon.index12306.ticketservice.dto.resp.TicketPurchaseRespDTO;
import com.talon.index12306.ticketservice.mq.domain.MessageWrapper;
import com.talon.index12306.ticketservice.mq.event.PurchaseTicketsEvent;
import com.talon.index12306.ticketservice.service.TicketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;

/**
 * 用户异步购票消费者
 * <p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(
        topic = TicketRocketMQConstant.PURCHASE_TICKET_ASYNC_TOPIC_KEY,
        consumerGroup = TicketRocketMQConstant.PURCHASE_TICKET_ASYNC_CG_KEY
)
public class PurchaseTicketsConsumer implements RocketMQListener<MessageWrapper<PurchaseTicketsEvent>> {

    private final TicketService ticketService;
    private final OrderTrackingMapper orderTrackingMapper;

    @Idempotent(
            uniqueKeyPrefix = "index12306-ticket:purchase_tickets_v3:",
            key = "#messageWrapper.getKeys()",
            type = IdempotentTypeEnum.SPEL,
            scene = IdempotentSceneEnum.MQ,
            keyTimeout = 7200L
    )
    @Override
    public void onMessage(MessageWrapper<PurchaseTicketsEvent> messageWrapper) {
        log.info("[用户异步购票] 开始消费：{}", JSON.toJSONString(messageWrapper));

        // 获取用户购票参数
        PurchaseTicketsEvent message = messageWrapper.getMessage();
        PurchaseTicketReqDTO originalRequestParam = message.getOriginalRequestParam();
        Long orderTrackingId = Long.parseLong(message.getOrderTrackingId());
        // 发起用户创建订单
        UserInfoDTO userInfo = message.getUserInfo();
        String username = userInfo.getUsername();
        TicketPurchaseRespDTO ticketPurchaseRespDTO = null;
        boolean insufficientTicketFlag = false;
        try {
            UserContext.setUser(userInfo);
            ticketPurchaseRespDTO = ticketService.executePurchaseTickets(originalRequestParam);
        } catch (ServiceException se) {
            // 错误可能有两种，其中一个是列车无余票，另外可能是发起购票失败，比如订单服务宕机、Redis 宕机等极端情况
            insufficientTicketFlag = ObjUtil.equals(se.getErrorCode(), PurchaseTicketsErrorCodeEnum.INSUFFICIENT_TRAIN_TICKETS.code());
        } finally {
            UserContext.remove();
        }
        // 根据用户是否创建订单成功构建订单追踪实体
        OrderTrackingDO orderTrackingDO = OrderTrackingDO.builder()
                .id(orderTrackingId)
                .orderSn(ticketPurchaseRespDTO != null ? ticketPurchaseRespDTO.getOrderSn() : null)
                // 状态 0：请求下单成功 1：列车与余票不足 2：购票请求失败
                .status(ticketPurchaseRespDTO != null ? 0 : insufficientTicketFlag ? 1 : 2)
                .username(username)
                .build();
        // 新增订单追踪记录，方便为购票 v3 接口异步下单后的结果提供查询能力
        orderTrackingMapper.insert(orderTrackingDO);
    }
}