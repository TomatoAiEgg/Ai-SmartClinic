package com.example.airegistration.schedulemcp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("clinic_slot_inventory_audit_log")
public class ClinicSlotInventoryAuditLogEntity {

    @TableId(value = "audit_id", type = IdType.AUTO)
    private Long auditId;

    @TableField("operation_type")
    private String operationType;

    @TableField("trace_id")
    private String traceId;

    @TableField("department_code")
    private String departmentCode;

    @TableField("doctor_id")
    private String doctorId;

    @TableField("clinic_date")
    private String clinicDate;

    @TableField("start_time")
    private String startTime;

    @TableField("success")
    private Boolean success;

    @TableField("reason")
    private String reason;

    @TableField("remaining_before")
    private Integer remainingBefore;

    @TableField("remaining_after")
    private Integer remainingAfter;

    @TableField("source_service")
    private String sourceService;

    public Long getAuditId() {
        return auditId;
    }

    public void setAuditId(Long auditId) {
        this.auditId = auditId;
    }

    public String getOperationType() {
        return operationType;
    }

    public void setOperationType(String operationType) {
        this.operationType = operationType;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public String getDepartmentCode() {
        return departmentCode;
    }

    public void setDepartmentCode(String departmentCode) {
        this.departmentCode = departmentCode;
    }

    public String getDoctorId() {
        return doctorId;
    }

    public void setDoctorId(String doctorId) {
        this.doctorId = doctorId;
    }

    public String getClinicDate() {
        return clinicDate;
    }

    public void setClinicDate(String clinicDate) {
        this.clinicDate = clinicDate;
    }

    public String getStartTime() {
        return startTime;
    }

    public void setStartTime(String startTime) {
        this.startTime = startTime;
    }

    public Boolean getSuccess() {
        return success;
    }

    public void setSuccess(Boolean success) {
        this.success = success;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Integer getRemainingBefore() {
        return remainingBefore;
    }

    public void setRemainingBefore(Integer remainingBefore) {
        this.remainingBefore = remainingBefore;
    }

    public Integer getRemainingAfter() {
        return remainingAfter;
    }

    public void setRemainingAfter(Integer remainingAfter) {
        this.remainingAfter = remainingAfter;
    }

    public String getSourceService() {
        return sourceService;
    }

    public void setSourceService(String sourceService) {
        this.sourceService = sourceService;
    }
}
