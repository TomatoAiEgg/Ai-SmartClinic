package com.example.airegistration.schedulemcp;

import com.example.airegistration.schedulemcp.dto.ScheduleInventoryAuditLogView;
import com.example.airegistration.schedulemcp.entity.ClinicSlotInventoryAuditLogEntity;
import com.example.airegistration.schedulemcp.entity.ScheduleInventoryAuditRecord;
import com.example.airegistration.schedulemcp.repository.ScheduleInventoryAuditRepository;
import com.example.airegistration.schedulemcp.service.ScheduleInventoryAuditQueryService;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ScheduleInventoryAuditQueryServiceTest {

    @Test
    void shouldNormalizeFiltersAndMapAuditViews() {
        RecordingAuditRepository repository = new RecordingAuditRepository();
        ScheduleInventoryAuditQueryService service = new ScheduleInventoryAuditQueryService(repository);

        List<ScheduleInventoryAuditLogView> result = service.listAuditLogs(
                " CONF-001 ",
                " trace-001 ",
                " resp ",
                " doc-101 ",
                "2026-05-10",
                " reserve ",
                true,
                500
        );

        assertThat(repository.lastOperationId).isEqualTo("CONF-001");
        assertThat(repository.lastTraceId).isEqualTo("trace-001");
        assertThat(repository.lastDepartmentCode).isEqualTo("RESP");
        assertThat(repository.lastDoctorId).isEqualTo("doc-101");
        assertThat(repository.lastOperationType).isEqualTo("RESERVE");
        assertThat(repository.lastLimit).isEqualTo(200);
        assertThat(result)
                .singleElement()
                .satisfies(view -> {
                    assertThat(view.auditId()).isEqualTo(10L);
                    assertThat(view.operationId()).isEqualTo("CONF-001");
                    assertThat(view.operationSource()).isEqualTo("REGISTRATION_CREATE");
                    assertThat(view.remainingBefore()).isEqualTo(5);
                    assertThat(view.remainingAfter()).isEqualTo(4);
                });
    }

    private static class RecordingAuditRepository implements ScheduleInventoryAuditRepository {

        private String lastOperationId;
        private String lastTraceId;
        private String lastDepartmentCode;
        private String lastDoctorId;
        private String lastOperationType;
        private int lastLimit;

        @Override
        public void append(ScheduleInventoryAuditRecord record) {
        }

        @Override
        public List<ClinicSlotInventoryAuditLogEntity> listAuditLogs(String operationId,
                                                                     String traceId,
                                                                     String departmentCode,
                                                                     String doctorId,
                                                                     String clinicDate,
                                                                     String operationType,
                                                                     Boolean success,
                                                                     int limit) {
            this.lastOperationId = operationId;
            this.lastTraceId = traceId;
            this.lastDepartmentCode = departmentCode;
            this.lastDoctorId = doctorId;
            this.lastOperationType = operationType;
            this.lastLimit = limit;

            ClinicSlotInventoryAuditLogEntity entity = new ClinicSlotInventoryAuditLogEntity();
            entity.setAuditId(10L);
            entity.setOperationType("RESERVE");
            entity.setTraceId(traceId);
            entity.setDepartmentCode(departmentCode);
            entity.setDoctorId(doctorId);
            entity.setClinicDate(clinicDate);
            entity.setStartTime("09:00");
            entity.setOperationId(operationId);
            entity.setOperationSource("REGISTRATION_CREATE");
            entity.setSuccess(success);
            entity.setReason("ok");
            entity.setRemainingBefore(5);
            entity.setRemainingAfter(4);
            entity.setSourceService("schedule-mcp-server");
            entity.setCreatedAt(OffsetDateTime.parse("2026-05-03T10:00:00+08:00"));
            return List.of(entity);
        }
    }
}
