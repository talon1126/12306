package com.talon.index12306.ticketservice.service.handler.ticket.filter.purchase;

import cn.hutool.core.util.StrUtil;
import com.talon.index12306.cache.DistributedCache;
import com.talon.index12306.convention.exception.ClientException;
import com.talon.index12306.ticketservice.dto.domain.PurchaseTicketPassengerDetailDTO;
import com.talon.index12306.ticketservice.dto.req.PurchaseTicketReqDTO;
import com.talon.index12306.ticketservice.service.cache.SeatMarginCacheLoader;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.talon.index12306.ticketservice.common.constant.RedisKeyConstant.TRAIN_STATION_REMAINING_TICKET;


/**
 * 购票流程过滤器之验证列车站点库存是否充足
 * 公众号：马丁玩编程，回复：加群，添加马哥微信（备注：12306）获取项目资料
 */
@Component
@RequiredArgsConstructor
public class TrainPurchaseTicketParamStockChainHandler implements TrainPurchaseTicketChainFilter<PurchaseTicketReqDTO> {

    private final SeatMarginCacheLoader seatMarginCacheLoader;
    private final DistributedCache distributedCache;

    @Override
    public void handler(PurchaseTicketReqDTO requestParam) {
        // 车次站点是否还有余票。如果用户提交多个乘车人非同一座位类型，拆分验证
        String keySuffix = StrUtil.join("_", requestParam.getTrainId(), requestParam.getDeparture(), requestParam.getArrival());
        StringRedisTemplate stringRedisTemplate = (StringRedisTemplate) distributedCache.getInstance();
        Map<Integer, List<PurchaseTicketPassengerDetailDTO>> map = requestParam.getPassengers().stream().collect(Collectors.groupingBy(PurchaseTicketPassengerDetailDTO::getSeatType));
        map.forEach((seatType, list) -> {
            //从缓存中获取余票数据
            Object stockObj = stringRedisTemplate.opsForHash().get(TRAIN_STATION_REMAINING_TICKET + keySuffix, seatType.toString());
            int stock = Optional.ofNullable(stockObj).map(item -> Integer.parseInt(item.toString())).orElseGet(() -> {
                Map<String, String> load = seatMarginCacheLoader.load(requestParam.getTrainId(), seatType.toString(), requestParam.getDeparture(), requestParam.getArrival());
                return Optional.ofNullable(load.get(seatType.toString())).map(Integer::parseInt).orElse(0);
            });
            if (stock < list.size()){
                throw new ClientException("列车站点已无余票");
            }
        });

    }

    @Override
    public int getOrder() {
        return 20;
    }
}
