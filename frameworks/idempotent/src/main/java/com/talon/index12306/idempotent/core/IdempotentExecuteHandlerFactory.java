package com.talon.index12306.idempotent.core;

import com.talon.index12306.base.ApplicationContextHolder;
import com.talon.index12306.idempotent.core.param.IdempotentParamService;
import com.talon.index12306.idempotent.core.spel.IdempotentSpELByMQExecuteHandler;
import com.talon.index12306.idempotent.core.spel.IdempotentSpELByRestAPIExecuteHandler;
import com.talon.index12306.idempotent.core.token.IdempotentTokenService;
import com.talon.index12306.idempotent.enums.IdempotentSceneEnum;
import com.talon.index12306.idempotent.enums.IdempotentTypeEnum;

/**
 * 幂等执行处理器工厂
 * <p>
 * Q：可能会有同学有疑问：这里为什么要采用简单工厂模式？策略模式不行么？
 * A：策略模式同样可以达到获取真正幂等处理器功能。但是简单工厂的语意更适合这个场景，所以选择了简单工厂
 * 公众号：马丁玩编程，回复：加群，添加马哥微信（备注：12306）获取项目资料
 */
public final class IdempotentExecuteHandlerFactory {

    public static IdempotentExecuteHandler getInstance(IdempotentSceneEnum scene, IdempotentTypeEnum type) {
        IdempotentExecuteHandler result = null;
        switch (scene) {
            case RESTAPI -> {
                switch (type) {
                    case PARAM -> result = ApplicationContextHolder.getBean(IdempotentParamService.class);
                    case TOKEN -> result = ApplicationContextHolder.getBean(IdempotentTokenService.class);
                    case SPEL -> result = ApplicationContextHolder.getBean(IdempotentSpELByRestAPIExecuteHandler.class);
                    default -> {
                    }
                }
            }
            case MQ -> result = ApplicationContextHolder.getBean(IdempotentSpELByMQExecuteHandler.class);
            default -> {
            }
    }
        return result;
}

}