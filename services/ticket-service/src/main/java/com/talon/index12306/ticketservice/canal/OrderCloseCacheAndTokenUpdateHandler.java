package com.talon.index12306.ticketservice.canal;

import com.talon.index12306.designpattern.strategy.AbstractExecuteStrategy;
import com.talon.index12306.ticketservice.common.enums.CanalExecuteStrategyMarkEnum;
import com.talon.index12306.ticketservice.mq.event.CanalBinlogEvent;
import com.talon.index12306.ticketservice.remote.TicketOrderRemoteService;
import com.talon.index12306.ticketservice.service.SeatService;
import com.talon.index12306.ticketservice.service.handler.ticket.tokenbucket.TicketAvailabilityTokenBucket;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 订单关闭或取消后置处理组件
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderCloseCacheAndTokenUpdateHandler implements AbstractExecuteStrategy<CanalBinlogEvent, Void> {

    private final TicketOrderRemoteService ticketOrderRemoteService;
    private final SeatService seatService;
    private final TicketAvailabilityTokenBucket ticketAvailabilityTokenBucket;

    @Override
    public void execute(CanalBinlogEvent message) {

    }

    @Override
    public String mark() {
        return CanalExecuteStrategyMarkEnum.T_ORDER.getActualTable();
    }

    @Override
    public String patternMatchMark() {
        return CanalExecuteStrategyMarkEnum.T_ORDER.getPatternMatchTable();
    }
}
