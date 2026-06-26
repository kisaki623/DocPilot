package com.docpilot.backend.memory.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.docpilot.backend.memory.entity.UserMemory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface UserMemoryMapper extends BaseMapper<UserMemory> {

    @Select("""
            SELECT *
              FROM tb_user_memory
             WHERE user_id = #{userId}
               AND status = 'ACTIVE'
               AND (#{memoryType} IS NULL OR memory_type = #{memoryType})
             ORDER BY priority DESC, update_time DESC, id DESC
             LIMIT #{limit}
            """)
    List<UserMemory> selectActiveByUser(@Param("userId") Long userId,
                                        @Param("memoryType") String memoryType,
                                        @Param("limit") int limit);

    @Select("""
            SELECT *
              FROM tb_user_memory
             WHERE user_id = #{userId}
               AND status = #{status}
               AND (#{memoryType} IS NULL OR memory_type = #{memoryType})
             ORDER BY priority DESC, update_time DESC, id DESC
             LIMIT #{limit}
            """)
    List<UserMemory> selectByUserAndStatus(@Param("userId") Long userId,
                                           @Param("status") String status,
                                           @Param("memoryType") String memoryType,
                                           @Param("limit") int limit);

    @Select("""
            SELECT *
              FROM tb_user_memory
             WHERE id = #{memoryId}
               AND user_id = #{userId}
            """)
    UserMemory selectByIdAndUserId(@Param("userId") Long userId,
                                   @Param("memoryId") Long memoryId);

    @Select("""
            SELECT *
              FROM tb_user_memory
             WHERE user_id = #{userId}
               AND memory_type = #{memoryType}
               AND content = #{content}
               AND status IN ('SUGGESTED', 'ACTIVE')
             ORDER BY id DESC
             LIMIT 1
            """)
    UserMemory selectExistingCandidate(@Param("userId") Long userId,
                                       @Param("memoryType") String memoryType,
                                       @Param("content") String content);

    @Update("""
            UPDATE tb_user_memory
               SET status = #{toStatus}
             WHERE id = #{memoryId}
               AND user_id = #{userId}
               AND status = #{fromStatus}
            """)
    int updateStatus(@Param("userId") Long userId,
                     @Param("memoryId") Long memoryId,
                     @Param("fromStatus") String fromStatus,
                     @Param("toStatus") String toStatus);

    @Update("""
            UPDATE tb_user_memory
               SET status = 'DELETED'
             WHERE id = #{memoryId}
               AND user_id = #{userId}
               AND status <> 'DELETED'
            """)
    int softDeleteByUser(@Param("userId") Long userId,
                         @Param("memoryId") Long memoryId);

    @Update("""
            UPDATE tb_user_memory
               SET last_used_time = #{lastUsedTime},
                   use_count = COALESCE(use_count, 0) + 1
             WHERE id = #{memoryId}
               AND user_id = #{userId}
            """)
    int markUsed(@Param("userId") Long userId,
                 @Param("memoryId") Long memoryId,
                 @Param("lastUsedTime") LocalDateTime lastUsedTime);
}
