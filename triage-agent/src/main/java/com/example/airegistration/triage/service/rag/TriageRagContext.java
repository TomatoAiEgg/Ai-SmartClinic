package com.example.airegistration.triage.service.rag;

import com.example.airegistration.dto.DepartmentSuggestion;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record TriageRagContext(List<TriageKnowledgeHit> hits) {

    public TriageRagContext {
        hits = hits == null ? List.of() : List.copyOf(hits);
    }

    public static TriageRagContext empty() {
        return new TriageRagContext(List.of());
    }

    public boolean hasHits() {
        return !hits.isEmpty();
    }

    public DepartmentSuggestion bestSuggestion(double minScore) {
        if (hits.isEmpty()) {
            return null;
        }
        TriageKnowledgeHit hit = hits.get(0);
        if (hit.getScore() < minScore) {
            return null;
        }
        if (hit.getDepartmentCode() == null || hit.getDepartmentCode().isBlank()
                || hit.getDepartmentName() == null || hit.getDepartmentName().isBlank()) {
            return null;
        }
        return new DepartmentSuggestion(
                hit.getDepartmentCode(),
                hit.getDepartmentName(),
                hit.isEmergency(),
                "RAG知识库命中证据 " + hit.getEvidenceId() + "，建议优先考虑" + hit.getDepartmentName()
        );
    }

    public Map<String, Object> toPromptData() {
        List<Map<String, Object>> evidence = hits.stream()
                .map(hit -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("evidenceId", hit.getEvidenceId());
                    item.put("departmentCode", hit.getDepartmentCode());
                    item.put("departmentName", hit.getDepartmentName());
                    item.put("title", hit.getTitle());
                    item.put("content", hit.getContent());
                    item.put("emergency", hit.isEmergency());
                    item.put("score", hit.getScore());
                    return item;
                })
                .toList();
        return Map.of(
                "enabled", true,
                "hitCount", hits.size(),
                "evidence", evidence
        );
    }
}
