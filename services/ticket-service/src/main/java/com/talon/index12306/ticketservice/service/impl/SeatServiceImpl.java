package com.talon.index12306.ticketservice.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.talon.index12306.cache.DistributedCache;
import com.talon.index12306.ticketservice.common.enums.SeatStatusEnum;
import com.talon.index12306.ticketservice.dao.entity.SeatDO;
import com.talon.index12306.ticketservice.dao.mapper.SeatMapper;
import com.talon.index12306.ticketservice.dto.domain.RouteDTO;
import com.talon.index12306.ticketservice.dto.domain.SeatTypeCountDTO;
import com.talon.index12306.ticketservice.service.SeatService;
import com.talon.index12306.ticketservice.service.TrainStationService;
import com.talon.index12306.ticketservice.service.handler.ticket.dto.TrainPurchaseTicketRespDTO;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.talon.index12306.ticketservice.common.constant.RedisKeyConstant.LOCK_TRAIN_STATION_CARRIAGE_REMAINING_TICKET;
import static com.talon.index12306.ticketservice.common.constant.RedisKeyConstant.TRAIN_STATION_CARRIAGE_REMAINING_TICKET;

/**
 * 座位接口层实现
 */
@Service
@RequiredArgsConstructor
public class SeatServiceImpl extends ServiceImpl<SeatMapper, SeatDO> implements SeatService {

    private final SeatMapper seatMapper;
    private final TrainStationService trainStationService;
    private final DistributedCache distributedCache;
    private final RedissonClient redissonClient;

    @Override
    public List<String> listAvailableSeat(String trainId, String carriageNumber, Integer seatType, String departure, String arrival) {
        LambdaQueryWrapper<SeatDO> queryWrapper = Wrappers.lambdaQuery(SeatDO.class)
                .eq(SeatDO::getTrainId, trainId)
                .eq(SeatDO::getCarriageNumber, carriageNumber)
                .eq(SeatDO::getSeatType, seatType)
                .eq(SeatDO::getStartStation, departure)
                .eq(SeatDO::getEndStation, arrival)
                .eq(SeatDO::getSeatStatus, SeatStatusEnum.AVAILABLE.getCode())
                .select(SeatDO::getSeatNumber);
        List<SeatDO> seatDOList = seatMapper.selectList(queryWrapper);
        return seatDOList.stream().map(SeatDO::getSeatNumber).collect(Collectors.toList());
    }

    @Override
    public List<Integer> listSeatRemainingTicket(String trainId, String departure, String arrival, List<String> trainCarriageList) {
        String key = TRAIN_STATION_CARRIAGE_REMAINING_TICKET + StrUtil.join("_", trainId, departure, arrival);
        StringRedisTemplate stringRedisTemplate = (StringRedisTemplate) distributedCache.getInstance();
        List<Object> trainStationCarriageRemainingTicket = stringRedisTemplate.opsForHash().multiGet(key, Arrays.asList(trainCarriageList.toArray()));
        if (trainStationCarriageRemainingTicket.stream().filter(Objects::isNull).count() > 3) {
            RLock lock = redissonClient.getLock(LOCK_TRAIN_STATION_CARRIAGE_REMAINING_TICKET);
            lock.lock();
            try {
                if ((trainStationCarriageRemainingTicket = stringRedisTemplate.opsForHash().multiGet(key, Arrays.asList(trainCarriageList.toArray()))).stream().filter(Objects::isNull).count() > 3) {
                    SeatDO seatDO = SeatDO.builder()
                            .trainId(Long.parseLong(trainId))
                            .startStation(departure)
                            .endStation(arrival)
                            .build();
                    List<Integer> res = seatMapper.listSeatRemainingTicket(seatDO, trainCarriageList);
                    stringRedisTemplate.executePipelined((RedisCallback<String>) connection -> {
                        for (int i = 0; i < trainCarriageList.size(); i++) {
                            connection.hashCommands().hSet(key.getBytes(), trainCarriageList.get(i).getBytes(), res.get(i).toString().getBytes());
                        }
                        return null;
                    });
                    return res;
                }
            } finally {
                lock.unlock();
            }
        }
        return trainStationCarriageRemainingTicket.stream().map(each -> Integer.parseInt(each.toString())).collect(Collectors.toList());
    }

    @Override
    public List<String> listUsableCarriageNumber(String trainId, Integer carriageType, String departure, String arrival) {
        LambdaQueryWrapper<SeatDO> queryWrapper = Wrappers.lambdaQuery(SeatDO.class)
                .eq(SeatDO::getTrainId, trainId)
                .eq(SeatDO::getSeatType, carriageType)
                .eq(SeatDO::getStartStation, departure)
                .eq(SeatDO::getEndStation, arrival)
                .eq(SeatDO::getSeatStatus, SeatStatusEnum.AVAILABLE.getCode())
                .groupBy(SeatDO::getCarriageNumber)
                .select(SeatDO::getCarriageNumber);
        List<SeatDO> seatDOList = seatMapper.selectList(queryWrapper);
        return seatDOList.stream().map(SeatDO::getCarriageNumber).collect(Collectors.toList());
    }

    @Override
    public List<SeatTypeCountDTO> listSeatTypeCount(Long trainId, String startStation, String endStation, List<Integer> seatTypes) {
        return seatMapper.listSeatTypeCount(trainId, startStation, endStation, seatTypes);
    }

    @Override
    public void lockSeat(String trainId, String departure, String arrival, List<TrainPurchaseTicketRespDTO> trainPurchaseTicketRespList) {
        List<RouteDTO> routeList = trainStationService.listTakeoutTrainStationRoute(trainId, departure, arrival);
        trainPurchaseTicketRespList.forEach(each -> routeList.forEach(item -> {
            LambdaUpdateWrapper<SeatDO> updateWrapper = Wrappers.lambdaUpdate(SeatDO.class)
                    .eq(SeatDO::getTrainId, trainId)
                    .eq(SeatDO::getCarriageNumber, each.getCarriageNumber())
                    .eq(SeatDO::getStartStation, item.getStartStation())
                    .eq(SeatDO::getEndStation, item.getEndStation())
                    .eq(SeatDO::getSeatNumber, each.getSeatNumber());
            SeatDO updateSeatDO = SeatDO.builder()
                    .seatStatus(SeatStatusEnum.LOCKED.getCode())
                    .build();
            seatMapper.update(updateSeatDO, updateWrapper);
        }));
    }

    @Override
    public void unlock(String trainId, String departure, String arrival, List<TrainPurchaseTicketRespDTO> trainPurchaseTicketResults) {
        List<RouteDTO> routeList = trainStationService.listTakeoutTrainStationRoute(trainId, departure, arrival);
        trainPurchaseTicketResults.forEach(each -> routeList.forEach(item -> {
            LambdaUpdateWrapper<SeatDO> updateWrapper = Wrappers.lambdaUpdate(SeatDO.class)
                    .eq(SeatDO::getTrainId, trainId)
                    .eq(SeatDO::getCarriageNumber, each.getCarriageNumber())
                    .eq(SeatDO::getStartStation, item.getStartStation())
                    .eq(SeatDO::getEndStation, item.getEndStation())
                    .eq(SeatDO::getSeatNumber, each.getSeatNumber());
            SeatDO updateSeatDO = SeatDO.builder()
                    .seatStatus(SeatStatusEnum.AVAILABLE.getCode())
                    .build();
            seatMapper.update(updateSeatDO, updateWrapper);
        }));
    }
}