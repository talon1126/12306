package com.talon.index12306.designpattern.chain;

import cn.hutool.core.collection.CollUtil;
import com.talon.index12306.base.ApplicationContextHolder;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.Ordered;

import java.util.*;
import java.util.stream.Collectors;

public final class AbstractChainContext<T> implements CommandLineRunner {

    private static Map<String, List<AbstractChainHandler>> abstractChainHandlerContainer = new HashMap<>();

    //开启责任链入口
    public void handler(String mark, T requestParam) {
        List<AbstractChainHandler> abstractChainHandlers = abstractChainHandlerContainer.get(mark);
        if (CollUtil.isEmpty(abstractChainHandlers)) {
            throw new RuntimeException(String.format("[%s] Chain of Responsibility ID is undefined.", mark));
        }
        for (AbstractChainHandler abstractChainHandler : abstractChainHandlers) {
            abstractChainHandler.handler(requestParam);
        }
    }

    //责任链初始化
    @Override
    public void run(String... args) throws Exception {
        //从applicationcontext拿到所有实现abstractHandler的bean
        Map<String, AbstractChainHandler> beans = ApplicationContextHolder.getBeansOfType(AbstractChainHandler.class);
        //封装list
        beans.forEach((beanName, bean) -> {
            List<AbstractChainHandler> list = abstractChainHandlerContainer.get(bean.mark());
            if (CollUtil.isEmpty(list)) {
                list = new ArrayList<>();
            }
            list.add(bean);
            abstractChainHandlerContainer.put(bean.mark(), list.stream().sorted(Comparator.comparing(Ordered::getOrder))
                    .collect(Collectors.toList()));
        });

    }
}
