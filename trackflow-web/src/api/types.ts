/**
 * 类型定义统一出口（向后兼容的桶文件）
 *
 * 架构说明：类型已按模块拆分到 src/api/types/ 目录下，此文件为唯一的兼容入口。
 * 所有 `import type { ... } from './types'` 语句通过此文件解析到 types/ 子目录。
 *
 * ✅ 推荐：直接从子模块导入（更清晰的依赖关系）：
 *   import type { IssueVO } from './types/issue'
 *   import type { ProjectVO } from './types/project'
 *
 * ✅ 兼容：从此文件导入（已有代码保持不变）：
 *   import type { IssueVO, ProjectVO } from './types'
 *
 * ❌ 禁止：在此文件中直接定义类型，所有类型必须放在 types/ 子目录对应模块文件中。
 */
export * from './types/index'
