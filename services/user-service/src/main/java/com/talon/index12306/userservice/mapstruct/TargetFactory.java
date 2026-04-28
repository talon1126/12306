package com.talon.index12306.userservice.mapstruct;

import org.mapstruct.ObjectFactory;
import org.mapstruct.TargetType;
@Deprecated
public class TargetFactory {

    @ObjectFactory
    public <T> T create(@TargetType Class<T> targetType) {
       try {
           // 用反射来创建实例
           return targetType.getDeclaredConstructor().newInstance();
       }catch (Exception e) {
           throw new RuntimeException("无法创建实例: " + targetType, e);
       }
    }
}
