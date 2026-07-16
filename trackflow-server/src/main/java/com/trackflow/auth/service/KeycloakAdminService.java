package com.trackflow.auth.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trackflow.auth.config.KeycloakAdminConfig;
import com.trackflow.common.exception.BusinessException;
import com.trackflow.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * Keycloak Admin REST API 服务
 * <p>
 * 通过 client_credentials 模式获取 token，调用 Keycloak Admin API 管理用户。
 * 支持：创建用户、设置密码、查询用户是否存在。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KeycloakAdminService {

    private final KeycloakAdminConfig config;
    private final ObjectMapper objectMapper;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * 在 Keycloak 中创建新用户
     *
     * @param username    用户名
     * @param email       邮箱
     * @param firstName   名（given name）
     * @param lastName    姓（family name）
     * @param password    临时密码
     * @return 创建的用户在 Keycloak 中的 ID
     */
    public String createUser(String username, String email, String firstName, String lastName, String password) {
        String token = getAdminToken();
        String url = config.getServerUrl() + "/admin/realms/" + config.getRealm() + "/users";

        // 构建用户 representation
        Map<String, Object> userRep = Map.of(
                "username", username,
                "email", email,
                "firstName", firstName != null ? firstName : "",
                "lastName", lastName != null ? lastName : "",
                "enabled", true,
                "emailVerified", true,
                "credentials", List.of(Map.of(
                        "type", "password",
                        "value", password,
                        "temporary", true
                )),
                "realmRoles", List.of("tf_user")
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);

        try {
            ResponseEntity<Void> response = restTemplate.exchange(
                    url, HttpMethod.POST,
                    new HttpEntity<>(userRep, headers),
                    Void.class
            );

            // Keycloak 返回 201 Created，Location header 包含新用户的 URL
            if (response.getStatusCode() == HttpStatus.CREATED) {
                String location = response.getHeaders().getFirst(HttpHeaders.LOCATION);
                if (location != null) {
                    // Location: http://localhost:8080/admin/realms/trackflow/users/{keycloakUserId}
                    String keycloakUserId = location.substring(location.lastIndexOf('/') + 1);
                    log.info("Created Keycloak user: username={}, keycloakId={}", username, keycloakUserId);
                    // 分配 tf_user realm role
                    assignRealmRole(keycloakUserId, "tf_user", token);
                    return keycloakUserId;
                }
            }
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "创建 Keycloak 用户失败：未获取到用户 ID");
        } catch (HttpClientErrorException.Conflict e) {
            // 409 Conflict — 用户名或邮箱已存在
            log.warn("Keycloak user creation conflict: username={}, email={}", username, email);
            throw new BusinessException(ErrorCode.DUPLICATE_RESOURCE, "用户名或邮箱在 Keycloak 中已存在");
        } catch (HttpClientErrorException e) {
            log.error("Keycloak Admin API error: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "Keycloak 用户创建失败: " + e.getStatusCode());
        }
    }

    /**
     * 检查 Keycloak 中是否存在指定用户名的用户
     */
    public boolean userExists(String username) {
        String token = getAdminToken();
        String url = config.getServerUrl() + "/admin/realms/" + config.getRealm()
                + "/users?username=" + username + "&exact=true";

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.GET,
                    new HttpEntity<>(headers),
                    String.class
            );
            JsonNode users = objectMapper.readTree(response.getBody());
            return users.isArray() && users.size() > 0;
        } catch (Exception e) {
            log.error("Failed to check Keycloak user existence: username={}", username, e);
            return false;
        }
    }

    /**
     * 检查 Keycloak 中是否存在指定邮箱的用户
     */
    public boolean emailExists(String email) {
        String token = getAdminToken();
        String url = config.getServerUrl() + "/admin/realms/" + config.getRealm()
                + "/users?email=" + email + "&exact=true";

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.GET,
                    new HttpEntity<>(headers),
                    String.class
            );
            JsonNode users = objectMapper.readTree(response.getBody());
            return users.isArray() && users.size() > 0;
        } catch (Exception e) {
            log.error("Failed to check Keycloak email existence: email={}", email, e);
            return false;
        }
    }

    /**
     * 为 Keycloak 用户分配 realm role
     */
    private void assignRealmRole(String keycloakUserId, String roleName, String token) {
        try {
            // 1. 获取 realm role 的 representation
            String roleUrl = config.getServerUrl() + "/admin/realms/" + config.getRealm()
                    + "/roles/" + roleName;
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);

            ResponseEntity<String> roleResponse = restTemplate.exchange(
                    roleUrl, HttpMethod.GET,
                    new HttpEntity<>(headers),
                    String.class
            );
            JsonNode roleNode = objectMapper.readTree(roleResponse.getBody());

            // 2. 分配给用户
            String assignUrl = config.getServerUrl() + "/admin/realms/" + config.getRealm()
                    + "/users/" + keycloakUserId + "/role-mappings/realm";
            headers.setContentType(MediaType.APPLICATION_JSON);

            String rolePayload = "[" + roleResponse.getBody() + "]";
            restTemplate.exchange(
                    assignUrl, HttpMethod.POST,
                    new HttpEntity<>(rolePayload, headers),
                    Void.class
            );
            log.debug("Assigned realm role '{}' to Keycloak user {}", roleName, keycloakUserId);
        } catch (Exception e) {
            log.warn("Failed to assign realm role '{}' to Keycloak user {}: {}",
                    roleName, keycloakUserId, e.getMessage());
            // 不抛异常，用户已创建成功，角色分配失败不应阻断流程
        }
    }

    /**
     * 通过 client_credentials 模式获取 Admin API access token
     * <p>
     * 使用 master realm 的 admin-cli 获取 token（因为 realm 级 service account
     * 需要额外配置 realm-management 角色，master realm 更直接可靠）。
     */
    private String getAdminToken() {
        // 使用 master realm admin 凭据获取 token（开发环境简化方案）
        String tokenUrl = config.getServerUrl() + "/realms/master/protocol/openid-connect/token";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("grant_type", "password");
        params.add("client_id", "admin-cli");
        params.add("username", "admin");
        params.add("password", "admin");

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    tokenUrl, HttpMethod.POST,
                    new HttpEntity<>(params, headers),
                    String.class
            );
            JsonNode tokenNode = objectMapper.readTree(response.getBody());
            return tokenNode.get("access_token").asText();
        } catch (Exception e) {
            log.error("Failed to obtain Keycloak admin token", e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                    "无法连接 Keycloak 管理服务，请检查 Keycloak 是否运行正常");
        }
    }
}
