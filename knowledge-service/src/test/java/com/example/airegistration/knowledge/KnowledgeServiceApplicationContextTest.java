package com.example.airegistration.knowledge;

import com.example.airegistration.rag.service.KnowledgeIngestService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "DASHSCOPE_API_KEY=test-key",
                "NACOS_CONFIG_ENABLED=false"
        }
)
class KnowledgeServiceApplicationContextTest {

    @MockBean
    private KnowledgeIngestService ingestService;

    @Test
    void contextLoads() {
    }
}
