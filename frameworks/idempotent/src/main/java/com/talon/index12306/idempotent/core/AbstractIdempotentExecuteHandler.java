package com.talon.index12306.idempotent.core;

import com.talon.index12306.idempotent.annotation.Idempotent;
import org.aspectj.lang.ProceedingJoinPoint;

/**
 * 抽象幂等执行处理器
 */
public abstract class AbstractIdempotentExecuteHandler implements IdempotentExecuteHandler {

    /**
     * 构建幂等验证过程中所需要的参数包装器
     * @param joinPoint AOP 方法处理
     * @return 幂等参数包装器
     */
    protected abstract IdempotentParamWrapper buildWrapper(ProceedingJoinPoint joinPoint);

    /**
     * 执行幂等处理逻辑
     *
     * @param joinPoint  AOP 方法处理
     * @param idempotent 幂等注解
     */
    @Override
    public void execute(ProceedingJoinPoint joinPoint, Idempotent idempotent) {
        handler(buildWrapper(joinPoint).setIdempotent(idempotent));
    }
}