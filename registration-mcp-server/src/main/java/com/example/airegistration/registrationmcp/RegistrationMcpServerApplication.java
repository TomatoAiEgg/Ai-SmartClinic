package com.example.airegistration.registrationmcp;

import com.example.airegistration.registrationmcp.controller.RegistrationMcpTools;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@MapperScan("com.example.airegistration.registrationmcp.mapper")
public class RegistrationMcpServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(RegistrationMcpServerApplication.class, args);
    }

    @Bean
    public ToolCallbackProvider toolCallbackProvider(RegistrationMcpTools tools) {
        return MethodToolCallbackProvider.builder().toolObjects(tools).build();
    }
}
