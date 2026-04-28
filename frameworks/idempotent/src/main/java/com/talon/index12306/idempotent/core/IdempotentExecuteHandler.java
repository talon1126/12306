package com.talon.index12306.idempotent.core;

import com.talon.index12306.idempotent.annotation.Idempotent;
import org.aspectj.lang.ProceedingJoinPoint;

/**
 * 幂等执行处理器
 */
public interface IdempotentExecuteHandler {

    /**
     * 幂等处理逻辑
     */
    void handler(IdempotentParamWrapper idempotentParamWrapper);

    /**
     * 执行幂等处理逻辑
     */
    void execute(ProceedingJoinPoint joinPoint, Idempotent idempotent);

    /**
     * 后置处理
     */
    default void postProcessing() {

    }

    /**
     * 异常处理
     */
    default void exceptionProcessing() {

    }
}