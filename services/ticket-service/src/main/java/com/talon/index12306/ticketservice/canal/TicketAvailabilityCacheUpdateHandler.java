package com.talon.index12306.ticketservice.canal;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.talon.index12306.cache.DistributedCache;
import com.talon.index12306.cache.util.CacheUtil;
import com.talon.index12306.designpattern.strategy.AbstractExecuteStrategy;
import com.talon.index12306.ticketservice.common.enums.CanalExecuteStrategyMarkEnum;
import com.talon.index12306.ticketservice.common.enums.SeatStatusEnum;
import com.talon.index12306.ticketservice.mq.event.CanalBinlogEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.talon.index12306.ticketservice.common.constant.RedisKeyConstant.TRAIN_STATION_REMAINING_TICKET;

/**
 * 列车余票缓存更新组件
 */
@Component
@RequiredArgsConstructor
public class TicketAvailabilityCacheUpdateHandler implements AbstractExecuteStrategy<CanalBinlogEvent, Void> {

    private final DistributedCache distributedCache;

    @Override
    public void execute(CanalBinlogEvent message) {
        List<Map<String, Object>> messageDataList = new ArrayList<>();
        List<Map<String, Object>> actualOldDataList = new ArrayList<>();
        //校验新旧信息
        for (int i = 0; i < message.getOld().size(); i++) {
            Map<String, Object> oldMap = message.getOld().get(i);
            if (StringUtils.hasText(oldMap.get("seat_status").toString())) {
                Map<String, Object> newMap = message.getData().get(i);
                if (StrUtil.equalsAny(newMap.get("seat_status").toString(), String.valueOf(SeatStatusEnum.AVAILABLE.getCode()), String.valueOf(SeatStatusEnum.LOCKED.getCode()))) {
                    messageDataList.add(newMap);
                    actualOldDataList.add(oldMap);
                }
            }
        }
        if (CollUtil.isEmpty(messageDataList) || CollUtil.isEmpty(actualOldDataList)) {
            return;
        }
        Map<String, Map<Integer, Integer>> cacheChangeKeyMap = new HashMap<>();
        for (int i = 0; i < actualOldDataList.size(); i++) {
            Map<String, Object> oldMap = actualOldDataList.get(i);
            Map<String, Object> newMap = messageDataList.get(i);
            String trainId = newMap.get("train_id").toString();
            int oldSeatStatus = Integer.parseInt(oldMap.get("seat_status").toString());
            //判断缓存增减
            int increment = oldSeatStatus == 0 ? -1 : 1;
            String cacheKey = CacheUtil.buildKey(TRAIN_STATION_REMAINING_TICKET + trainId + "_" + newMap.get("start_station") + "_" + newMap.get("end_station"));
            Map<Integer, Integer> seatTypeMap = cacheChangeKeyMap.get(cacheKey);
            if (CollUtil.isEmpty(seatTypeMap)) {
                seatTypeMap = new HashMap<>();
            }
            Integer seatType = Integer.parseInt(newMap.get("seat_type").toString());
            seatTypeMap.compute(seatType, (k, num) -> num == null ? increment : num + increment);
            cacheChangeKeyMap.put(cacheKey, seatTypeMap);
        }
        StringRedisTemplate stringRedisTemplate = (StringRedisTemplate) distributedCache.getInstance();
        stringRedisTemplate.executePipelined((RedisCallback<?>) connection -> {
            cacheChangeKeyMap.forEach((cacheKey, cacheVal) -> cacheVal.forEach((seatType, seatCount) -> connection.hashCommands().hIncrBy(cacheKey.getBytes(), seatType.toString().getBytes(), seatCount)));
            return null;
        });
    }

    @Override
    public String mark() {
        return CanalExecuteStrategyMarkEnum.T_SEAT.getActualTable();
    }
}
