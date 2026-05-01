package com.example.airegistration.triage.service.rag;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface TriageKnowledgeMapper {

    @Select("""
            SELECT evidence_id,
                   department_code,
                   department_name,
                   title,
                   content,
                   emergency,
                   1 - (embedding <=> CAST(#{embedding} AS vector)) AS score
            FROM triage_knowledge_chunk
            WHERE namespace = #{namespace}
              AND enabled = true
              AND 1 - (embedding <=> CAST(#{embedding} AS vector)) >= #{minScore}
            ORDER BY embedding <=> CAST(#{embedding} AS vector)
            LIMIT #{topK}
            """)
    @Results({
            @Result(column = "evidence_id", property = "evidenceId"),
            @Result(column = "department_code", property = "departmentCode"),
            @Result(column = "department_name", property = "departmentName"),
            @Result(column = "title", property = "title"),
            @Result(column = "content", property = "content"),
            @Result(column = "emergency", property = "emergency"),
            @Result(column = "score", property = "score")
    })
    List<TriageKnowledgeHit> search(@Param("namespace") String namespace,
                                    @Param("embedding") String embedding,
                                    @Param("topK") int topK,
                                    @Param("minScore") double minScore);
}
