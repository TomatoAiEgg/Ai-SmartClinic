package com.example.airegistration.schedulemcp;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class ScheduleMcpServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ScheduleMcpServerApplication.class, args);
    }

    @Bean
    public ToolCallbackProvider toolCallbackProvider(ScheduleMcpTools tools) {
        return MethodToolCallbackProvider.builder().toolObjects(tools).build();
    }
}
