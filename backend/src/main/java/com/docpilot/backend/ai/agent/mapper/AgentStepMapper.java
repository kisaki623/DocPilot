package com.docpilot.backend.ai.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.docpilot.backend.ai.agent.entity.AgentStep;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AgentStepMapper extends BaseMapper<AgentStep> {
}
