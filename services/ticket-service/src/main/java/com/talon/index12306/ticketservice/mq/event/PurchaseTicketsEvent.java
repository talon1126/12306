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

package com.talon.index12306.ticketservice.mq.event;

import com.talon.index12306.framework.user.core.UserInfoDTO;
import com.talon.index12306.ticketservice.dto.req.PurchaseTicketReqDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户购票事件
 * <p>
 * 作者：马丁
 * 加项目群：早加入就是优势！500人内部项目群，分享的知识总有你需要的 <a href="https://t.zsxq.com/cw7b9" />
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseTicketsEvent {

    /**
     * 订单追踪 ID，用户下单 v3 接口专属
     */
    private String orderTrackingId;

    /**
     * 购票方法原始请求入参
     */
    private PurchaseTicketReqDTO originalRequestParam;

    /**
     * 用户上下文信息
     */
    private UserInfoDTO userInfo;
}
