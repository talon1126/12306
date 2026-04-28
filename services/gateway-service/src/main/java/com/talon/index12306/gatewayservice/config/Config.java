package com.talon.index12306.gatewayservice.config;

import lombok.Data;

import java.util.List;

@Data
public class Config {

    /**
     * 黑名单前置路径
     */
    private List<String> blackPathPre;
}
