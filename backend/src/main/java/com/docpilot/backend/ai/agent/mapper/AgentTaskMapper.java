package com.docpilot.backend.ai.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.docpilot.backend.ai.agent.entity.AgentTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AgentTaskMapper extends BaseMapper<AgentTask> {

    @Select("SELECT * FROM tb_agent_task WHERE id = #{taskId} AND user_id = #{userId}")
    AgentTask selectByUserAndId(@Param("userId") Long userId, @Param("taskId") Long taskId);
}
