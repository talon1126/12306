package com.talon.index12306.idempotent.core.token;

import com.talon.index12306.cache.DistributedCache;
import com.talon.index12306.idempotent.config.IdempotentProperties;
import com.talon.index12306.idempotent.core.AbstractIdempotentExecuteHandler;
import com.talon.index12306.idempotent.core.IdempotentParamWrapper;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;

/**
 *   基于 Token 验证请求幂等性, 通常应用于 RestAPI 方法
 */
@RequiredArgsConstructor
public final class IdempotentTokenExecuteHandler extends AbstractIdempotentExecuteHandler implements IdempotentTokenService {

    private final DistributedCache distributedCache;
    private final IdempotentProperties idempotentProperties;

    @Override
    protected IdempotentParamWrapper buildWrapper(ProceedingJoinPoint joinPoint) {
        return null;
    }

    @Override
    public String createToken() {
        return "";
    }

    @Override
    public void handler(IdempotentParamWrapper idempotentParamWrapper) {

    }
}
