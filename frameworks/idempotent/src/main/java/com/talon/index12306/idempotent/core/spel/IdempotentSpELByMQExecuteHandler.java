package com.talon.index12306.idempotent.core.spel;

import com.talon.index12306.cache.DistributedCache;
import com.talon.index12306.idempotent.annotation.Idempotent;
import com.talon.index12306.idempotent.core.*;
import com.talon.index12306.idempotent.enums.IdempotentMQConsumeStatusEnum;
import com.talon.index12306.idempotent.util.LogUtil;
import com.talon.index12306.idempotent.util.SpELUtil;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 基于 SpEL 方法验证请求幂等性，适用于 MQ 场景
 * 公众号：马丁玩编程，回复：加群，添加马哥微信（备注：12306）获取项目资料
 */
@RequiredArgsConstructor
public final class IdempotentSpELByMQExecuteHandler extends AbstractIdempotentExecuteHandler implements IdempotentSpELService {


    private final static int TIMEOUT = 600;

    private final static String WRAPPER = "wrapper:spEL:MQ";

    private final static String LUA_SCRIPT_SET_IF_ABSENT_AND_GET_PATH = "lua/set_if_absent_and_get.lua";

    private final DistributedCache distributedCache;

    @SneakyThrows
    @Override
    protected IdempotentParamWrapper buildWrapper(ProceedingJoinPoint joinPoint) {
        Idempotent idempotent = IdempotentAspect.getIdempotent(joinPoint);
        return IdempotentParamWrapper.builder()
                .joinPoint(joinPoint)
                .lockKey((String) SpELUtil.parse(idempotent.key(), ((MethodSignature) joinPoint.getSignature()).getMethod(), joinPoint.getArgs()))
                .build();
    }

    @Override
    public void handler(IdempotentParamWrapper wrapper) {
        String key = wrapper.getIdempotent().uniqueKeyPrefix() + wrapper.getLockKey();
        //判断状态
        String setIfAbsentAndGet = setIfAbsentAndGet(key, IdempotentMQConsumeStatusEnum.CONSUMING.getCode(), TIMEOUT, TimeUnit.SECONDS);
        if (setIfAbsentAndGet != null) {
            boolean error = IdempotentMQConsumeStatusEnum.isError(setIfAbsentAndGet);
            LogUtil.getLog(wrapper.getJoinPoint()).warn("[{}] MQ repeated consumption, {}.", key, error ? "Wait for the client to delay consumption" : "Status is completed");
            throw new RepeatConsumptionException(error);
        }
        IdempotentContext.put(WRAPPER, wrapper);
    }

    public String setIfAbsentAndGet(String key, String value, long timeout, TimeUnit timeUnit) {
        //执行lua脚本状态设置为consuming
        DefaultRedisScript<String> defaultRedisScript = new DefaultRedisScript<>();
        defaultRedisScript.setScriptSource(new ResourceScriptSource(new ClassPathResource(LUA_SCRIPT_SET_IF_ABSENT_AND_GET_PATH)));
        defaultRedisScript.setResultType(String.class);
        return ((StringRedisTemplate) distributedCache.getInstance()).execute(defaultRedisScript, List.of(key), value, String.valueOf(timeUnit.toMillis(timeout)));
    }


    @Override
    public void postProcessing() {
        IdempotentParamWrapper wrapper = (IdempotentParamWrapper) IdempotentContext.getKey(WRAPPER);
        //状态设置为consumed
        if (wrapper != null) {
            Idempotent idempotent = wrapper.getIdempotent();
            String key = idempotent.uniqueKeyPrefix() + wrapper.getLockKey();
            try {
                distributedCache.put(key, IdempotentMQConsumeStatusEnum.CONSUMED.getCode(), idempotent.keyTimeout(), TimeUnit.SECONDS);
            } catch (Throwable ex) {
                LogUtil.getLog(wrapper.getJoinPoint()).error("[{}] Failed to set MQ anti-heavy token.", key);
            }
        }
    }

    @Override
    public void exceptionProcessing() {
        //删除key
        IdempotentParamWrapper wrapper = (IdempotentParamWrapper) IdempotentContext.getKey(WRAPPER);
        if (wrapper != null) {
            Idempotent idempotent = wrapper.getIdempotent();
            String uniqueKey = idempotent.uniqueKeyPrefix() + wrapper.getLockKey();
            try {
                distributedCache.delete(uniqueKey);
            } catch (Throwable ex) {
                LogUtil.getLog(wrapper.getJoinPoint()).error("[{}] Failed to del MQ anti-heavy token.", uniqueKey);
            }
        }
    }

}
