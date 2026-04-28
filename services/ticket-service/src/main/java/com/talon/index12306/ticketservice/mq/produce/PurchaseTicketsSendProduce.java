/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.talon.index12306.ticketservice.mq.produce;

import cn.hutool.core.util.StrUtil;
import com.talon.index12306.ticketservice.common.constant.TicketRocketMQConstant;
import com.talon.index12306.ticketservice.mq.domain.MessageWrapper;
import com.talon.index12306.ticketservice.mq.event.PurchaseTicketsEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.common.message.MessageConst;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 延迟关闭订单生产者
 * <p>
 * 作者：马丁
 * 加项目群：早加入就是优势！500人内部项目群，分享的知识总有你需要的 <a href="https://t.zsxq.com/cw7b9" />
 */
@Slf4j
@Component
public class PurchaseTicketsSendProduce extends AbstractCommonSendProduceTemplate<PurchaseTicketsEvent> {

    private final ConfigurableEnvironment environment;

    public PurchaseTicketsSendProduce(@Autowired RocketMQTemplate rocketMQTemplate, @Autowired ConfigurableEnvironment environment) {
        super(rocketMQTemplate);
        this.environment = environment;
    }

    @Override
    protected BaseSendExtendDTO buildBaseSendExtendParam(PurchaseTicketsEvent messageSendEvent) {
        return BaseSendExtendDTO.builder()
                .eventName("用户异步购票")
                .keys(messageSendEvent.getOrderTrackingId())
                .topic(environment.resolvePlaceholders(TicketRocketMQConstant.PURCHASE_TICKET_ASYNC_TOPIC_KEY))
                .tag(environment.resolvePlaceholders(TicketRocketMQConstant.PURCHASE_TICKET_ASYNC_CG_KEY))
                .sentTimeout(2000L)
                .build();
    }

    @Override
    protected Message<?> buildMessage(PurchaseTicketsEvent messageSendEvent, BaseSendExtendDTO requestParam) {
        String keys = StrUtil.isEmpty(requestParam.getKeys()) ? UUID.randomUUID().toString() : requestParam.getKeys();
        return MessageBuilder
                .withPayload(new MessageWrapper(requestParam.getKeys(), messageSendEvent))
                .setHeader(MessageConst.PROPERTY_KEYS, keys)
                .setHeader(MessageConst.PROPERTY_TAGS, requestParam.getTag())
                .build();
    }
}
