package com.docpilot.backend.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.docpilot.backend.user.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserMapper extends BaseMapper<User> {

    @Select("SELECT id, username, password_hash, phone, nickname, status, create_time, update_time FROM tb_user WHERE phone = #{phone} LIMIT 1")
    User selectByPhone(String phone);

    @Select("SELECT id, username, password_hash, phone, nickname, status, create_time, update_time FROM tb_user WHERE username = #{username} LIMIT 1")
    User selectByUsername(String username);

    @Select("SELECT COUNT(1) > 0 FROM tb_user WHERE id = #{userId} AND status = 'ACTIVE' AND is_internal_admin = 1")
    boolean existsActiveInternalAdmin(Long userId);
}

