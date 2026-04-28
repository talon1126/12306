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

package com.talon.index12306.ticketservice.controller;

import com.talon.index12306.convention.result.Result;
import com.talon.index12306.ticketservice.dto.domain.RouteDTO;
import com.talon.index12306.ticketservice.dto.resp.TrainStationQueryRespDTO;
import com.talon.index12306.ticketservice.service.TrainStationService;
import com.talon.index12306.web.Results;
import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 列车站点控制层
 * 公众号：马丁玩编程，回复：加群，添加马哥微信（备注：12306）获取项目资料
 */
@RestController
@RequiredArgsConstructor
public class TrainStationController {

    private final TrainStationService trainStationService;

    /**
     * 根据列车 ID 查询站点信息
     */
    @GetMapping("/api/ticket-service/train-station/query")
    public Result<List<TrainStationQueryRespDTO>> listTrainStationQuery(String trainId) {
        return Results.success(trainStationService.listTrainStationQuery(trainId));
    }

    @Deprecated
    @GetMapping("/api/ticket-service/train-station/abc")
    public  Result<List<RouteDTO>> listTrainStationRoute(String trainId, String departure, String arrival) {
        return Results.success(trainStationService.listTrainStationRoute(trainId, departure, arrival));
    }

}
