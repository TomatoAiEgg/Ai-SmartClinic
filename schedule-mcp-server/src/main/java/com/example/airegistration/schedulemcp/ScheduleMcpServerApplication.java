package com.example.airegistration.schedulemcp;

import com.example.airegistration.schedulemcp.controller.ScheduleMcpTools;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@MapperScan("com.example.airegistration.schedulemcp.mapper")
public class ScheduleMcpServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(ScheduleMcpServerApplication.class, args);
    }

    @Bean
    public ToolCallbackProvider toolCallbackProvider(ScheduleMcpTools tools) {
        return MethodToolCallbackProvider.builder().toolObjects(tools).build();
    }
}
