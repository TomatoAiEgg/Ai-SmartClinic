package com.example.airegistration.triage;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.airegistration.dto.DepartmentSuggestion;
import com.example.airegistration.triage.service.policy.TriagePolicy;
import org.junit.jupiter.api.Test;

class TriagePolicyTest {

    private final TriagePolicy policy = new TriagePolicy();

    @Test
    void shouldRouteRedFlagSymptomsToEmergency() {
        DepartmentSuggestion suggestion = policy.suggestDepartment("chest pain and shortness of breath");

        assertThat(suggestion.departmentCode()).isEqualTo("ER");
        assertThat(suggestion.emergency()).isTrue();
    }

    @Test
    void shouldSuggestRespiratoryDepartmentForCoughAndFever() {
        DepartmentSuggestion suggestion = policy.suggestDepartment("cough and fever for two days");

        assertThat(suggestion.departmentCode()).isEqualTo("RESP");
        assertThat(suggestion.emergency()).isFalse();
    }

    @Test
    void shouldFallbackToGeneralMedicineWhenNoRuleMatches() {
        DepartmentSuggestion suggestion = policy.suggestDepartment(null);

        assertThat(suggestion.departmentCode()).isEqualTo("GEN");
        assertThat(suggestion.departmentName()).isEqualTo("全科医学科");
    }

    @Test
    void shouldSuggestOphthalmologyForEyeDiscomfort() {
        DepartmentSuggestion suggestion = policy.suggestDepartment("我眼睛有点不舒服想挂个号");

        assertThat(suggestion.departmentCode()).isEqualTo("OPH");
        assertThat(suggestion.departmentName()).isEqualTo("眼科");
    }

    @Test
    void shouldSuggestNeurologyForHeadDiscomfort() {
        DepartmentSuggestion suggestion = policy.suggestDepartment("我头不舒服能给我挂个号吗");

        assertThat(suggestion.departmentCode()).isEqualTo("NEURO");
        assertThat(suggestion.departmentName()).isEqualTo("神经内科");
    }
}
