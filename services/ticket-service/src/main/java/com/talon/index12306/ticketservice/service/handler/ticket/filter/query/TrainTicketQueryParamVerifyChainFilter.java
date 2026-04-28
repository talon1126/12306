package com.talon.index12306.ticketservice.service.handler.ticket.filter.query;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.google.common.collect.Maps;
import com.talon.index12306.cache.DistributedCache;
import com.talon.index12306.convention.exception.ClientException;
import com.talon.index12306.ticketservice.dao.entity.RegionDO;
import com.talon.index12306.ticketservice.dao.entity.StationDO;
import com.talon.index12306.ticketservice.dao.mapper.RegionMapper;
import com.talon.index12306.ticketservice.dao.mapper.StationMapper;
import com.talon.index12306.ticketservice.dto.req.TicketPageQueryReqDTO;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Objects;

import static com.talon.index12306.ticketservice.common.constant.RedisKeyConstant.LOCK_QUERY_ALL_REGION_LIST;
import static com.talon.index12306.ticketservice.common.constant.RedisKeyConstant.QUERY_ALL_REGION_LIST;

/**
 * 查询列车车票流程过滤器之验证数据是否正确
 * 公众号：马丁玩编程，回复：加群，添加马哥微信（备注：12306）获取项目资料
 */
@Component
@RequiredArgsConstructor
public class TrainTicketQueryParamVerifyChainFilter implements TrainTicketQueryChainFilter<TicketPageQueryReqDTO> {

    private final RegionMapper regionMapper;
    private final StationMapper stationMapper;
    private final DistributedCache distributedCache;
    private final RedissonClient redissonClient;

    /**
     * 缓存数据为空并且已经加载过标识
     */
    private static boolean CACHE_DATA_ISNULL_AND_LOAD_FLAG = false;

    @Override
    public void handler(TicketPageQueryReqDTO requestParam) {
        StringRedisTemplate stringRedisTemplate = (StringRedisTemplate) distributedCache.getInstance();
        HashOperations<String, Object, Object> hashOperations = stringRedisTemplate.opsForHash();
        //获取缓存
        List<Object> objects = hashOperations.multiGet(QUERY_ALL_REGION_LIST, List.of(requestParam.getFromStation(), requestParam.getToStation()));
        long count = objects.stream().filter(Objects::isNull).count();
        if (count == 0) {
            return;
        }
        if (count == 1 || (count == 2 && CACHE_DATA_ISNULL_AND_LOAD_FLAG && stringRedisTemplate.hasKey(QUERY_ALL_REGION_LIST))) {
            throw new ClientException("出发地或目的地不存在");
        }
        //缓存不完整 尝试重新构建
        RLock lock = redissonClient.getLock(LOCK_QUERY_ALL_REGION_LIST);
        lock.lock();
        try {
            //缓存存在
            if (distributedCache.hasKey(QUERY_ALL_REGION_LIST)) {
                objects = hashOperations.multiGet(QUERY_ALL_REGION_LIST, List.of(requestParam.getFromStation(), requestParam.getToStation()));
                count = objects.stream().filter(Objects::nonNull).count();
                if (count != 2) {
                    throw new ClientException("出发地或目的地不存在");
                }
                return;
            }
            //缓存不存在 构建缓存
            List<RegionDO> regionDOList = regionMapper.selectList(Wrappers.emptyWrapper());
            List<StationDO> stationDOList = stationMapper.selectList(Wrappers.emptyWrapper());
            HashMap<Object, Object> regionValueMap = Maps.newHashMap();
            for (RegionDO each : regionDOList) {
                regionValueMap.put(each.getCode(), each.getName());
            }
            for (StationDO each : stationDOList) {
                regionValueMap.put(each.getCode(), each.getName());
            }
            hashOperations.putAll(QUERY_ALL_REGION_LIST, regionValueMap);
            CACHE_DATA_ISNULL_AND_LOAD_FLAG = true;
            //校验入参 起始站和终点站是否在缓存中存在
            if (regionValueMap.keySet().stream()
                    .filter(each -> StrUtil.equalsAny(each.toString(), requestParam.getFromStation(), requestParam.getToStation()))
                    .count() != 2) {
                throw new ClientException("出发地或目的地不存在");
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public int getOrder() {
        return 20;
    }
}