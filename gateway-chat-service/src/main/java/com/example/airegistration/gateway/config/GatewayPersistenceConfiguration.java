package com.example.airegistration.gateway.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@MapperScan("com.example.airegistration.gateway.mapper")
@ConditionalOnProperty(name = "app.persistence.mybatis-enabled", havingValue = "true", matchIfMissing = true)
public class GatewayPersistenceConfiguration {
}
