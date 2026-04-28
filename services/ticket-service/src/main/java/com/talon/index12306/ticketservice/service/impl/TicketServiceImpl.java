package com.talon.index12306.ticketservice.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.collection.ListUtil;
import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.talon.index12306.base.ApplicationContextHolder;
import com.talon.index12306.cache.DistributedCache;
import com.talon.index12306.cache.util.CacheUtil;
import com.talon.index12306.common.util.BeanUtil;
import com.talon.index12306.convention.exception.ServiceException;
import com.talon.index12306.convention.result.Result;
import com.talon.index12306.designpattern.chain.AbstractChainContext;
import com.talon.index12306.distributedid.util.SnowflakeIdUtil;
import com.talon.index12306.framework.user.core.UserContext;
import com.talon.index12306.idempotent.annotation.Idempotent;
import com.talon.index12306.idempotent.enums.IdempotentSceneEnum;
import com.talon.index12306.idempotent.enums.IdempotentTypeEnum;
import com.talon.index12306.ticketservice.common.enums.*;
import com.talon.index12306.ticketservice.dao.entity.*;
import com.talon.index12306.ticketservice.dao.mapper.*;
import com.talon.index12306.ticketservice.dto.domain.PurchaseTicketPassengerDetailDTO;
import com.talon.index12306.ticketservice.dto.domain.SeatClassDTO;
import com.talon.index12306.ticketservice.dto.domain.SeatTypeCountDTO;
import com.talon.index12306.ticketservice.dto.domain.TicketListDTO;
import com.talon.index12306.ticketservice.dto.req.CancelTicketOrderReqDTO;
import com.talon.index12306.ticketservice.dto.req.PurchaseTicketReqDTO;
import com.talon.index12306.ticketservice.dto.req.RefundTicketReqDTO;
import com.talon.index12306.ticketservice.dto.req.TicketPageQueryReqDTO;
import com.talon.index12306.ticketservice.dto.resp.*;
import com.talon.index12306.ticketservice.mq.event.PurchaseTicketsEvent;
import com.talon.index12306.ticketservice.mq.produce.PurchaseTicketsSendProduce;
import com.talon.index12306.ticketservice.remote.PayRemoteService;
import com.talon.index12306.ticketservice.remote.TicketOrderRemoteService;
import com.talon.index12306.ticketservice.remote.dto.PayInfoRespDTO;
import com.talon.index12306.ticketservice.remote.dto.TicketOrderCreateRemoteReqDTO;
import com.talon.index12306.ticketservice.remote.dto.TicketOrderItemCreateRemoteReqDTO;
import com.talon.index12306.ticketservice.service.SeatService;
import com.talon.index12306.ticketservice.service.TicketService;
import com.talon.index12306.ticketservice.service.TrainStationService;
import com.talon.index12306.ticketservice.service.cache.SeatMarginCacheLoader;
import com.talon.index12306.ticketservice.service.handler.ticket.dto.TokenResultDTO;
import com.talon.index12306.ticketservice.service.handler.ticket.dto.TrainPurchaseTicketRespDTO;
import com.talon.index12306.ticketservice.service.handler.ticket.select.TrainSeatTypeSelector;
import com.talon.index12306.ticketservice.service.handler.ticket.tokenbucket.TicketAvailabilityTokenBucket;
import com.talon.index12306.ticketservice.util.DateUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import static com.talon.index12306.ticketservice.common.constant.Index12306Constant.ADVANCE_TICKET_DAY;
import static com.talon.index12306.ticketservice.common.constant.RedisKeyConstant.*;
import static com.talon.index12306.ticketservice.util.DateUtil.convertDateToLocalTime;

