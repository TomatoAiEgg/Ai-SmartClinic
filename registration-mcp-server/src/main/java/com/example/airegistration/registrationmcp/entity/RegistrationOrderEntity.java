package com.example.airegistration.registrationmcp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;

@TableName("registration_order")
public class RegistrationOrderEntity {

    @TableId(value = "registration_id", type = IdType.INPUT)
    private String registrationId;

    @TableField("user_id")
    private String userId;

    @TableField("patient_id")
    private String patientId;

    @TableField("slot_id")
    private Long slotId;

    @TableField("department_code")
    private String departmentCode;

    @TableField("doctor_id")
    private String doctorId;

    @TableField("clinic_date")
    private LocalDate clinicDate;

    @TableField("start_time")
    private LocalTime startTime;

    @TableField("status")
    private String status;

    @TableField("confirmation_required")
    private Boolean confirmationRequired;

    @TableField("source_channel")
    private String sourceChannel;

    @TableField("chat_id")
    private String chatId;

    @TableField("external_request_id")
    private String externalRequestId;

    @TableField("cancel_reason")
    private String cancelReason;

    @TableField("confirmed_at")
    private OffsetDateTime confirmedAt;

    @TableField("cancelled_at")
    private OffsetDateTime cancelledAt;

    public String getRegistrationId() {
        return registrationId;
    }

    public void setRegistrationId(String registrationId) {
        this.registrationId = registrationId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getPatientId() {
        return patientId;
    }

    public void setPatientId(String patientId) {
        this.patientId = patientId;
    }

    public Long getSlotId() {
        return slotId;
    }

    public void setSlotId(Long slotId) {
        this.slotId = slotId;
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

    public LocalDate getClinicDate() {
        return clinicDate;
    }

    public void setClinicDate(LocalDate clinicDate) {
        this.clinicDate = clinicDate;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalTime startTime) {
        this.startTime = startTime;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Boolean getConfirmationRequired() {
        return confirmationRequired;
    }

    public void setConfirmationRequired(Boolean confirmationRequired) {
        this.confirmationRequired = confirmationRequired;
    }

    public String getSourceChannel() {
        return sourceChannel;
    }

    public void setSourceChannel(String sourceChannel) {
        this.sourceChannel = sourceChannel;
    }

    public String getChatId() {
        return chatId;
    }

    public void setChatId(String chatId) {
        this.chatId = chatId;
    }

    public String getExternalRequestId() {
        return externalRequestId;
    }

    public void setExternalRequestId(String externalRequestId) {
        this.externalRequestId = externalRequestId;
    }

    public String getCancelReason() {
        return cancelReason;
    }

    public void setCancelReason(String cancelReason) {
        this.cancelReason = cancelReason;
    }

    public OffsetDateTime getConfirmedAt() {
        return confirmedAt;
    }

    public void setConfirmedAt(OffsetDateTime confirmedAt) {
        this.confirmedAt = confirmedAt;
    }

    public OffsetDateTime getCancelledAt() {
        return cancelledAt;
    }

    public void setCancelledAt(OffsetDateTime cancelledAt) {
        this.cancelledAt = cancelledAt;
    }
}
