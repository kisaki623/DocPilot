package com.docpilot.backend.quality.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.docpilot.backend.quality.entity.QualityRunGate;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface QualityRunGateMapper extends BaseMapper<QualityRunGate> {

    @Select("SELECT * FROM tb_quality_run_gate WHERE run_id = #{runId} ORDER BY sort_order ASC, id ASC")
    List<QualityRunGate> selectByRunId(Long runId);

    @Delete("DELETE FROM tb_quality_run_gate WHERE run_id = #{runId}")
    int deleteByRunId(Long runId);
}
