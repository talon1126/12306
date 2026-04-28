package com.talon.index12306.ticketservice.service.handler.ticket.tokenbucket;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.google.common.collect.Lists;
import com.talon.index12306.base.Singleton;
import com.talon.index12306.cache.DistributedCache;
import com.talon.index12306.cache.util.CacheUtil;
import com.talon.index12306.convention.exception.ServiceException;
import com.talon.index12306.ticketservice.common.enums.VehicleTypeEnum;
import com.talon.index12306.ticketservice.dao.entity.TrainDO;
import com.talon.index12306.ticketservice.dao.mapper.TrainMapper;
import com.talon.index12306.ticketservice.dto.domain.PurchaseTicketPassengerDetailDTO;
import com.talon.index12306.ticketservice.dto.domain.RouteDTO;
import com.talon.index12306.ticketservice.dto.domain.SeatTypeCountDTO;
import com.talon.index12306.ticketservice.dto.req.PurchaseTicketReqDTO;
import com.talon.index12306.ticketservice.remote.dto.TicketOrderDetailRespDTO;
import com.talon.index12306.ticketservice.service.SeatService;
import com.talon.index12306.ticketservice.service.TrainStationService;
import com.talon.index12306.ticketservice.service.handler.ticket.dto.TokenResultDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.talon.index12306.ticketservice.common.constant.Index12306Constant.ADVANCE_TICKET_DAY;
import static com.talon.index12306.ticketservice.common.constant.RedisKeyConstant.*;

/**
 * 列车车票余量令牌桶，应对海量并发场景下满足并行、限流以及防超卖等场景
 * 公众号：马丁玩编程，回复：加群，添加马哥微信（备注：12306）获取项目资料
 */
@Slf4j
@Component
@RequiredArgsConstructor
public final class TicketAvailabilityTokenBucket {

    private final TrainStationService trainStationService;
    private final DistributedCache distributedCache;
    private final RedissonClient redissonClient;
    private final SeatService seatService;
    private final TrainMapper trainMapper;

    private static final String LUA_TICKET_AVAILABILITY_TOKEN_BUCKET_PATH = "lua/ticket_availability_token_bucket.lua";
    private static final String LUA_TICKET_AVAILABILITY_ROLLBACK_TOKEN_BUCKET_PATH = "lua/ticket_availability_rollback_token_bucket.lua";

