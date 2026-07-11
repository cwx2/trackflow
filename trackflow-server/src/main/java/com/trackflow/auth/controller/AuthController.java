package com.trackflow.auth.controller;

import com.trackflow.common.model.R;
import com.trackflow.common.util.SecurityUtils;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    @GetMapping("/me")
    public R<Map<String, Object>> getCurrentUser() {
        Jwt jwt = SecurityUtils.getCurrentJwt();
        if (jwt == null) {
            return R.fail(40100, "未认证");
        }
        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("keycloakId", jwt.getSubject());
        userInfo.put("username", jwt.getClaimAsString("preferred_username"));
        userInfo.put("displayName", jwt.getClaimAsString("name"));
        userInfo.put("email", jwt.getClaimAsString("email"));
        return R.ok(userInfo);
    }
}
