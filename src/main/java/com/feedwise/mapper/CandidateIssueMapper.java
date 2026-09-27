package com.feedwise.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.feedwise.entity.CandidateIssue;
import org.apache.ibatis.annotations.Mapper;

/** 候选问题卡片 Mapper（selectById 由 cache-kit 自动缓存）。 */
@Mapper
public interface CandidateIssueMapper extends BaseMapper<CandidateIssue> {
}