    /**
     * 获取车站间令牌桶中的令牌访问
     * 如果返回 {@link Boolean#TRUE} 代表可以参与接下来的购票下单流程
     * 如果返回 {@link Boolean#FALSE} 代表当前访问出发站点和到达站点令牌已被拿完，无法参与购票下单等逻辑
     *
     * @param requestParam 购票请求参数入参
     * @return 是否获取列车车票余量令牌桶中的令牌返回结果
     */
    public TokenResultDTO takeTokenFromBucket(PurchaseTicketReqDTO requestParam) {
        TrainDO trainDO = distributedCache.safeGet(
                TRAIN_INFO + requestParam.getTrainId(),
                TrainDO.class,
                () -> trainMapper.selectById(requestParam.getTrainId()),
                ADVANCE_TICKET_DAY,
                TimeUnit.DAYS);
        //列车路线
        List<RouteDTO> routeDTOS = trainStationService.listTrainStationRoute(requestParam.getTrainId(), trainDO.getStartStation(), trainDO.getEndStation());
        StringRedisTemplate stringRedisTemplate = (StringRedisTemplate) distributedCache.getInstance();
        String tokenBucketHashKey = TICKET_AVAILABILITY_TOKEN_BUCKET + requestParam.getTrainId();
        //令牌桶不存在
        if (!distributedCache.hasKey(tokenBucketHashKey)) {
            RLock lock = redissonClient.getLock(String.format(LOCK_TICKET_AVAILABILITY_TOKEN_BUCKET, requestParam.getTrainId()));
            if (!lock.tryLock()) {
                throw new ServiceException("购票异常，请稍候再试");
            }
            try {
                //dcl
                if (!distributedCache.hasKey(tokenBucketHashKey)) {
                    //重建缓存
                    //             key      TICKET_AVAILABILITY_TOKEN_BUCKET + requestParam.getTrainId()
                    //列车购买令牌桶
                    //                      key    StartStation_EndStation_SeatType
                    //             value
                    //                      value  SeatCount
                    List<Integer> seatTypesByCode = VehicleTypeEnum.findSeatTypesByCode(trainDO.getTrainType());
                    HashMap<String, Integer> tokenBuckethMap = new HashMap<>();
                    //每种座位类型的余票
                    List<SeatTypeCountDTO> seatTypeCountDTOS = seatService.listSeatTypeCount(trainDO.getId(), requestParam.getDeparture(), requestParam.getArrival(), seatTypesByCode);
                    for (RouteDTO routeDTO : routeDTOS) {
                        for (SeatTypeCountDTO dto : seatTypeCountDTOS) {
                            tokenBuckethMap.put(CacheUtil.buildKey(routeDTO.getStartStation(), routeDTO.getEndStation(), dto.getSeatType().toString()), dto.getSeatCount());
                        }
                    }
                    stringRedisTemplate.opsForHash().putAll(tokenBucketHashKey, tokenBuckethMap);
                }
            } finally {
                lock.unlock();
            }
        }
        DefaultRedisScript<String> actual = Singleton.get(LUA_TICKET_AVAILABILITY_TOKEN_BUCKET_PATH, () -> {
            DefaultRedisScript<String> redisScript = new DefaultRedisScript<>();
            redisScript.setScriptSource(new ResourceScriptSource(new ClassPathResource(LUA_TICKET_AVAILABILITY_TOKEN_BUCKET_PATH)));
            redisScript.setResultType(String.class);
            return redisScript;
        });
        //查看余票是否充足
        Map<Integer, Long> collect = requestParam.getPassengers().stream().collect(Collectors.groupingBy(PurchaseTicketPassengerDetailDTO::getSeatType, Collectors.counting()));
        JSONArray seatTypeCountArray = collect.entrySet().stream().map((entry) -> {
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("seatType", String.valueOf(entry.getKey()));
            jsonObject.put("count", String.valueOf(entry.getValue()));
            return jsonObject;
        }).collect(Collectors.toCollection(JSONArray::new));
        List<RouteDTO> takeoutRouteDTOList = trainStationService
                .listTakeoutTrainStationRoute(requestParam.getTrainId(), requestParam.getDeparture(), requestParam.getArrival());
        String luaScriptKey = StrUtil.join("_", requestParam.getDeparture(), requestParam.getArrival());
        String resultStr = stringRedisTemplate.execute(actual, Lists.newArrayList(tokenBucketHashKey, luaScriptKey), JSON.toJSONString(seatTypeCountArray), JSON.toJSONString(takeoutRouteDTOList));
        TokenResultDTO result = JSON.parseObject(resultStr, TokenResultDTO.class);
        return result == null
                ? TokenResultDTO.builder().tokenIsNull(Boolean.TRUE).build()
                : result;

    }

    /**
     * 回滚列车余量令牌，一般为订单取消或长时间未支付触发
     *
     * @param requestParam 回滚列车余量令牌入参
     */
    public void rollbackInBucket(TicketOrderDetailRespDTO requestParam) {

    }

    /**
     * 删除令牌，一般在令牌与数据库不一致情况下触发
     *
     * @param requestParam 删除令牌容器参数
     */
    public void delTokenInBucket(PurchaseTicketReqDTO requestParam) {
        StringRedisTemplate stringRedisTemplate = (StringRedisTemplate) distributedCache.getInstance();
        String tokenBucketHashKey = TICKET_AVAILABILITY_TOKEN_BUCKET + requestParam.getTrainId();
        stringRedisTemplate.delete(tokenBucketHashKey);
    }
}