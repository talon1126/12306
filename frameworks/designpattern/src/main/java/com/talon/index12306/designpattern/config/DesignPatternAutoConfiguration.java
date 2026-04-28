package com.talon.index12306.designpattern.config;

import com.talon.index12306.designpattern.chain.AbstractChainContext;
import com.talon.index12306.designpattern.strategy.AbstractStrategyChoose;
import org.springframework.context.annotation.Bean;

public class DesignPatternAutoConfiguration {

    /**
     * 策略模式选择器
     */
    @Bean
    public AbstractStrategyChoose abstractStrategyChoose() {
        return new AbstractStrategyChoose();
    }


    /**
     * 责任链上下文
     */
    @Bean
    public AbstractChainContext abstractChainContext() {
        return new AbstractChainContext();
    }
}
