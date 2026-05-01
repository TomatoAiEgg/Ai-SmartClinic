package com.example.airegistration.agent;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public record AgentWorkflowDefinition(
        String id,
        String name,
        String ownerBusinessModule,
        List<AgentWorkflowNode> nodes,
        List<AgentWorkflowEdge> edges
) {
    public AgentWorkflowDefinition {
        id = requireText(id, "id");
        name = requireText(name, "name");
        ownerBusinessModule = requireText(ownerBusinessModule, "ownerBusinessModule");
        nodes = List.copyOf(nodes == null ? List.of() : nodes);
        edges = List.copyOf(edges == null ? List.of() : edges);
        validateGraph(nodes, edges);
    }

    public String toMermaid() {
        StringBuilder builder = new StringBuilder("flowchart TD\n");
        for (AgentWorkflowNode node : nodes) {
            builder.append("    ")
                    .append(node.id())
                    .append("[\"")
                    .append(node.name())
                    .append("<br/>")
                    .append(node.businessCapability())
                    .append("\"]\n");
        }
        for (AgentWorkflowEdge edge : edges) {
            builder.append("    ")
                    .append(edge.source())
                    .append(" -->|")
                    .append(edge.condition())
                    .append("| ")
                    .append(edge.target())
                    .append("\n");
        }
        return builder.toString();
    }

    private static void validateGraph(List<AgentWorkflowNode> nodes, List<AgentWorkflowEdge> edges) {
        if (nodes.isEmpty()) {
            throw new IllegalArgumentException("workflow must contain at least one node");
        }
        Set<String> nodeIds = new HashSet<>();
        for (AgentWorkflowNode node : nodes) {
            if (!nodeIds.add(node.id())) {
                throw new IllegalArgumentException("duplicate node id: " + node.id());
            }
        }
        for (AgentWorkflowEdge edge : edges) {
            if (!nodeIds.contains(edge.source())) {
                throw new IllegalArgumentException("edge source does not exist: " + edge.source());
            }
            if (!nodeIds.contains(edge.target())) {
                throw new IllegalArgumentException("edge target does not exist: " + edge.target());
            }
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
