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

package com.talon.index12306.ticketservice.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.talon.index12306.ticketservice.dao.entity.TicketDO;
import com.talon.index12306.ticketservice.dto.req.CancelTicketOrderReqDTO;
import com.talon.index12306.ticketservice.dto.req.PurchaseTicketReqDTO;
import com.talon.index12306.ticketservice.dto.req.RefundTicketReqDTO;
import com.talon.index12306.ticketservice.dto.req.TicketPageQueryReqDTO;
import com.talon.index12306.ticketservice.dto.resp.OrderTrackingRespDTO;
import com.talon.index12306.ticketservice.dto.resp.RefundTicketRespDTO;
import com.talon.index12306.ticketservice.dto.resp.TicketPageQueryRespDTO;
import com.talon.index12306.ticketservice.dto.resp.TicketPurchaseRespDTO;
import com.talon.index12306.ticketservice.remote.dto.PayInfoRespDTO;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 车票接口
 * 公众号：马丁玩编程，回复：加群，添加马哥微信（备注：12306）获取项目资料
 */
public interface TicketService extends IService<TicketDO> {

    /**
     * 根据条件分页查询车票
     *
     * @param requestParam 分页查询车票请求参数
     * @return 查询车票返回结果
     */
    TicketPageQueryRespDTO pageListTicketQueryV1(TicketPageQueryReqDTO requestParam);

    /**
     * 根据条件分页查询车票V2高性能版本
     *
     * @param requestParam 分页查询车票请求参数
     * @return 查询车票返回结果
     */
    TicketPageQueryRespDTO pageListTicketQueryV2(TicketPageQueryReqDTO requestParam);

    /**
     * 购买车票
     *
     * @param requestParam 车票购买请求参数
     * @return 订单号
     */
    TicketPurchaseRespDTO purchaseTicketsV1(@RequestBody PurchaseTicketReqDTO requestParam);

    /**
     * 购买车票V2高性能版本
     *
     * @param requestParam 车票购买请求参数
     * @return 订单号
     */
    TicketPurchaseRespDTO purchaseTicketsV2(@RequestBody PurchaseTicketReqDTO requestParam);

    /**
     * 购买车票V3高性能版本
     *
     * @param requestParam 车票购买请求参数
     * @return 关联用户购票订单流水号
     */
    String purchaseTicketsV3(PurchaseTicketReqDTO requestParam);

    /**
     * 根据订单追踪 ID 查询订单状态
     *
     * @param orderTrackingId 订单追踪 ID
     * @return 订单号和下单订单状态追踪
     */
    OrderTrackingRespDTO purchaseTicketsV3Query(String orderTrackingId);

    /**
     * 执行购买车票
     * 被对应购票版本号接口调用 {@link TicketService#purchaseTicketsV1(PurchaseTicketReqDTO)} and {@link TicketService#purchaseTicketsV2(PurchaseTicketReqDTO)}
     *
     * @param requestParam 车票购买请求参数
     * @return 订单号
     */
    TicketPurchaseRespDTO executePurchaseTickets(@RequestBody PurchaseTicketReqDTO requestParam);

    /**
     * 支付单详情查询
     *
     * @param orderSn 订单号
     * @return 支付单详情
     */
    PayInfoRespDTO getPayInfo(String orderSn);

    /**
     * 取消车票订单
     *
     * @param requestParam 取消车票订单入参
     */
    void cancelTicketOrder(CancelTicketOrderReqDTO requestParam);

    /**
     * 公共退款接口
     *
     * @param requestParam 退款请求参数
     * @return 退款返回详情
     */
    RefundTicketRespDTO commonTicketRefund(RefundTicketReqDTO requestParam);
}
