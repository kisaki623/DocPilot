package com.docpilot.backend.ai.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.docpilot.backend.ai.agent.entity.AgentStep;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AgentStepMapper extends BaseMapper<AgentStep> {

    @Select("SELECT * FROM tb_agent_step WHERE task_id = #{taskId} ORDER BY step_index ASC")
    List<AgentStep> selectByTaskId(@Param("taskId") Long taskId);
}
