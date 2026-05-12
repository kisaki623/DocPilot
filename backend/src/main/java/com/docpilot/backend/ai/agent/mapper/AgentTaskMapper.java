package com.docpilot.backend.ai.agent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.docpilot.backend.ai.agent.entity.AgentTask;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AgentTaskMapper extends BaseMapper<AgentTask> {
}
