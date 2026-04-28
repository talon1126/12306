package com.talon.index12306.framework.user.config;

import com.talon.index12306.framework.user.core.UserTransmitFilter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;



import static com.talon.index12306.base.constant.FilterOrderConstant.USER_TRANSMIT_FILTER_ORDER;

@ConditionalOnWebApplication
public class UserAutoConfiguration {
    @Bean
    public FilterRegistrationBean<UserTransmitFilter> FilterRegistrationBean() {
        FilterRegistrationBean<UserTransmitFilter> bean = new FilterRegistrationBean<>();
        bean.setFilter(new UserTransmitFilter());
        bean.setOrder(USER_TRANSMIT_FILTER_ORDER);
        bean.addUrlPatterns("/*");
        return bean;
    }

}
