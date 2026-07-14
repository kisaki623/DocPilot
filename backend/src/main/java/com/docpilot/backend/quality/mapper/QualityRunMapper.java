package com.docpilot.backend.quality.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.docpilot.backend.quality.entity.QualityRun;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface QualityRunMapper extends BaseMapper<QualityRun> {

    @Select("SELECT * FROM tb_quality_run ORDER BY COALESCE(artifact_updated_at, imported_at) DESC, id DESC LIMIT #{limit}")
    List<QualityRun> selectRecent(int limit);

    @Select("SELECT * FROM tb_quality_run WHERE marker = #{marker} LIMIT 1")
    QualityRun selectByMarker(String marker);

    @Select("SELECT * FROM tb_quality_run WHERE source_sha256 = #{sourceSha256} LIMIT 1")
    QualityRun selectBySourceSha256(String sourceSha256);

    @Select("SELECT COUNT(*) FROM tb_quality_run")
    int countRuns();

    @Select("SELECT MAX(imported_at) FROM tb_quality_run")
    LocalDateTime selectLastImportedAt();
}
