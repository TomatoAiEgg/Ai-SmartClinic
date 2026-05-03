package com.example.airegistration.registrationmcp;

import com.example.airegistration.registrationmcp.dto.RegistrationAuditLogView;
import com.example.airegistration.registrationmcp.entity.RegistrationAuditLogEntity;
import com.example.airegistration.registrationmcp.entity.RegistrationAuditRecord;
import com.example.airegistration.registrationmcp.repository.RegistrationAuditRepository;
import com.example.airegistration.registrationmcp.service.RegistrationAuditQueryService;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RegistrationAuditQueryServiceTest {

    @Test
    void shouldNormalizeFiltersAndMapAuditViews() {
        RecordingAuditRepository repository = new RecordingAuditRepository();
        RegistrationAuditQueryService service = new RegistrationAuditQueryService(repository);

        List<RegistrationAuditLogView> result = service.listAuditLogs(
                " REG-001 ",
                " user-001 ",
                " chat-001 ",
                " trace-001 ",
                " create ",
                true,
                500
        );

        assertThat(repository.lastRegistrationId).isEqualTo("REG-001");
        assertThat(repository.lastOperatorUserId).isEqualTo("user-001");
        assertThat(repository.lastChatId).isEqualTo("chat-001");
        assertThat(repository.lastTraceId).isEqualTo("trace-001");
        assertThat(repository.lastOperationType).isEqualTo("CREATE");
        assertThat(repository.lastSuccess).isTrue();
        assertThat(repository.lastLimit).isEqualTo(200);
        assertThat(result)
                .singleElement()
                .satisfies(view -> {
                    assertThat(view.auditId()).isEqualTo(20L);
                    assertThat(view.registrationId()).isEqualTo("REG-001");
                    assertThat(view.operationType()).isEqualTo("CREATE");
                    assertThat(view.requestPayload()).isEqualTo("{\"registrationId\":\"REG-001\"}");
                    assertThat(view.createdAt()).isEqualTo(OffsetDateTime.parse("2026-05-03T10:00:00+08:00"));
                });
    }

    private static class RecordingAuditRepository implements RegistrationAuditRepository {

        private String lastRegistrationId;
        private String lastOperatorUserId;
        private String lastChatId;
        private String lastTraceId;
        private String lastOperationType;
        private Boolean lastSuccess;
        private int lastLimit;

        @Override
        public void append(RegistrationAuditRecord record) {
        }

        @Override
        public List<RegistrationAuditLogEntity> listAuditLogs(String registrationId,
                                                              String operatorUserId,
                                                              String chatId,
                                                              String traceId,
                                                              String operationType,
                                                              Boolean success,
                                                              int limit) {
            this.lastRegistrationId = registrationId;
            this.lastOperatorUserId = operatorUserId;
            this.lastChatId = chatId;
            this.lastTraceId = traceId;
            this.lastOperationType = operationType;
            this.lastSuccess = success;
            this.lastLimit = limit;

            RegistrationAuditLogEntity entity = new RegistrationAuditLogEntity();
            entity.setAuditId(20L);
            entity.setRegistrationId(registrationId);
            entity.setOperationType(operationType);
            entity.setOperatorUserId(operatorUserId);
            entity.setChatId(chatId);
            entity.setSourceService("registration-mcp-server");
            entity.setSuccess(success);
            entity.setReason("ok");
            entity.setTraceId(traceId);
            entity.setRequestPayload("{\"registrationId\":\"REG-001\"}");
            entity.setResponsePayload("{\"status\":\"BOOKED\"}");
            entity.setBeforeSnapshot("{}");
            entity.setAfterSnapshot("{\"status\":\"BOOKED\"}");
            entity.setCreatedAt(OffsetDateTime.parse("2026-05-03T10:00:00+08:00"));
            return List.of(entity);
        }
    }
}
