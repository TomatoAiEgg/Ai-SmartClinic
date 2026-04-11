package com.example.airegistration.registrationmcp;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class RegistrationMcpServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(RegistrationMcpServerApplication.class, args);
    }

    @Bean
    public ToolCallbackProvider toolCallbackProvider(RegistrationMcpTools tools) {
        return MethodToolCallbackProvider.builder().toolObjects(tools).build();
    }
}
