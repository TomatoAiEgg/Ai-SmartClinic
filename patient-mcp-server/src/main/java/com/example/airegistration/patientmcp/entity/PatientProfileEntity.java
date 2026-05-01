package com.example.airegistration.patientmcp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("patient_profile")
public class PatientProfileEntity {

    @TableId(value = "patient_id", type = IdType.INPUT)
    private String patientId;

    @TableField("patient_name")
    private String patientName;

    @TableField("id_type")
    private String idType;

    @TableField("id_number_masked")
    private String idNumberMasked;

    @TableField("phone_masked")
    private String phoneMasked;

    @TableField("active")
    private Boolean active;

    @TableField("verified_status")
    private String verifiedStatus;

    @TableField("source_channel")
    private String sourceChannel;

    public String getPatientId() {
        return patientId;
    }

    public void setPatientId(String patientId) {
        this.patientId = patientId;
    }

    public String getPatientName() {
        return patientName;
    }

    public void setPatientName(String patientName) {
        this.patientName = patientName;
    }

    public String getIdType() {
        return idType;
    }

    public void setIdType(String idType) {
        this.idType = idType;
    }

    public String getIdNumberMasked() {
        return idNumberMasked;
    }

    public void setIdNumberMasked(String idNumberMasked) {
        this.idNumberMasked = idNumberMasked;
    }

    public String getPhoneMasked() {
        return phoneMasked;
    }

    public void setPhoneMasked(String phoneMasked) {
        this.phoneMasked = phoneMasked;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public String getVerifiedStatus() {
        return verifiedStatus;
    }

    public void setVerifiedStatus(String verifiedStatus) {
        this.verifiedStatus = verifiedStatus;
    }

    public String getSourceChannel() {
        return sourceChannel;
    }

    public void setSourceChannel(String sourceChannel) {
        this.sourceChannel = sourceChannel;
    }
}
