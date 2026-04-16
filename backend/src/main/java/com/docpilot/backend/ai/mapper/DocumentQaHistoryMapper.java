package com.docpilot.backend.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.docpilot.backend.ai.entity.DocumentQaHistory;
import com.docpilot.backend.ai.vo.DocumentQaHistoryItemResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface DocumentQaHistoryMapper extends BaseMapper<DocumentQaHistory> {

    @Select("""
            SELECT id,
                   document_id AS documentId,
                   question,
                   answer,
                   create_time AS createTime
              FROM tb_qa_history
             WHERE user_id = #{userId}
               AND document_id = #{documentId}
             ORDER BY create_time DESC, id DESC
             LIMIT #{limit}
            """)
    List<DocumentQaHistoryItemResponse> selectRecentByUserAndDocument(@Param("userId") Long userId,
                                                                       @Param("documentId") Long documentId,
                                                                       @Param("limit") int limit);
}

