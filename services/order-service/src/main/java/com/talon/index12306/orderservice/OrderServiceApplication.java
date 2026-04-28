package com.talon.index12306.orderservice;

import cn.crane4j.spring.boot.annotation.EnableCrane4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients("com.talon.index12306.orderservice.remote")
@EnableCrane4j(enumPackages = "com.talon.index12306.orderservice.common.enums")
public class OrderServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }

}
