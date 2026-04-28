package com.talon.index12306.idempotent.core.param;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.alibaba.fastjson2.JSON;
import com.talon.index12306.convention.exception.ClientException;
import com.talon.index12306.framework.user.core.UserContext;
import com.talon.index12306.idempotent.core.AbstractIdempotentExecuteHandler;
import com.talon.index12306.idempotent.core.IdempotentParamWrapper;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.redisson.api.RedissonClient;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 基于方法参数验证请求幂等性
 */
@RequiredArgsConstructor
public final class IdempotentParamExecuteHandler extends AbstractIdempotentExecuteHandler implements IdempotentParamService {

    private final RedissonClient redissonClient;

    private final static String LOCK = "lock:param:restAPI";

    @Override
    protected IdempotentParamWrapper buildWrapper(ProceedingJoinPoint joinPoint) {
        return null;
    }

    /**
     * @return 当前操作用户 ID
     */
    private String getCurrentUserId() {
        String userId = UserContext.getUserId();
        if (StrUtil.isBlank(userId)) {
            throw new ClientException("用户ID获取失败，请登录");
        }
        return userId;
    }

    /**
     * @return 获取当前线程上下文 ServletPath
     */
    private String getServletPath() {
        //它的核心功能就是将当前请求的上下文绑定到线程（ThreadLocal）里
        //这种方式依赖于当前线程有 HTTP 请求上下文（即 DispatcherServlet 里执行），如果是异步线程就拿不到。
      return   ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes()).getRequest().getServletPath();
    }

    /**
     * @return joinPoint md5
     */
    private String calcArgsMD5(ProceedingJoinPoint joinPoint) {
        return DigestUtil.md5Hex(JSON.toJSONBytes(joinPoint.getArgs()));
    }

    @Override
    public void handler(IdempotentParamWrapper idempotentParamWrapper) {

    }

    @Override
    public void exceptionProcessing() {
        postProcessing();
    }
}
