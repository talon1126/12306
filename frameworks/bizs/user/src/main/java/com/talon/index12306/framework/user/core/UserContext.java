package com.talon.index12306.framework.user.core;

import java.util.Optional;

public  class UserContext {
    private static final ThreadLocal<UserInfoDTO> threadLocal = new ThreadLocal<>();

    public static void setUser(UserInfoDTO user) {
        threadLocal.set(user);
    }

    public static UserInfoDTO get() {
        return threadLocal.get();
    }

    public static   String getUserId() {
        return Optional.ofNullable(threadLocal.get())
                .map(UserInfoDTO::getUserId)
                .orElse(null);
    }

    public static String getUsername(){
        return Optional.ofNullable(threadLocal.get())
                .map(UserInfoDTO::getUsername)
                .orElse(null);
    }

    public static String getRealName(){
        return Optional.ofNullable(threadLocal.get())
                .map(UserInfoDTO::getRealName)
                .orElse(null);
    }

    public static String getToken(){
        return Optional.ofNullable(threadLocal.get())
                .map(UserInfoDTO::getToken)
                .orElse(null);
    }

    public static void remove() {
        threadLocal.remove();
    }
}
