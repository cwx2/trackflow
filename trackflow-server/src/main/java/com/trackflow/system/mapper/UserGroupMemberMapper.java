package com.trackflow.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.trackflow.system.entity.UserGroupMember;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface UserGroupMemberMapper extends BaseMapper<UserGroupMember> {

    /**
     * 查询用户所属的所有组 ID
     */
    @Select("SELECT group_id FROM user_group_member WHERE user_id = #{userId}")
    List<Long> selectGroupIdsByUserId(@Param("userId") Long userId);

    /**
     * 查询组的所有成员用户 ID
     */
    @Select("SELECT user_id FROM user_group_member WHERE group_id = #{groupId}")
    List<Long> selectUserIdsByGroupId(@Param("groupId") Long groupId);
}
