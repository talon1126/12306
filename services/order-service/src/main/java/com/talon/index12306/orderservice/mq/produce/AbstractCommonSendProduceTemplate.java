package com.talon.index12306.orderservice.mq.produce;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.Message;
import org.springframework.util.StringUtils;

import java.util.Optional;

/**
 * RocketMQ 抽象公共发送消息组件
 * 公众号：马丁玩编程，回复：加群，添加马哥微信（备注：12306）获取项目资料
 */
@Slf4j
@RequiredArgsConstructor
public abstract class AbstractCommonSendProduceTemplate<T> {
    private final RocketMQTemplate rocketMQTemplate;

    /**
     * 构建消息发送事件基础属性实体
     *
     * @param messageSendEvent 消息发送事件
     * @return 扩充属性实体
     */
    protected abstract BaseSendExtendDTO buildBaseSendExtendParam(T messageSendEvent);

    /**
     * 构建消息基本参数，请求头、Keys...
     *
     * @param messageSendEvent 消息发送事件
     * @param requestParam     扩充属性实体
     * @return 消息基本参数
     */
    protected abstract Message<?> buildMessage(T messageSendEvent, BaseSendExtendDTO requestParam);

    /**
     * 消息事件通用发送
     *
     * @param messageSendEvent 消息发送事件
     * @return 消息发送返回结果
     */
    public SendResult sendMessage(T messageSendEvent) {
        BaseSendExtendDTO baseSendExtendDTO = buildBaseSendExtendParam(messageSendEvent);
        SendResult sendResult ;
       try {
           StringBuilder builder = StrUtil.builder().append(baseSendExtendDTO.getTopic());
           String des = StringUtils.hasText(baseSendExtendDTO.getKeys()) ? builder.append(":").append(baseSendExtendDTO.getKeys()).toString() : builder.toString();
           sendResult = rocketMQTemplate.syncSend(
                   //topic:tag
                   des,
                   buildMessage(messageSendEvent, baseSendExtendDTO),
                   baseSendExtendDTO.getSentTimeout(),
                   Optional.ofNullable(baseSendExtendDTO.getDelayLevel()).orElse(0));
           log.info("[{}] 消息发送结果：{}，消息ID：{}，消息Keys：{}", baseSendExtendDTO.getEventName(), sendResult.getSendStatus(), sendResult.getMsgId(), baseSendExtendDTO.getKeys());
       } catch (Throwable ex) {
           log.error("[{}] 消息发送失败，消息体：{}", baseSendExtendDTO.getEventName(), JSON.toJSONString(messageSendEvent), ex);
           throw ex;
       }
        return sendResult;
    }
}