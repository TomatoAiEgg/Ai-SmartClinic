package com.example.airegistration.registration.service.rag;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface RegistrationPolicyMapper {

    @Select("""
            SELECT policy_id,
                   source_id,
                   source_name,
                   document_id,
                   policy_type,
                   action_tag,
                   title,
                   content,
                   metadata::text AS metadata_json,
                   1 - (embedding <=> CAST(#{embedding} AS vector)) AS score
            FROM registration_policy_chunk
            WHERE namespace = #{namespace}
              AND enabled = true
              AND (action_tag = 'ALL' OR action_tag = #{actionTag})
              AND (valid_from IS NULL OR valid_from <= CURRENT_DATE)
              AND (valid_to IS NULL OR valid_to >= CURRENT_DATE)
              AND 1 - (embedding <=> CAST(#{embedding} AS vector)) >= #{minScore}
            ORDER BY embedding <=> CAST(#{embedding} AS vector)
            LIMIT #{topK}
            """)
    @Results({
            @Result(column = "policy_id", property = "policyId"),
            @Result(column = "source_id", property = "sourceId"),
            @Result(column = "source_name", property = "sourceName"),
            @Result(column = "document_id", property = "documentId"),
            @Result(column = "policy_type", property = "policyType"),
            @Result(column = "action_tag", property = "actionTag"),
            @Result(column = "title", property = "title"),
            @Result(column = "content", property = "content"),
            @Result(column = "metadata_json", property = "metadataJson"),
            @Result(column = "score", property = "score")
    })
    List<RegistrationPolicyHit> search(@Param("namespace") String namespace,
                                       @Param("actionTag") String actionTag,
                                       @Param("embedding") String embedding,
                                       @Param("topK") int topK,
                                       @Param("minScore") double minScore);
}
