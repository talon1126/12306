package com.talon.index12306.framework.user.core;

import com.talon.index12306.base.constant.UserConstant;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URLDecoder;


import static java.nio.charset.StandardCharsets.UTF_8;

public class UserTransmitFilter implements Filter {
    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        String id = request.getHeader(UserConstant.USER_ID_KEY);
        if (StringUtils.hasText(id)) {
            String username = request.getHeader(UserConstant.USER_NAME_KEY);
            if (StringUtils.hasText(username)) {
                username = URLDecoder.decode(username, UTF_8);
            }
            String realName = request.getHeader(UserConstant.REAL_NAME_KEY);
            if (StringUtils.hasText(realName)) {
                realName = URLDecoder.decode(realName, UTF_8);
            }
            String token = request.getHeader(UserConstant.USER_TOKEN_KEY);
            UserContext.setUser(UserInfoDTO.builder()
                    .userId(id)
                    .username(username)
                    .realName(realName)
                    .token(token)
                    .build());
        }
        try {
            filterChain.doFilter(servletRequest, servletResponse);
        } finally {
            UserContext.remove();
        }
    }
}