/**
 * 车票接口实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TicketServiceImpl extends ServiceImpl<TicketMapper, TicketDO> implements TicketService, CommandLineRunner {

    private final TrainMapper trainMapper;
    private final TrainStationRelationMapper trainStationRelationMapper;
    private final TrainStationPriceMapper trainStationPriceMapper;
    private final DistributedCache distributedCache;
    private final TicketOrderRemoteService ticketOrderRemoteService;
    private final PayRemoteService payRemoteService;
    private final StationMapper stationMapper;
    private final SeatMapper seatMapper;
    private final SeatService seatService;
    private final TrainStationService trainStationService;
    private final TrainSeatTypeSelector trainSeatTypeSelector;
    private final SeatMarginCacheLoader seatMarginCacheLoader;
    private final AbstractChainContext<TicketPageQueryReqDTO> ticketPageQueryAbstractChainContext;
    private final AbstractChainContext<PurchaseTicketReqDTO> purchaseTicketAbstractChainContext;
    private final AbstractChainContext<RefundTicketReqDTO> refundReqDTOAbstractChainContext;
    private final RedissonClient redissonClient;
    private final ConfigurableEnvironment environment;
    private final TicketAvailabilityTokenBucket ticketAvailabilityTokenBucket;
    private final PurchaseTicketsSendProduce purchaseTicketsSendProduce;
    private TicketService ticketService;
    private final OrderTrackingMapper orderTrackingMapper;


    @Value("${ticket.availability.cache-update.type:}")
    private String ticketAvailabilityCacheUpdateType;
    @Value("${framework.cache.redis.prefix:}")
    private String cacheRedisPrefix;

    @Override
    public TicketPageQueryRespDTO pageListTicketQueryV1(TicketPageQueryReqDTO requestParam) {
        //前置工作 校验参数 开启责任链
        ticketPageQueryAbstractChainContext.handler(TicketChainMarkEnum.TRAIN_QUERY_FILTER.name(), requestParam);
        StringRedisTemplate stringRedisTemplate = (StringRedisTemplate) distributedCache.getInstance();
        HashOperations<String, Object, Object> opsForHash = stringRedisTemplate.opsForHash();
        //                 key     region_train_station_mapping
        //  地区与站点
        //                             key     地区Code
        //                 value(Map)
        //                             value   地区名称
        List<Object> stationDetails = opsForHash.multiGet(REGION_TRAIN_STATION_MAPPING, List.of(requestParam.getFromStation(), requestParam.getToStation()));
        //参数合法 但是有null说明缓存不完整或无
        if (stationDetails.stream().anyMatch(Objects::isNull)) {
            RLock lock = redissonClient.getLock(LOCK_REGION_TRAIN_STATION_MAPPING);
            lock.lock();
            try {
                //dcl
                stationDetails = opsForHash.multiGet(REGION_TRAIN_STATION_MAPPING, List.of(requestParam.getFromStation(), requestParam.getToStation()));
                if (stationDetails.stream().anyMatch(Objects::isNull)) {
                    //构建缓存
                    List<StationDO> stationDOS = stationMapper.selectList(Wrappers.emptyWrapper());
                    HashMap<String, String> cache = new HashMap<>();
                    for (StationDO stationDO : stationDOS) {
                        cache.put(stationDO.getCode(), stationDO.getRegionName());
                    }
                    opsForHash.putAll(REGION_TRAIN_STATION_MAPPING, cache);
                    stationDetails.clear();
                    stationDetails.add(cache.get(requestParam.getFromStation()));
                    stationDetails.add(cache.get(requestParam.getToStation()));
                }
            } finally {
                lock.unlock();
            }
        }
        //          key     region_train_station:起始城市_终点城市_日期
        // 站点查询
        //                      key     列车ID_起始站点_终点
        //          value(Map)
        //                      value   列车详细信息
        //起始城市_终点城市_日期
        List<TicketListDTO> seatResults = new ArrayList<>();
        String buildRegionTrainStationHashKey = String.format(REGION_TRAIN_STATION, stationDetails.get(0), stationDetails.get(1));
        Map<Object, Object> regionTrainStationAllMap = stringRedisTemplate.opsForHash().entries(buildRegionTrainStationHashKey);
        //regionTrainStationAllMap 在缓存中不存在 重建缓存 填充seatResults
        if (MapUtil.isEmpty(regionTrainStationAllMap)) {
            //需要构建缓存
            RLock lock = redissonClient.getLock(LOCK_REGION_TRAIN_STATION);
            lock.lock();
            try {
                //dcl
                regionTrainStationAllMap = stringRedisTemplate.opsForHash().entries(buildRegionTrainStationHashKey);
                if (MapUtil.isEmpty(regionTrainStationAllMap)) {
                    //构建缓存
                    List<TrainStationRelationDO> trainStationRelationList = trainStationRelationMapper.selectList(Wrappers.lambdaQuery(TrainStationRelationDO.class)
                            .eq(TrainStationRelationDO::getStartRegion, stationDetails.get(0))
                            .eq(TrainStationRelationDO::getEndRegion, stationDetails.get(1)));
                    for (TrainStationRelationDO each : trainStationRelationList) {
                        //封装 TicketListDTO
                        TrainDO trainDO = distributedCache.safeGet(
                                TRAIN_INFO + each.getTrainId(),
                                TrainDO.class,
                                () -> trainMapper.selectById(each.getTrainId()),
                                ADVANCE_TICKET_DAY,
                                TimeUnit.DAYS);
                        TicketListDTO result = new TicketListDTO();
                        result.setTrainId(String.valueOf(trainDO.getId()));
                        result.setTrainNumber(trainDO.getTrainNumber());
                        result.setDepartureTime(convertDateToLocalTime(each.getDepartureTime(), "HH:mm"));
                        result.setArrivalTime(convertDateToLocalTime(each.getArrivalTime(), "HH:mm"));
                        result.setDuration(DateUtil.calculateHourDifference(each.getDepartureTime(), each.getArrivalTime()));
                        result.setDeparture(each.getDeparture());
                        result.setArrival(each.getArrival());
                        result.setDepartureFlag(each.getDepartureFlag());
                        result.setArrivalFlag(each.getArrivalFlag());
                        result.setTrainType(trainDO.getTrainType());
                        result.setTrainBrand(trainDO.getTrainBrand());
                        if (StrUtil.isNotBlank(trainDO.getTrainTag())) {
                            result.setTrainTags(StrUtil.split(trainDO.getTrainTag(), ","));
                        }
                        long betweenDay = cn.hutool.core.date.DateUtil.betweenDay(each.getDepartureTime(), each.getArrivalTime(), false);
                        result.setDaysArrived((int) betweenDay);
                        result.setSaleStatus(new Date().after(trainDO.getSaleTime()) ? 0 : 1);
                        result.setSaleTime(convertDateToLocalTime(trainDO.getSaleTime(), "MM-dd HH:mm"));
                        seatResults.add(result);
                        //列车ID_起始站点_终点                                                          //列车详细信息
                        regionTrainStationAllMap.put(CacheUtil.buildKey(String.valueOf(each.getTrainId()), each.getDeparture(), each.getArrival()), JSON.toJSONString(result));
                    }
                    //写入缓存
                    stringRedisTemplate.opsForHash().putAll(buildRegionTrainStationHashKey, regionTrainStationAllMap);
                }
            } finally {
                lock.unlock();
            }
        }
        //regionTrainStationAllMap 在缓存中存在 填充seatResults
        seatResults = CollUtil.isEmpty(seatResults)
                ? regionTrainStationAllMap.values().stream().map(each -> JSON.parseObject(each.toString(), TicketListDTO.class)).toList()
                : seatResults;
        //查询列车余票信息并填充到基本信息中
        for (TicketListDTO each : seatResults) {
            // 加载列车对应的座位价格数据
            String trainStationPriceStr = distributedCache.safeGet(
                    String.format(TRAIN_STATION_PRICE, each.getTrainId(), each.getDeparture(), each.getArrival()),
                    String.class,
                    () ->
                            JSON.toJSONString(trainStationPriceMapper.selectList(Wrappers.lambdaQuery(TrainStationPriceDO.class)
                                    .eq(TrainStationPriceDO::getDeparture, each.getDeparture())
                                    .eq(TrainStationPriceDO::getArrival, each.getArrival())
                                    .eq(TrainStationPriceDO::getTrainId, each.getTrainId())))
                    ,
                    ADVANCE_TICKET_DAY,
                    TimeUnit.DAYS
            );
            List<TrainStationPriceDO> trainStationPriceDOList = JSON.parseArray(trainStationPriceStr, TrainStationPriceDO.class);
            //加载列车对应的余票数据
            List<SeatClassDTO> seatClassList = new ArrayList<>();
            for (TrainStationPriceDO item : trainStationPriceDOList) {
                //通过 trainId seatType from to 查询seat
                String seatType = String.valueOf(item.getSeatType());
                String keySuffix = StrUtil.join("_", each.getTrainId(), item.getDeparture(), item.getArrival());
                //查取缓存
                Object quantityObj = stringRedisTemplate.opsForHash().get(TRAIN_STATION_REMAINING_TICKET + keySuffix, seatType);
                int quantity = Optional.ofNullable(quantityObj)
                        .map(Object::toString)
                        .map(Integer::parseInt)
                        .orElseGet(() -> {
                            Map<String, String> load = seatMarginCacheLoader.load(String.valueOf(each.getTrainId()), seatType, item.getDeparture(), item.getArrival());
                            return Optional.ofNullable(load.get(seatType)).map(Integer::parseInt).orElse(0);
                        });
                seatClassList.add(new SeatClassDTO(item.getSeatType(), quantity, new BigDecimal(item.getPrice()).divide(new BigDecimal("100"), 1, RoundingMode.HALF_UP), false));
            }
            each.setSeatClassList(seatClassList);
        }
        return TicketPageQueryRespDTO.builder()
                .trainList(seatResults)
                .departureStationList(buildDepartureStationList(seatResults))
                .arrivalStationList(buildArrivalStationList(seatResults))
                .trainBrandList(buildTrainBrandList(seatResults))
                .seatClassTypeList(buildSeatClassList(seatResults))
                .build();
    }

    @Override
    public TicketPageQueryRespDTO pageListTicketQueryV2(TicketPageQueryReqDTO requestParam) {
        ticketPageQueryAbstractChainContext.handler(TicketChainMarkEnum.TRAIN_QUERY_FILTER.name(), requestParam);
        StringRedisTemplate stringRedisTemplate = (StringRedisTemplate) distributedCache.getInstance();
        HashOperations<String, Object, Object> opsForHash = stringRedisTemplate.opsForHash();
        //拿到code所属地区
        List<Object> stationDetails = opsForHash.multiGet(REGION_TRAIN_STATION_MAPPING, List.of(requestParam.getFromStation(), requestParam.getToStation()));
        //缓存不存在
        if (stationDetails.stream().anyMatch(Objects::isNull)) {
            RLock lock = redissonClient.getLock(LOCK_REGION_TRAIN_STATION_MAPPING);
            lock.lock();
            try {
                //dcl
                stationDetails = opsForHash.multiGet(REGION_TRAIN_STATION_MAPPING, List.of(requestParam.getFromStation(), requestParam.getToStation()));
                if (stationDetails.stream().anyMatch(Objects::isNull)) {
                    //构建缓存
                    List<StationDO> stationDOS = stationMapper.selectList(Wrappers.emptyWrapper());
                    HashMap<String, String> cache = new HashMap<>();
                    for (StationDO stationDO : stationDOS) {
                        cache.put(stationDO.getCode(), stationDO.getRegionName());
                    }
                    opsForHash.putAll(REGION_TRAIN_STATION_MAPPING, cache);
                    stationDetails.clear();
                    stationDetails.add(cache.get(requestParam.getFromStation()));
                    stationDetails.add(cache.get(requestParam.getToStation()));
                }
            } finally {
                lock.unlock();
            }
        }
        //拿到列车详细信息
        //修改1:加日期
        String departureDate = requestParam.getDepartureDate().toString().substring(8, 10);
        String buildRegionTrainStationHashKey = String.format(REGION_TRAIN_STATION, stationDetails.get(0), stationDetails.get(1)) + "_" + departureDate;
        Map<Object, Object> regionTrainStationAllMap = opsForHash.entries(buildRegionTrainStationHashKey);
        //相当于所有 TrainStationService.listTrainStationRoute 拿到所有路线组合 包含当前出发与终点 的列车的详细信息
        List<TicketListDTO> seatResults = new ArrayList<>();
        //缓存不存在
        if (MapUtil.isEmpty(regionTrainStationAllMap)) {
            RLock lock = redissonClient.getLock(LOCK_REGION_TRAIN_STATION);
            lock.lock();
            try {
                //dcl
                regionTrainStationAllMap = stringRedisTemplate.opsForHash().entries(buildRegionTrainStationHashKey);
                if (MapUtil.isEmpty(regionTrainStationAllMap)) {
                    //构建缓存
                    List<TrainStationRelationDO> trainStationRelationList = trainStationRelationMapper.selectList(Wrappers.lambdaQuery(TrainStationRelationDO.class)
                            .eq(TrainStationRelationDO::getStartRegion, stationDetails.get(0))
                            .eq(TrainStationRelationDO::getEndRegion, stationDetails.get(1))
//                            .eq(TrainStationRelationDO::getDepartureTime, requestParam.getDepartureDate())
                    );
                    for (TrainStationRelationDO each : trainStationRelationList) {
                        //封装 TicketListDTO
                        TrainDO trainDO = distributedCache.safeGet(
                                TRAIN_INFO + each.getTrainId(),
                                TrainDO.class,
                                () -> trainMapper.selectById(each.getTrainId()),
                                ADVANCE_TICKET_DAY,
                                TimeUnit.DAYS);
                        TicketListDTO result = new TicketListDTO();
                        result.setTrainId(String.valueOf(trainDO.getId()));
                        result.setTrainNumber(trainDO.getTrainNumber());
                        result.setDepartureTime(convertDateToLocalTime(each.getDepartureTime(), "HH:mm"));
                        result.setArrivalTime(convertDateToLocalTime(each.getArrivalTime(), "HH:mm"));
                        result.setDuration(DateUtil.calculateHourDifference(each.getDepartureTime(), each.getArrivalTime()));
                        result.setDeparture(each.getDeparture());
                        result.setArrival(each.getArrival());
                        result.setDepartureFlag(each.getDepartureFlag());
                        result.setArrivalFlag(each.getArrivalFlag());
                        result.setTrainType(trainDO.getTrainType());
                        result.setTrainBrand(trainDO.getTrainBrand());
                        if (StrUtil.isNotBlank(trainDO.getTrainTag())) {
                            result.setTrainTags(StrUtil.split(trainDO.getTrainTag(), ","));
                        }
                        long betweenDay = cn.hutool.core.date.DateUtil.betweenDay(each.getDepartureTime(), each.getArrivalTime(), false);
                        result.setDaysArrived((int) betweenDay);
                        result.setSaleStatus(new Date().after(trainDO.getSaleTime()) ? 0 : 1);
                        result.setSaleTime(convertDateToLocalTime(trainDO.getSaleTime(), "MM-dd HH:mm"));
                        seatResults.add(result);
                        //列车ID_起始站点_终点                                                          //列车详细信息
                        regionTrainStationAllMap.put(CacheUtil.buildKey(String.valueOf(each.getTrainId()), each.getDeparture(), each.getArrival()), JSON.toJSONString(result));
                    }
                    //写入缓存
                    stringRedisTemplate.opsForHash().putAll(buildRegionTrainStationHashKey, regionTrainStationAllMap);
                }
            } finally {
                lock.unlock();
            }
        }
        seatResults = CollUtil.isEmpty(seatResults)
                //缓存存在
                ? regionTrainStationAllMap.values().stream().map(each -> JSON.parseObject(each.toString(), TicketListDTO.class)).toList()
                //缓存不存在
                : seatResults;
        List<String> tranStationPriceKeyList = seatResults.stream().map(each -> String.format(cacheRedisPrefix + TRAIN_STATION_PRICE, each.getTrainId(), each.getDeparture(), each.getArrival())).toList();
        //拿到每种列车每种座位两站之间价格相关属性实体
        List<Object> tranStationPriceObjects = stringRedisTemplate.executePipelined((RedisCallback<String>) connection -> {
            for (String s : tranStationPriceKeyList) {
                connection.stringCommands().get(s.getBytes());
            }
            return null;
        });
        //缓存不存在
        if (tranStationPriceObjects.stream().anyMatch(Objects::isNull)) {
            RLock lock = redissonClient.getLock(LOCK_TRAIN_STATION_PRICE);
            lock.lock();
            try {
                tranStationPriceObjects = stringRedisTemplate.executePipelined((RedisCallback<String>) connection -> {
                    for (String s : tranStationPriceKeyList) {
                        connection.stringCommands().get(s.getBytes());
                    }
                    return null;
                });
                if (tranStationPriceObjects.stream().anyMatch(Objects::isNull)) {
                    List<Object> objects = new ArrayList<>();
                    stringRedisTemplate.executePipelined((RedisCallback<String>) connection -> {
                        for (String s : tranStationPriceKeyList) {
                            String[] condition = s.substring(s.lastIndexOf(":") + 1).split("_");
                            String res = JSON.toJSONString(trainStationPriceMapper.selectList(Wrappers.lambdaQuery(TrainStationPriceDO.class)
                                    .eq(TrainStationPriceDO::getDeparture, condition[1])
                                    .eq(TrainStationPriceDO::getArrival, condition[2])
                                    .eq(TrainStationPriceDO::getTrainId, condition[0])));
                            connection.stringCommands().set(s.getBytes(), res.getBytes());
                            objects.add(res);
                        }
                        return null;
                    });
                    tranStationPriceObjects = new ArrayList<>(objects);
                }
            } finally {
                lock.unlock();
            }
        }
        List<TrainStationPriceDO> trainStationPriceDOList = new ArrayList<>();
        List<String> trainStationRemainingKeyList = new ArrayList<>();
        //解析对象 封装key
        for (Object priceObject : tranStationPriceObjects) {
            List<TrainStationPriceDO> trainStationPriceDOS = JSON.parseArray(priceObject.toString(), TrainStationPriceDO.class);
            trainStationPriceDOList.addAll(trainStationPriceDOS);
            for (TrainStationPriceDO item : trainStationPriceDOS) {
                String trainStationRemainingKey = cacheRedisPrefix + TRAIN_STATION_REMAINING_TICKET + StrUtil.join("_", item.getTrainId(), item.getDeparture(), item.getArrival());
                trainStationRemainingKeyList.add(trainStationRemainingKey);
            }
        }
        //拿到余票数据
        List<Object> trainStationRemainingTickets = stringRedisTemplate.executePipelined((RedisCallback<String>) connection -> {
            for (int i = 0; i < trainStationRemainingKeyList.size(); i++) {
                connection.hashCommands().hGet(trainStationRemainingKeyList.get(i).getBytes(), trainStationPriceDOList.get(i).getSeatType().toString().getBytes());
            }
            return null;
        });
        //缓存不存在
        if (trainStationRemainingTickets.stream().anyMatch(Objects::isNull)) {
            RLock lock = redissonClient.getLock(LOCK_TRAIN_STATION_REMAINING_TICKET);
            lock.lock();
            try {
                trainStationRemainingTickets = stringRedisTemplate.executePipelined((RedisCallback<String>) connection -> {
                    for (int i = 0; i < trainStationRemainingKeyList.size(); i++) {
                        connection.hashCommands().hGet(trainStationRemainingKeyList.get(i).getBytes(), trainStationPriceDOList.get(i).getSeatType().toString().getBytes());
                    }
                    return null;
                });
                if (trainStationRemainingTickets.stream().anyMatch(Objects::isNull)) {
                    List<Object> objects = new ArrayList<>();
                    for (TrainStationPriceDO trainStationPriceDO : trainStationPriceDOList) {
                        String seatType = trainStationPriceDO.getSeatType().toString();
                        Map<String, String> load = seatMarginCacheLoader.load(String.valueOf(trainStationPriceDO.getTrainId()), seatType, trainStationPriceDO.getDeparture(), trainStationPriceDO.getArrival());
                        objects.add(Optional.ofNullable(load.get(seatType)).orElse("0"));
                    }
                    trainStationRemainingTickets = new ArrayList<>(objects);
                }
            } finally {
                lock.unlock();
            }
        }
        //封装列车信息
        for (TicketListDTO each : seatResults) {
            //拿到当前车次的座位类型
            List<Integer> seatTypes = VehicleTypeEnum.findSeatTypesByCode(each.getTrainType());
            //拿到余票信息和价格信息
            List<Object> remainingTicket = new ArrayList<>(trainStationRemainingTickets.subList(0, seatTypes.size()));
            List<TrainStationPriceDO> trainStationPriceDOSub = new ArrayList<>(trainStationPriceDOList.subList(0, seatTypes.size()));
            trainStationRemainingTickets.subList(0, seatTypes.size()).clear();
            trainStationPriceDOList.subList(0, seatTypes.size()).clear();
            List<SeatClassDTO> seatClassList = new ArrayList<>();
            for (int i = 0; i < trainStationPriceDOSub.size(); i++) {
                TrainStationPriceDO trainStationPriceDO = trainStationPriceDOSub.get(i);
                SeatClassDTO seatClassDTO = SeatClassDTO.builder()
                        .type(trainStationPriceDO.getSeatType())
                        .quantity(Integer.parseInt(remainingTicket.get(i).toString()))
                        .price(new BigDecimal(trainStationPriceDO.getPrice()).divide(new BigDecimal("100"), 1, RoundingMode.HALF_UP))
                        .candidate(false)
                        .build();
                seatClassList.add(seatClassDTO);
            }
            each.setSeatClassList(seatClassList);
        }
        return TicketPageQueryRespDTO.builder()
                .trainList(seatResults)
                .departureStationList(buildDepartureStationList(seatResults))
                .arrivalStationList(buildArrivalStationList(seatResults))
                .trainBrandList(buildTrainBrandList(seatResults))
                .seatClassTypeList(buildSeatClassList(seatResults))
                .build();
    }


    @Override
    public TicketPurchaseRespDTO purchaseTicketsV1(PurchaseTicketReqDTO requestParam) {
        return null;
    }

    @Idempotent(
            uniqueKeyPrefix = "index12306-ticket:lock_purchase-tickets:",
            key = "T(com.talon.index12306.base.ApplicationContextHolder).getBean('environment').getProperty('unique-name', '')"
                    + "+'_'+"
                    + "T(com.talon.index12306.framework.user.core.UserContext).getUsername()",
            message = "正在执行下单流程，请稍后...",
            scene = IdempotentSceneEnum.RESTAPI,
            type = IdempotentTypeEnum.SPEL
    )
    @Override
    public TicketPurchaseRespDTO purchaseTicketsV2(PurchaseTicketReqDTO requestParam) {
        purchaseTicketAbstractChainContext.handler(TicketChainMarkEnum.TRAIN_PURCHASE_TICKET_FILTER.name(), requestParam);
        //令牌桶获取令牌
        TokenResultDTO tokenResultDTO = ticketAvailabilityTokenBucket.takeTokenFromBucket(requestParam);
        //获取令牌失败
        if (tokenResultDTO.getTokenIsNull()) {
            //是否是缓存与数据库不一致造成(数据库还有余票)
            if (tokenTicketsRefreshMap.getIfPresent(requestParam.getTrainId()) == null) {
                synchronized (TicketService.class) {
                    //尝试刷新tokenTicketsRefreshMap
                    if (tokenTicketsRefreshMap.getIfPresent(requestParam.getTrainId()) == null) {
                        tokenTicketsRefreshMap.put(requestParam.getTrainId(), new Object());
                        tokenIsNullRefreshToken(requestParam, tokenResultDTO);
                    }
                }
            }
            throw new ServiceException("列车站点已无余票");
        }
        List<ReentrantLock> localLockList = new ArrayList<>();
        List<RLock> distributedLockList = new ArrayList<>();
        //每个Map对应的key的顺序都是一致的，如果张三的顺序为1，3，2，那么李四的顺序就为1，2
        Map<Integer, List<PurchaseTicketPassengerDetailDTO>> seatTypeMap = requestParam.getPassengers().stream()
                .collect(Collectors.groupingBy(PurchaseTicketPassengerDetailDTO::getSeatType));
        seatTypeMap.forEach((searType, count) -> {
            String lockKey = environment.resolvePlaceholders(String.format(LOCK_PURCHASE_TICKETS_V2, requestParam.getTrainId(), searType));
            //本地锁
            ReentrantLock localLock = localLockMap.getIfPresent(lockKey);
            if (localLock == null) {
                synchronized (TicketService.class) {
                    if ((localLock = localLockMap.getIfPresent(lockKey)) == null) {
                        localLock = new ReentrantLock(true);
                        localLockMap.put(lockKey, localLock);
                    }
                }
            }
            localLockList.add(localLock);
            //获取分布式锁
            distributedLockList.add(redissonClient.getFairLock(lockKey));
        });
        try {
            localLockList.forEach(ReentrantLock::lock);
            distributedLockList.forEach(RLock::lock);
            //加锁成功
            return ticketService.executePurchaseTickets(requestParam);
        } finally {
            localLockList.forEach(localLock -> {
                try {
                    localLock.unlock();
                } catch (Throwable ignored) {
                }
            });
            distributedLockList.forEach(distributedLock -> {
                try {
                    distributedLock.unlock();
                } catch (Throwable ignored) {
                }
            });
        }
    }

    @Idempotent(
            uniqueKeyPrefix = "index12306-ticket:lock_purchase-tickets:",
            key = "T(com.talon.index12306.base.ApplicationContextHolder).getBean('environment').getProperty('unique-name', '')"
                    + "+'_'+"
                    + "T(com.talon.index12306.framework.user.core.UserContext).getUsername()",
            message = "正在执行下单流程，请稍后...",
            scene = IdempotentSceneEnum.RESTAPI,
            type = IdempotentTypeEnum.SPEL
    )
    @Override
    public String purchaseTicketsV3(PurchaseTicketReqDTO requestParam) {
        purchaseTicketAbstractChainContext.handler(TicketChainMarkEnum.TRAIN_PURCHASE_TICKET_FILTER.name(), requestParam);
        TokenResultDTO tokenResult = ticketAvailabilityTokenBucket.takeTokenFromBucket(requestParam);
        if (tokenResult.getTokenIsNull()) {
            if (tokenTicketsRefreshMap.asMap().putIfAbsent(requestParam.getTrainId(), true) == null) {
                tokenIsNullRefreshToken(requestParam, tokenResult);
            }
            throw new ServiceException("列车站点已无余票");
        }
        PurchaseTicketsEvent purchaseTicketsEvent = PurchaseTicketsEvent.builder()
                .orderTrackingId(SnowflakeIdUtil.nextIdStr())
                .originalRequestParam(requestParam)
                .userInfo(UserContext.get())
                .build();
        SendResult sendResult = purchaseTicketsSendProduce.sendMessage(purchaseTicketsEvent);
        if (!Objects.equals(sendResult.getSendStatus(), SendStatus.SEND_OK)) {
            throw new ServiceException("发送用户异步购票消息失败");
        }
        // 返回全局唯一标识，当做用户购票异步返回和订单之间的关联关系
        return purchaseTicketsEvent.getOrderTrackingId();
    }

    @Override
    public OrderTrackingRespDTO purchaseTicketsV3Query(String orderTrackingId) {
        LambdaQueryWrapper<OrderTrackingDO> queryWrapper = Wrappers.lambdaQuery(OrderTrackingDO.class)
                .eq(OrderTrackingDO::getId, orderTrackingId)
                // 添加 username 字段，以防止非登录用户访问其他用户的数据，避免数据横向越权
                .eq(OrderTrackingDO::getUsername, UserContext.getUsername());
        OrderTrackingDO orderTrackingDO = orderTrackingMapper.selectOne(queryWrapper);
        return BeanUtil.convert(orderTrackingDO, OrderTrackingRespDTO.class);
    }

    private final Cache<String, ReentrantLock> localLockMap = Caffeine.newBuilder()
            .expireAfterWrite(1, TimeUnit.DAYS)
            .build();

    private final Cache<String, Object> tokenTicketsRefreshMap = Caffeine.newBuilder()
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .build();

    @Override
    @Transactional(rollbackFor = Throwable.class)
    public TicketPurchaseRespDTO executePurchaseTickets(PurchaseTicketReqDTO requestParam) {
        List<TicketOrderDetailRespDTO> ticketOrderDetailResults = new ArrayList<>();
        String trainId = requestParam.getTrainId();
        TrainDO trainDO = distributedCache.safeGet(
                TRAIN_INFO + trainId,
                TrainDO.class,
                () -> trainMapper.selectById(trainId),
                ADVANCE_TICKET_DAY,
                TimeUnit.DAYS);
        //选取座位 锁定座位
        List<TrainPurchaseTicketRespDTO> trainPurchaseTicketResults = trainSeatTypeSelector.select(trainDO.getTrainType(), requestParam);
        //写入ticketDO
        List<TicketDO> ticketDOList = trainPurchaseTicketResults.stream()
                .map(each -> TicketDO.builder()
                        .username(UserContext.getUsername())
                        .trainId(Long.parseLong(requestParam.getTrainId()))
                        .carriageNumber(each.getCarriageNumber())
                        .seatNumber(each.getSeatNumber())
                        .passengerId(each.getPassengerId())
                        .ticketStatus(TicketStatusEnum.UNPAID.getCode())
                        .build())
                .toList();
        saveBatch(ticketDOList);
        Result<String> ticketOrderResult;
        try {
            List<TicketOrderItemCreateRemoteReqDTO> orderItemCreateRemoteReqDTOList = new ArrayList<>();
            trainPurchaseTicketResults.forEach(each -> {
                TicketOrderItemCreateRemoteReqDTO orderItemCreateRemoteReqDTO = TicketOrderItemCreateRemoteReqDTO.builder()
                        .amount(each.getAmount())
                        .carriageNumber(each.getCarriageNumber())
                        .seatNumber(each.getSeatNumber())
                        .idCard(each.getIdCard())
                        .idType(each.getIdType())
                        .phone(each.getPhone())
                        .seatType(each.getSeatType())
                        .ticketType(each.getUserType())
                        .realName(each.getRealName())
                        .build();
                TicketOrderDetailRespDTO ticketOrderDetailRespDTO = TicketOrderDetailRespDTO.builder()
                        .amount(each.getAmount())
                        .carriageNumber(each.getCarriageNumber())
                        .seatNumber(each.getSeatNumber())
                        .idCard(each.getIdCard())
                        .idType(each.getIdType())
                        .seatType(each.getSeatType())
                        .ticketType(each.getUserType())
                        .realName(each.getRealName())
                        .build();
                orderItemCreateRemoteReqDTOList.add(orderItemCreateRemoteReqDTO);
                ticketOrderDetailResults.add(ticketOrderDetailRespDTO);
            });
            LambdaQueryWrapper<TrainStationRelationDO> queryWrapper = Wrappers.lambdaQuery(TrainStationRelationDO.class)
                    .eq(TrainStationRelationDO::getTrainId, trainId)
                    .eq(TrainStationRelationDO::getDeparture, requestParam.getDeparture())
                    .eq(TrainStationRelationDO::getArrival, requestParam.getArrival());
            TrainStationRelationDO trainStationRelationDO = trainStationRelationMapper.selectOne(queryWrapper);
            TicketOrderCreateRemoteReqDTO orderCreateRemoteReqDTO = TicketOrderCreateRemoteReqDTO.builder()
                    .departure(requestParam.getDeparture())
                    .arrival(requestParam.getArrival())
                    .orderTime(new Date())
                    .source(SourceEnum.INTERNET.getCode())
                    .trainNumber(trainDO.getTrainNumber())
                    .departureTime(trainStationRelationDO.getDepartureTime())
                    .arrivalTime(trainStationRelationDO.getArrivalTime())
                    .ridingDate(trainStationRelationDO.getDepartureTime())
                    .userId(UserContext.getUserId())
                    .username(UserContext.getUsername())
                    .trainId(Long.parseLong(requestParam.getTrainId()))
                    .ticketOrderItems(orderItemCreateRemoteReqDTOList)
                    .build();
            //调用远程服务创建订单
            ticketOrderResult = ticketOrderRemoteService.createTicketOrder(orderCreateRemoteReqDTO);
            if (!ticketOrderResult.isSuccess() || StrUtil.isBlank(ticketOrderResult.getData())) {
                log.error("订单服务调用失败，返回结果：{}", ticketOrderResult.getMessage());
                throw new ServiceException("订单服务调用失败");
            }
        } catch (Throwable ex) {
            log.error("远程调用订单服务创建错误，请求参数：{}", JSON.toJSONString(requestParam), ex);
            throw ex;
        }
        return new TicketPurchaseRespDTO(ticketOrderResult.getData(), ticketOrderDetailResults);
    }

    @Override
    public PayInfoRespDTO getPayInfo(String orderSn) {
        return null;
    }

    @Override
    public void cancelTicketOrder(CancelTicketOrderReqDTO requestParam) {

    }

    @Override
    public RefundTicketRespDTO commonTicketRefund(RefundTicketReqDTO requestParam) {
        return null;
    }

    @Override
    public void run(String... args) throws Exception {
        ticketService = ApplicationContextHolder.getBean(TicketService.class);
    }

    private List<String> buildDepartureStationList(List<TicketListDTO> seatResults) {
        return seatResults.stream().map(TicketListDTO::getDeparture).distinct().collect(Collectors.toList());
    }

    private List<String> buildArrivalStationList(List<TicketListDTO> seatResults) {
        return seatResults.stream().map(TicketListDTO::getArrival).distinct().collect(Collectors.toList());
    }

    private List<Integer> buildSeatClassList(List<TicketListDTO> seatResults) {
        Set<Integer> resultSeatClassList = new HashSet<>();
        for (TicketListDTO each : seatResults) {
            for (SeatClassDTO item : each.getSeatClassList()) {
                resultSeatClassList.add(item.getType());
            }
        }
        return resultSeatClassList.stream().toList();
    }

    private List<Integer> buildTrainBrandList(List<TicketListDTO> seatResults) {
        Set<Integer> trainBrandSet = new HashSet<>();
        for (TicketListDTO each : seatResults) {
            if (StrUtil.isNotBlank(each.getTrainBrand())) {
                trainBrandSet.addAll(StrUtil.split(each.getTrainBrand(), ",").stream().map(Integer::parseInt).toList());
            }
        }
        return trainBrandSet.stream().toList();
    }

    private final ScheduledExecutorService tokenIsNullRefreshExecutor = Executors.newScheduledThreadPool(1);


    private void tokenIsNullRefreshToken(PurchaseTicketReqDTO requestParam, TokenResultDTO tokenResult) {
        RLock lock = redissonClient.getLock(String.format(LOCK_TOKEN_BUCKET_ISNULL, requestParam.getTrainId()));
        if (!lock.tryLock()) {
            return;
        }
        tokenIsNullRefreshExecutor.schedule(() -> {
            try {
                List<Integer> seatTypes = new ArrayList<>();
                Map<Integer, Integer> tokenCountMap = new HashMap<>();
                //通过result 得到用户所需的票数
                tokenResult.getTokenIsNullSeatTypeCounts().stream()
                        .map(each -> each.split("_"))
                        .forEach(each -> {
                            Integer seatType = Integer.valueOf(each[0]);
                            seatTypes.add(seatType);
                            tokenCountMap.put(seatType, Integer.valueOf(each[1]));
                        });
                //查询数据库是否真的卖完
                List<SeatTypeCountDTO> seatTypeCountDTOS = seatService.listSeatTypeCount(Long.parseLong(requestParam.getTrainId()), requestParam.getDeparture(), requestParam.getArrival(), seatTypes);
                for (SeatTypeCountDTO seatTypeCountDTO : seatTypeCountDTOS) {
                    if (tokenCountMap.get(seatTypeCountDTO.getSeatType()) <= seatTypeCountDTO.getSeatCount()) {
                        //如果卖完删除tokenBucket
                        ticketAvailabilityTokenBucket.delTokenInBucket(requestParam);
                        break;
                    }
                }
            } finally {
                lock.unlock();
            }
        }, 10, TimeUnit.SECONDS);
    }


}