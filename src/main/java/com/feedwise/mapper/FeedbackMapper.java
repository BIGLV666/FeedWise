package com.feedwise.mapper;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.feedwise.entity.Feedback;
import io.github.biglv666.datascope.annotation.DataScope;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 反馈 Mapper。列表查询走 data-scope 行级过滤：SUPPORT 只能查到自己录入的反馈。 */
@Mapper
public interface FeedbackMapper extends BaseMapper<Feedback> {

    /**
     * 分页查询反馈（数据范围由 {@link com.feedwise.config.DataScopeConfig} 决策改写 SQL）。
     *
     * @param page    MP 分页参数（count SQL 同样被过滤后统计）
     * @param wrapper 查询条件
     * @return 过滤后的反馈分页
     */
    @Select("SELECT * FROM feedback ${ew.customSqlSegment}")
    @DataScope(selfColumn = "created_by")
    Page<Feedback> selectScoped(Page<Feedback> page, @Param(Constants.WRAPPER) Wrapper<Feedback> wrapper);
}
