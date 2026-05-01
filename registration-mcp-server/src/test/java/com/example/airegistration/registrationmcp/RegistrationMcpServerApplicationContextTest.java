package com.example.airegistration.registrationmcp;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "NACOS_MCP_REGISTER_ENABLED=false"
)
class RegistrationMcpServerApplicationContextTest {

    @Test
    void contextLoads() {
    }
}
