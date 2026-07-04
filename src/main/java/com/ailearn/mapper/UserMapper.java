package com.ailearn.mapper;

import com.ailearn.entity.User;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/**
 * 用户数据访问层接口
 * 继承MyBatis-Plus的BaseMapper，提供sys_user表的基础CRUD操作
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {

    /**
     * 根据用户名查询用户
     * 用于登录时通过用户名查找用户记录
     *
     * @param username 用户名
     * @return 用户实体对象，未找到则返回null
     */
    @Select("SELECT * FROM sys_user WHERE username = #{username}")
    User selectByUsername(String username);
}
