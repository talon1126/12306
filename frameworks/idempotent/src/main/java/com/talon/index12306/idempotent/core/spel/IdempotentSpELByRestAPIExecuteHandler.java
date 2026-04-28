package com.talon.index12306.idempotent.core.spel;

import com.talon.index12306.convention.exception.ClientException;
import com.talon.index12306.idempotent.annotation.Idempotent;
import com.talon.index12306.idempotent.core.AbstractIdempotentExecuteHandler;
import com.talon.index12306.idempotent.core.IdempotentAspect;
import com.talon.index12306.idempotent.core.IdempotentContext;
import com.talon.index12306.idempotent.core.IdempotentParamWrapper;
import com.talon.index12306.idempotent.util.SpELUtil;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

/**
 * 基于 SpEL 方法验证请求幂等性，适用于 RestAPI 场景
 * 公众号：马丁玩编程，回复：加群，添加马哥微信（备注：12306）获取项目资料
 */
@RequiredArgsConstructor
public final class IdempotentSpELByRestAPIExecuteHandler extends AbstractIdempotentExecuteHandler implements IdempotentSpELService {

    private final RedissonClient redissonClient;
    private final static String LOCK = "lock:spEL:restAPI";

    @SneakyThrows
    @Override
    protected IdempotentParamWrapper buildWrapper(ProceedingJoinPoint joinPoint) {
        Idempotent idempotent = IdempotentAspect.getIdempotent(joinPoint);
        String key = (String) SpELUtil.parseKey(idempotent.key(), ((MethodSignature) joinPoint.getSignature()).getMethod(), joinPoint.getArgs());
        return new IdempotentParamWrapper().setLockKey(key);

    }

    @Override
    public void handler(IdempotentParamWrapper wrapper) {
        //加分布式锁
        RLock lock = redissonClient.getLock(wrapper.getIdempotent().uniqueKeyPrefix() + wrapper.getLockKey());
        if (!lock.tryLock()) {
            throw new ClientException(wrapper.getIdempotent().message());
        }
        IdempotentContext.put(LOCK, lock);
    }

    @Override
    public void postProcessing() {
        //解锁
        RLock lock = null;
        try {
            lock = (RLock) IdempotentContext.getKey(LOCK);
        } finally {
            if (lock != null) {
                lock.unlock();
            }
            IdempotentContext.clean();
        }
    }

    @Override
    public void exceptionProcessing() {
        postProcessing();
    }
}