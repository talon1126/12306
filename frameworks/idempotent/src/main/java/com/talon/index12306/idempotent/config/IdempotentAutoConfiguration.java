package com.talon.index12306.idempotent.config;

import com.talon.index12306.cache.DistributedCache;
import com.talon.index12306.idempotent.core.IdempotentAspect;
import com.talon.index12306.idempotent.core.param.IdempotentParamExecuteHandler;
import com.talon.index12306.idempotent.core.spel.IdempotentSpELByMQExecuteHandler;
import com.talon.index12306.idempotent.core.spel.IdempotentSpELByRestAPIExecuteHandler;
import com.talon.index12306.idempotent.core.token.IdempotentTokenController;
import com.talon.index12306.idempotent.core.token.IdempotentTokenExecuteHandler;
import com.talon.index12306.idempotent.core.token.IdempotentTokenService;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 幂等自动装配
 */
@EnableConfigurationProperties(IdempotentProperties.class)
public class IdempotentAutoConfiguration {
    /**
     * 幂等切面
     */
    @Bean
    public IdempotentAspect idempotentAspect() {
        return new IdempotentAspect();
    }

    /**
     *SpEL 方式幂等实现，基于 RestAPI 场景
     */
    @Bean
    public IdempotentSpELByRestAPIExecuteHandler idempotentSpELByRestAPIExecuteHandler(RedissonClient redissonClient){
        return new IdempotentSpELByRestAPIExecuteHandler(redissonClient);
    }

    /**
     *参数方式幂等实现，基于 RestAPI 场景
     */
    @Bean
    public IdempotentParamExecuteHandler idempotentParamExecuteHandler(RedissonClient redissonClient){
        return new IdempotentParamExecuteHandler(redissonClient);
    }

    /**
     * Token 方式幂等实现，基于 RestAPI 场景
     */
    @Bean
    @ConditionalOnMissingBean
    public IdempotentTokenService idempotentTokenExecuteHandler(DistributedCache distributedCache,
                                                                IdempotentProperties idempotentProperties) {
        return new IdempotentTokenExecuteHandler(distributedCache, idempotentProperties);
    }

    /**
     * 申请幂等 Token 控制器，基于 RestAPI 场景
     */
    @Bean
    public IdempotentTokenController idempotentTokenController(IdempotentTokenService idempotentTokenService) {
        return new IdempotentTokenController(idempotentTokenService);
    }

    /**
     * SpEL 方式幂等实现，基于 MQ 场景
     */
    @Bean
    @ConditionalOnMissingBean
    public IdempotentSpELByMQExecuteHandler idempotentSpELByMQExecuteHandler(DistributedCache distributedCache) {
        return new IdempotentSpELByMQExecuteHandler(distributedCache);
    }

}