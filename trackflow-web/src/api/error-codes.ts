/**
 * 后端错误码常量 — 与 ErrorCode.java 保持同步
 *
 * 使用方式：
 *   import { ERROR_CODES } from '@/api/error-codes'
 *   if (res.code === ERROR_CODES.CLOSE_CONFIRMATION_REQUIRED) { ... }
 *
 * 禁止在代码中硬编码数字错误码进行分支判断。
 */
export const ERROR_CODES = {
  // 成功
  SUCCESS: 0,

  // === Validation (400xx) ===
  VALIDATION_ERROR: 40000,
  BAD_REQUEST: 40001,
  INVALID_BATCH_OPERATION: 40002,
  PROJECT_ARCHIVED: 40003,
  QUIET_HOURS_INCOMPLETE: 40004,

  // === Authentication (401xx) ===
  AUTH_MISSING: 40100,
  TOKEN_EXPIRED: 40101,
  TOKEN_INVALID_ISSUER: 40102,

  // === Authorization (403xx) ===
  ACCESS_DENIED: 40300,
  // PROJECT_ACCESS_DENIED 与 ACCESS_DENIED 共用 40300（前端无需区分，统一按"无权限"处理）
  OWNERSHIP_REQUIRED: 40301,
  WORKFLOW_TRANSITION_DENIED: 40302,
  USER_DISABLED: 40303,
  BUILTIN_ROLE_PROTECTED: 40304,

  // === Not Found (404xx) ===
  RESOURCE_NOT_FOUND: 40400,

  // === Conflict (409xx) ===
  CONFLICT: 40900,
  ORG_HAS_REFERENCES: 40901,
  ORG_CODE_DUPLICATE: 40902,
  ROLE_IN_USE: 40903,
  ROLE_CODE_DUPLICATE: 40904,
  PROJECT_KEY_DUPLICATE: 40905,
  CLOSE_CONFIRMATION_REQUIRED: 40910,

  // === Server (500xx) ===
  INTERNAL_ERROR: 50000,
} as const

/** 错误码值类型 */
export type ErrorCodeValue = (typeof ERROR_CODES)[keyof typeof ERROR_CODES]
