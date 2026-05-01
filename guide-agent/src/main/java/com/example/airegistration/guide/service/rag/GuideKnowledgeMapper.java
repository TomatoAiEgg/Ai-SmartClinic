package com.example.airegistration.guide.service.rag;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Result;
import org.apache.ibatis.annotations.Results;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface GuideKnowledgeMapper {

    @Select("""
            SELECT citation_id,
                   source_id,
                   source_name,
                   document_id,
                   title,
                   content,
                   metadata::text AS metadata_json,
                   1 - (embedding <=> CAST(#{embedding} AS vector)) AS score
            FROM guide_knowledge_chunk
            WHERE namespace = #{namespace}
              AND enabled = true
              AND 1 - (embedding <=> CAST(#{embedding} AS vector)) >= #{minScore}
            ORDER BY embedding <=> CAST(#{embedding} AS vector)
            LIMIT #{topK}
            """)
    @Results({
            @Result(column = "citation_id", property = "citationId"),
            @Result(column = "source_id", property = "sourceId"),
            @Result(column = "source_name", property = "sourceName"),
            @Result(column = "document_id", property = "documentId"),
            @Result(column = "title", property = "title"),
            @Result(column = "content", property = "content"),
            @Result(column = "metadata_json", property = "metadataJson"),
            @Result(column = "score", property = "score")
    })
    List<GuideKnowledgeHit> search(@Param("namespace") String namespace,
                                   @Param("embedding") String embedding,
                                   @Param("topK") int topK,
                                   @Param("minScore") double minScore);
}
