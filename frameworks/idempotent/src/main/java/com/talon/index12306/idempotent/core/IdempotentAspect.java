package com.talon.index12306.idempotent.core;

import com.talon.index12306.idempotent.annotation.Idempotent;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;

import java.lang.reflect.Method;

/**
 * 幂等注解 AOP 拦截器
 * 公众号：马丁玩编程，回复：加群，添加马哥微信（备注：12306）获取项目资料
 */
@Aspect
public final class IdempotentAspect {

    @Around("@annotation(com.talon.index12306.idempotent.annotation.Idempotent)")
    public Object idempotentHandler(ProceedingJoinPoint joinPoint) throws Throwable {
        //注解
        Idempotent idempotent = getIdempotent(joinPoint);
        //获取实例
        IdempotentExecuteHandler instance = IdempotentExecuteHandlerFactory.getInstance(idempotent.scene(), idempotent.type());
        Object res;
        try {
            //AOP
            instance.execute(joinPoint, idempotent);
            //执行方法
            res = joinPoint.proceed();
            //后置处理
            instance.postProcessing();
        } catch (RepeatConsumptionException e) {
            //
            //触发幂等逻辑时可能有两种情况：
            //   * 1. 消息还在处理，但是不确定是否执行成功，那么需要返回错误，方便 RocketMQ 再次通过重试队列投递
            //   * 2. 消息处理成功了，该消息直接返回成功即可
            //
            //error==null
            if (!e.getError()) {
                return null;
            }
            throw e;
        } catch (Throwable ex) {
            //业务出现异常
            instance.exceptionProcessing();
            throw ex;
        } finally {
            IdempotentContext.clean();
        }
        return res;
    }

    public static Idempotent getIdempotent(ProceedingJoinPoint joinPoint) throws NoSuchMethodException {
        //方法签名 返回值 方法名 参数
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        //方法                     实现类(并非代理)
        Method method = joinPoint.getTarget().getClass().getDeclaredMethod(signature.getName(), signature.getParameterTypes());
        return method.getAnnotation(Idempotent.class);
    }
}