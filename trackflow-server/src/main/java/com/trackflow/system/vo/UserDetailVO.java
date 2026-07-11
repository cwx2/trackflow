package com.trackflow.system.vo;

import lombok.Data;

import java.util.List;

@Data
public class UserDetailVO {
    private UserVO user;
    private List<String> roleIds;
}
