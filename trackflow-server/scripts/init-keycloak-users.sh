#!/bin/bash
# =============================================================================
# TrackFlow - Keycloak 测试用户初始化脚本
# 用途：将数据库中预置的测试用户同步创建到 Keycloak
# 使用：docker compose up -d 后执行此脚本
# =============================================================================

KEYCLOAK_URL="http://localhost:8080"
REALM="trackflow"
ADMIN_USER="admin"
ADMIN_PASS="admin"
DEFAULT_PASSWORD="test123"

# 测试用户列表：username|firstName|lastName|email
USERS=(
  "testuser|Test|User|test@trackflow.dev"
  "zhangwei|伟|张|zhangwei@company.com"
  "lina|娜|李|lina@company.com"
  "wangqiang|强|王|wangqiang@company.com"
  "zhaojing|静|赵|zhaojing@company.com"
  "liuyang|洋|刘|liuyang@company.com"
  "chenfei|飞|陈|chenfei@company.com"
  "yangmin|敏|杨|yangmin@company.com"
  "huanglei|磊|黄|huanglei@company.com"
)

echo "========================================="
echo "TrackFlow Keycloak 用户初始化"
echo "========================================="

# 获取 admin token
echo ""
echo "[1/3] 获取 Keycloak Admin Token..."
TOKEN_RESPONSE=$(curl -s -X POST "${KEYCLOAK_URL}/realms/master/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "client_id=admin-cli&username=${ADMIN_USER}&password=${ADMIN_PASS}&grant_type=password")

TOKEN=$(echo "$TOKEN_RESPONSE" | grep -o '"access_token":"[^"]*"' | cut -d'"' -f4)

if [ -z "$TOKEN" ]; then
  echo "❌ 无法获取 Admin Token，请确认 Keycloak 已启动且 admin 密码正确"
  exit 1
fi
echo "✅ Token 获取成功"

# 获取已有用户
echo ""
echo "[2/3] 检查已有用户..."
EXISTING_USERS=$(curl -s -X GET "${KEYCLOAK_URL}/admin/realms/${REALM}/users?max=100" \
  -H "Authorization: Bearer ${TOKEN}")

# 创建用户
echo ""
echo "[3/3] 创建缺失用户..."
CREATED=0
SKIPPED=0
FAILED=0

for user_entry in "${USERS[@]}"; do
  IFS='|' read -r username firstName lastName email <<< "$user_entry"

  # 检查是否已存在
  if echo "$EXISTING_USERS" | grep -q "\"username\":\"${username}\""; then
    echo "  ⏭️  ${username} - 已存在，跳过"
    ((SKIPPED++))
    continue
  fi

  # 创建用户
  HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST \
    "${KEYCLOAK_URL}/admin/realms/${REALM}/users" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Content-Type: application/json" \
    -d "{
      \"username\": \"${username}\",
      \"enabled\": true,
      \"emailVerified\": true,
      \"firstName\": \"${firstName}\",
      \"lastName\": \"${lastName}\",
      \"email\": \"${email}\",
      \"credentials\": [{\"type\": \"password\", \"value\": \"${DEFAULT_PASSWORD}\", \"temporary\": false}]
    }")

  if [ "$HTTP_CODE" = "201" ]; then
    echo "  ✅ ${username} - 创建成功"
    ((CREATED++))
  elif [ "$HTTP_CODE" = "409" ]; then
    echo "  ⏭️  ${username} - 已存在（409）"
    ((SKIPPED++))
  elif [ "$HTTP_CODE" = "401" ]; then
    # Token 过期，重新获取
    echo "  🔄 Token 过期，重新获取..."
    TOKEN_RESPONSE=$(curl -s -X POST "${KEYCLOAK_URL}/realms/master/protocol/openid-connect/token" \
      -H "Content-Type: application/x-www-form-urlencoded" \
      -d "client_id=admin-cli&username=${ADMIN_USER}&password=${ADMIN_PASS}&grant_type=password")
    TOKEN=$(echo "$TOKEN_RESPONSE" | grep -o '"access_token":"[^"]*"' | cut -d'"' -f4)

    # 重试
    HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST \
      "${KEYCLOAK_URL}/admin/realms/${REALM}/users" \
      -H "Authorization: Bearer ${TOKEN}" \
      -H "Content-Type: application/json" \
      -d "{
        \"username\": \"${username}\",
        \"enabled\": true,
        \"emailVerified\": true,
        \"firstName\": \"${firstName}\",
        \"lastName\": \"${lastName}\",
        \"email\": \"${email}\",
        \"credentials\": [{\"type\": \"password\", \"value\": \"${DEFAULT_PASSWORD}\", \"temporary\": false}]
      }")

    if [ "$HTTP_CODE" = "201" ]; then
      echo "  ✅ ${username} - 创建成功（重试）"
      ((CREATED++))
    else
      echo "  ❌ ${username} - 创建失败 (HTTP ${HTTP_CODE})"
      ((FAILED++))
    fi
  else
    echo "  ❌ ${username} - 创建失败 (HTTP ${HTTP_CODE})"
    ((FAILED++))
  fi
done

echo ""
echo "========================================="
echo "完成！创建: ${CREATED} | 跳过: ${SKIPPED} | 失败: ${FAILED}"
echo "========================================="
echo ""
echo "所有用户密码: ${DEFAULT_PASSWORD}"
