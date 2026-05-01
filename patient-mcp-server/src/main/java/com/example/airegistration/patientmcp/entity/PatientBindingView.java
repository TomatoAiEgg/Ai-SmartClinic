package com.example.airegistration.patientmcp.entity;

import com.example.airegistration.dto.PatientSummary;

public class PatientBindingView {

    private String patientId;
    private String userId;
    private String name;
    private String idType;
    private String maskedIdNumber;
    private String maskedPhone;
    private String relationCode;
    private boolean defaultPatient;

    public PatientSummary toSummary() {
        return new PatientSummary(
                patientId,
                userId,
                name,
                idType,
                maskedIdNumber,
                maskedPhone,
                relationCode,
                defaultPatient
        );
    }

    public String getPatientId() {
        return patientId;
    }

    public void setPatientId(String patientId) {
        this.patientId = patientId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getIdType() {
        return idType;
    }

    public void setIdType(String idType) {
        this.idType = idType;
    }

    public String getMaskedIdNumber() {
        return maskedIdNumber;
    }

    public void setMaskedIdNumber(String maskedIdNumber) {
        this.maskedIdNumber = maskedIdNumber;
    }

    public String getMaskedPhone() {
        return maskedPhone;
    }

    public void setMaskedPhone(String maskedPhone) {
        this.maskedPhone = maskedPhone;
    }

    public String getRelationCode() {
        return relationCode;
    }

    public void setRelationCode(String relationCode) {
        this.relationCode = relationCode;
    }

    public boolean isDefaultPatient() {
        return defaultPatient;
    }

    public void setDefaultPatient(boolean defaultPatient) {
        this.defaultPatient = defaultPatient;
    }
}
