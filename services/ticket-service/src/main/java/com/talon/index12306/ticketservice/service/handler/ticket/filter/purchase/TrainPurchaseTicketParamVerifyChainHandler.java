package com.talon.index12306.ticketservice.service.handler.ticket.filter.purchase;

import cn.hutool.core.collection.CollUtil;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.talon.index12306.cache.DistributedCache;
import com.talon.index12306.convention.exception.ClientException;
import com.talon.index12306.ticketservice.dao.entity.TrainDO;
import com.talon.index12306.ticketservice.dao.entity.TrainStationDO;
import com.talon.index12306.ticketservice.dao.mapper.TrainMapper;
import com.talon.index12306.ticketservice.dao.mapper.TrainStationMapper;
import com.talon.index12306.ticketservice.dto.req.PurchaseTicketReqDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static com.talon.index12306.ticketservice.common.constant.Index12306Constant.ADVANCE_TICKET_DAY;
import static com.talon.index12306.ticketservice.common.constant.RedisKeyConstant.TRAIN_INFO;
import static com.talon.index12306.ticketservice.common.constant.RedisKeyConstant.TRAIN_STATION_STOPOVER_DETAIL;

/**
 * 购票流程过滤器之验证参数是否有效
 * 验证参数有效这个流程会大量交互缓存，为了优化性能需要使用 Lua。为了方便大家理解流程，这里使用多次调用缓存
 * 公众号：马丁玩编程，回复：加群，添加马哥微信（备注：12306）获取项目资料
 */
@Component
@RequiredArgsConstructor
public class TrainPurchaseTicketParamVerifyChainHandler implements TrainPurchaseTicketChainFilter<PurchaseTicketReqDTO> {

    private final TrainMapper trainMapper;
    private final TrainStationMapper trainStationMapper;
    private final DistributedCache distributedCache;

    @Override
    public void handler(PurchaseTicketReqDTO requestParam) {
        // 查询会员购票车次是否存在，通过封装后安全的 Get 方法
        TrainDO trainDO = distributedCache.safeGet(
                TRAIN_INFO + requestParam.getTrainId(),
                TrainDO.class,
                () -> trainMapper.selectById(requestParam.getTrainId()),
                ADVANCE_TICKET_DAY,
                TimeUnit.DAYS);
        if (Objects.isNull(trainDO)) {
            throw new ClientException("请检查车次是否存在");
        }
        // 车站是否存在车次中，以及车站的顺序是否正确
        String trainStationStopoverDetailStr = distributedCache.safeGet(
                TRAIN_STATION_STOPOVER_DETAIL + requestParam.getTrainId(),
                String.class,
                () -> {
                    LambdaQueryWrapper<TrainStationDO> queryWrapper = Wrappers.lambdaQuery(TrainStationDO.class)
                            .eq(TrainStationDO::getTrainId, requestParam.getTrainId())
                            .select(TrainStationDO::getDeparture);
                    List<TrainStationDO> actualTrainStationList = trainStationMapper.selectList(queryWrapper);
                    return CollUtil.isNotEmpty(actualTrainStationList) ? JSON.toJSONString(actualTrainStationList) : null;
                },
                ADVANCE_TICKET_DAY,
                TimeUnit.DAYS
        );
        List<TrainStationDO> trainDOList = JSON.parseArray(trainStationStopoverDetailStr, TrainStationDO.class);
        if (!validateStation(
                trainDOList.stream().map(TrainStationDO::getDeparture).toList(),
                requestParam.getDeparture(),
                requestParam.getArrival())) {
            throw new ClientException("列车车站数据错误");
        }
    }

    @Override
    public int getOrder() {
        return 10;
    }

    public boolean validateStation(List<String> stationList, String startStation, String endStation) {
        int index1 = stationList.indexOf(startStation);
        int index2 = stationList.indexOf(endStation);
        if (index1 == -1 || index2 == -1) {
            return false;
        }
        return index2 >= index1;
    }
}
