package com.talon.index12306.ticketservice.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.talon.index12306.ticketservice.dao.entity.TrainStationDO;
import com.talon.index12306.ticketservice.dao.mapper.TrainStationMapper;
import com.talon.index12306.ticketservice.dto.domain.RouteDTO;
import com.talon.index12306.ticketservice.dto.resp.TrainStationQueryRespDTO;
import com.talon.index12306.ticketservice.mapstruct.TicketConverter;
import com.talon.index12306.ticketservice.service.TrainStationService;
import com.talon.index12306.ticketservice.util.StationCalculateUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 列车站点接口实现层
 * 公众号：马丁玩编程，回复：加群，添加马哥微信（备注：12306）获取项目资料
 */
@Service
@RequiredArgsConstructor
public class TrainStationServiceImpl implements TrainStationService {

    private final TrainStationMapper trainStationMapper;
    private final TicketConverter ticketConverter;

    @Override
    public List<TrainStationQueryRespDTO> listTrainStationQuery(String trainId) {
        return ticketConverter.TrainStationDOs2RespDTOs(trainStationMapper.selectList(Wrappers.lambdaQuery(TrainStationDO.class)
                .eq(TrainStationDO::getTrainId, trainId)));
    }

    @Override
    public List<RouteDTO> listTrainStationRoute(String trainId, String departure, String arrival) {
        return StationCalculateUtil.throughStation(trainStationMapper.selectList(Wrappers.lambdaQuery(TrainStationDO.class)
                                .eq(TrainStationDO::getTrainId, trainId)
                                .select(TrainStationDO::getDeparture))
                        //拿到该车次所有经历站点
                        .stream().map(TrainStationDO::getDeparture).collect(Collectors.toList()),
                departure,
                arrival);
    }

    @Override
    public List<RouteDTO> listTakeoutTrainStationRoute(String trainId, String departure, String arrival) {
        return StationCalculateUtil.takeoutStation(trainStationMapper.selectList(Wrappers.lambdaQuery(TrainStationDO.class)
                                .eq(TrainStationDO::getTrainId, trainId)
                                .select(TrainStationDO::getDeparture))
                        .stream().map(TrainStationDO::getDeparture).collect(Collectors.toList()),
                departure,
                arrival);

    }
}