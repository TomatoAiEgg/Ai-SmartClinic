package com.example.airegistration.patientmcp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("user_patient_binding")
public class UserPatientBindingEntity {

    @TableId(value = "binding_id", type = IdType.AUTO)
    private Long bindingId;

    @TableField("user_id")
    private String userId;

    @TableField("patient_id")
    private String patientId;

    @TableField("relation_code")
    private String relationCode;

    @TableField("is_default")
    private Boolean defaultPatient;

    @TableField("active")
    private Boolean active;

    public Long getBindingId() {
        return bindingId;
    }

    public void setBindingId(Long bindingId) {
        this.bindingId = bindingId;
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

    public String getRelationCode() {
        return relationCode;
    }

    public void setRelationCode(String relationCode) {
        this.relationCode = relationCode;
    }

    public Boolean getDefaultPatient() {
        return defaultPatient;
    }

    public void setDefaultPatient(Boolean defaultPatient) {
        this.defaultPatient = defaultPatient;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }
}
