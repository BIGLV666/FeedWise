package com.feedwise.mapper;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.feedwise.entity.ImprovementTask;
import io.github.biglv666.datascope.annotation.DataScope;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 改进任务 Mapper。列表查询走 data-scope：DEV 只看到指派给自己的任务，PM/SUPPORT 全量。 */
@Mapper
public interface ImprovementTaskMapper extends BaseMapper<ImprovementTask> {

    /**
     * 分页查询任务（数据范围由 ScopeResolver 决策改写 SQL）。
     *
     * @param page    分页参数
     * @param wrapper 查询条件
     * @return 过滤后的任务分页
     */
    @Select("SELECT * FROM improvement_task ${ew.customSqlSegment}")
    @DataScope(selfColumn = "assignee_id")
    Page<ImprovementTask> selectScoped(Page<ImprovementTask> page, @Param(Constants.WRAPPER) Wrapper<ImprovementTask> wrapper);
}
