package com.feedwise.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.feedwise.entity.User;
import io.github.biglv666.cachekit.annotation.CachedQuery;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * 用户 Mapper。getUserById 走 cache-kit 三级缓存（L1 Caffeine → L2 Redis → DB），
 * updateById/deleteById 等写方法自动失效缓存。
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {

    /**
     * 按主键查用户，结果经三级缓存；缓存键为 feedwise:user:{id}。
     *
     * @param id 用户 id
     * @return 用户实体，不存在返回 null
     */
    @Select("SELECT * FROM fw_user WHERE id = #{id}")
    @CachedQuery
    User getUserById(Long id);
}
