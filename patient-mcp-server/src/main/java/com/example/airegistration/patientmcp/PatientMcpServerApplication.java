package com.example.airegistration.patientmcp;

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class PatientMcpServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(PatientMcpServerApplication.class, args);
    }

    @Bean
    public ToolCallbackProvider toolCallbackProvider(PatientMcpTools tools) {
        return MethodToolCallbackProvider.builder().toolObjects(tools).build();
    }
}
