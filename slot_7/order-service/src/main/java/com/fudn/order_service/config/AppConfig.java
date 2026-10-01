package com.fudn.order_service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import feign.Logger;
import feign.Retryer;

@Configuration
public class AppConfig {

    @Bean
    Retryer feignRetryer() {
        return new Retryer.Default(100, 1000, 3);
    }

    @Bean
    Logger.Level feignLoggerLevel() {
        return Logger.Level.FULL;
    }

}
