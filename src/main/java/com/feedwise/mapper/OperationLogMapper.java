package com.feedwise.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.feedwise.entity.OperationLog;
import org.apache.ibatis.annotations.Mapper;

/** 操作时间线 Mapper。 */
@Mapper
public interface OperationLogMapper extends BaseMapper<OperationLog> {
}
