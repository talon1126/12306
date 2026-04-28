package com.talon.index12306.ticketservice.service.impl;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.talon.index12306.cache.DistributedCache;
import com.talon.index12306.cache.core.CacheLoader;
import com.talon.index12306.common.enums.FlagEnum;
import com.talon.index12306.convention.exception.ClientException;
import com.talon.index12306.ticketservice.common.enums.RegionStationQueryTypeEnum;
import com.talon.index12306.ticketservice.dao.entity.RegionDO;
import com.talon.index12306.ticketservice.dao.entity.StationDO;
import com.talon.index12306.ticketservice.dao.mapper.RegionMapper;
import com.talon.index12306.ticketservice.dao.mapper.StationMapper;
import com.talon.index12306.ticketservice.dto.req.RegionStationQueryReqDTO;
import com.talon.index12306.ticketservice.dto.resp.RegionStationQueryRespDTO;
import com.talon.index12306.ticketservice.dto.resp.StationQueryRespDTO;
import com.talon.index12306.ticketservice.mapstruct.TicketConverter;
import com.talon.index12306.ticketservice.service.RegionStationService;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.talon.index12306.ticketservice.common.constant.Index12306Constant.ADVANCE_TICKET_DAY;
import static com.talon.index12306.ticketservice.common.constant.RedisKeyConstant.REGION_STATION;
import static com.talon.index12306.ticketservice.common.constant.RedisKeyConstant.STATION_ALL;

/**
 * 地区以及车站接口实现层
 * 公众号：马丁玩编程，回复：加群，添加马哥微信（备注：12306）获取项目资料
 */
@Service
@RequiredArgsConstructor
public class RegionStationImpl implements RegionStationService {

    private final RegionMapper regionMapper;
    private final StationMapper stationMapper;
    private final DistributedCache distributedCache;
    private final RedissonClient redissonClient;
    private final TicketConverter ticketConverter;

    @Override
    public List<RegionStationQueryRespDTO> listRegionStation(RegionStationQueryReqDTO requestParam) {
        String key;
        if (StringUtils.hasText(requestParam.getName())) {
            key = REGION_STATION + requestParam.getName();
            return safeGetRegionStation(
                    key,
                    () -> JSON.toJSONString(ticketConverter.stationDOs2RSQueryRespDTOs(stationMapper.selectList(Wrappers.lambdaQuery(StationDO.class)
                            .likeRight(StationDO::getName, requestParam.getName())
                            .or()
                            .likeRight(StationDO::getSpell, requestParam.getName())
                    )))
                    ,
                    requestParam.getName()
            );
        }
        key = REGION_STATION + requestParam.getQueryType();
        return safeGetRegionStation(
                key,
                () -> JSON.toJSONString(ticketConverter.RegionDOs2RSQueryRespDTOs(regionMapper.selectList(switch (requestParam.getQueryType()) {
                    case 0 -> Wrappers.lambdaQuery(RegionDO.class)
                            .eq(RegionDO::getPopularFlag, FlagEnum.TRUE.code());
                    case 1 -> Wrappers.lambdaQuery(RegionDO.class)
                            .in(RegionDO::getInitial, RegionStationQueryTypeEnum.A_E.getSpells());
                    case 2 -> Wrappers.lambdaQuery(RegionDO.class)
                            .in(RegionDO::getInitial, RegionStationQueryTypeEnum.F_J.getSpells());
                    case 3 -> Wrappers.lambdaQuery(RegionDO.class)
                            .in(RegionDO::getInitial, RegionStationQueryTypeEnum.K_O.getSpells());
                    case 4 -> Wrappers.lambdaQuery(RegionDO.class)
                            .in(RegionDO::getInitial, RegionStationQueryTypeEnum.P_T.getSpells());
                    case 5 -> Wrappers.lambdaQuery(RegionDO.class)
                            .in(RegionDO::getInitial, RegionStationQueryTypeEnum.U_Z.getSpells());
                    default -> throw new ClientException("查询失败，请检查查询参数是否正确");
                }))),
                String.valueOf(requestParam.getQueryType()));

    }

    @Override
    public List<StationQueryRespDTO> listAllStation() {
        return distributedCache.safeGet(
                STATION_ALL,
                List.class,
                () -> ticketConverter.stationDOs2SQueryRespDTOs(stationMapper.selectList(Wrappers.emptyWrapper())),
                ADVANCE_TICKET_DAY,
                TimeUnit.DAYS
        );
    }

    private List<RegionStationQueryRespDTO> safeGetRegionStation(final String key, CacheLoader<String> loader, String param) {
        List<RegionStationQueryRespDTO> res;
        //缓存不为空
        if (!CollectionUtils.isEmpty(res = JSON.parseArray(distributedCache.get(key, String.class), RegionStationQueryRespDTO.class))) {
            return res;
        }
        RLock lock = redissonClient.getLock(StrUtil.format(key, param));
        lock.lock();
        try {
            //dcl
            //缓存还是为空
            if (CollectionUtils.isEmpty(res = JSON.parseArray(distributedCache.get(key, String.class), RegionStationQueryRespDTO.class))) {
                if (CollectionUtils.isEmpty(res = loadAndSet(key, loader))) {
                    //数据库也不存在
                    return Collections.emptyList();
                }
            }
        } finally {
            lock.unlock();
        }
        return res;
    }

    private List<RegionStationQueryRespDTO> loadAndSet(final String key, CacheLoader<String> loader) {
        String json = loader.load();
        List<RegionStationQueryRespDTO> res = null;
        //存在返回 写入缓存
        if (StringUtils.hasText(json)) {
            if (!CollectionUtils.isEmpty(res = JSON.parseArray(json, RegionStationQueryRespDTO.class))) {
                distributedCache.put(key, res, ADVANCE_TICKET_DAY, TimeUnit.DAYS);
            }
        }
        return res;
    }


}