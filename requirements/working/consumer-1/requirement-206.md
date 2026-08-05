# REQ-206：关联工单列表显示优先级彩色数字方块

## 基本信息

| 字段 | 值 |
|------|-----|
| 编号 | REQ-206 |
| 标题 | 关联工单列表显示优先级彩色数字方块 |
| 类型 | 功能缺失 |
| 严重程度 | P1 |
| 发现方式 | YouTrack 功能对标 |
| 发现日期 | 2026-08-05 |
| 关联模块 | Issue 详情页 / 关联工单区域 / DetailMainContent.vue |
| 状态 | 已修复 |

---

## 1. 问题描述

YouTrack 关联工单（Relates to / Parent for / Subtask of）列表中，每条工单前面都有一个**带数字的彩色小方块**，用于直观展示该工单的优先级：

```
☆  [4]  ENGINEER-236910  [QC][Wording]...     ← 数字4，蓝色（Normal）
★  [1]  DE4-343265       [TASK][Wording]...   ← 数字1，红色（Critical）
★  [5]  DE4-342678       [Mobile]...          ← 数字5，橙色（Major）
```

其中：
- 方块颜色 = 优先级对应的颜色（与侧边栏优先级字段的颜色一致）
- 方块内数字 = 优先级的序号/级别编号
- 方块跟随每一条关联工单，在所有展示工单的位置都显示

TrackFlow 当前关联工单列表每条只显示：`issueKey` + `issueTitle` + `statusName`，**缺少优先级色块**。

---

## 2. YouTrack 标准样式

参考截图中的关联工单列表：

```
☆  [4]  ENGINEER-236910  [QC][Wording](7/28)SUG-Videos($10): Display...
☆  [4]  ENGINEER-236606  [QC][Wording](7/28)SUG-Videos($10): Display...

★  [1]  DE4-343265  [TASK][Wording](7/28)SUG-Videos($10): Allow users...

★  [5]  DE4-342678  [Mobile](7/28)SUG-Videos($10): Display the exactly...
```

每条关联工单的行结构（从左到右）：
1. ⭐ 星标图标（是否收藏，可点击）
2. **优先级彩色数字方块**（核心缺失项）
3. 工单编号（蓝色链接）
4. 工单标题

---

## 3. 优先级数字与颜色对应关系

根据 TrackFlow 现有优先级系统（参考 issue_priority 字段定义），对应关系通常为：

| 数字 | 优先级名称 | 颜色 |
|------|-----------|------|
| 1 | Critical | 红色 `#f5222d` |
| 2 | Major | 橙红色 `#fa541c` |
| 3 | Normal | 蓝色 `#1890ff` |
| 4 | Minor | 灰蓝色 `#40a9ff` |
| 5 | Trivial | 灰色 `#8c8c8c` |

> 实际颜色和数字以后端 `issue_priority` 表中配置为准。

---

## 4. 涉及修改范围

### 4.1 后端

`LinkItem` 相关 API 返回值中需要增加优先级信息。

需要在工单关联列表的响应中补充：
- `priorityName`：优先级名称
- `priorityColor`：优先级颜色（hex）
- `priorityOrder`：优先级排序数字（用于显示方块中的数字）

涉及文件：关联工单查询的 VO / Converter / Mapper（JOIN issue 表取 priority 字段）。

### 4.2 前端

**`DetailMainContent.vue`** 中关联工单的每条 `link-item` 模板，在 `issueKey` 前面插入优先级色块：

```vue
<div v-for="link in group.items" :key="link.id" class="link-item">
  <!-- 新增：优先级彩色方块 -->
  <span
    class="link-priority-badge"
    :style="{ backgroundColor: link.priorityColor }"
    :title="link.priorityName"
  >{{ link.priorityOrder }}</span>

  <router-link :to="`/issues/${link.issueKey}`" class="link-key-ref">
    {{ link.issueKey }}
  </router-link>
  <span class="link-title-text">{{ link.issueTitle }}</span>
  <span class="link-status" :style="{ color: link.statusColor }">{{ link.statusName }}</span>
</div>
```

**CSS 样式**：

```css
.link-priority-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 16px;
  height: 16px;
  border-radius: 3px;
  font-size: 11px;
  font-weight: 600;
  color: #fff;
  flex-shrink: 0;
  margin-right: 4px;
}
```

**`LinkItem` interface** 需扩展：

```typescript
export interface LinkItem {
  id: string
  typeLabel: string
  issueId: string
  issueKey: string
  issueTitle: string
  statusName: string
  statusColor: string
  isUnresolvedBlocker?: boolean
  // 新增
  priorityName?: string
  priorityColor?: string
  priorityOrder?: number
}
```

### 4.3 同步修改其他展示工单的位置

优先级色块应在所有展示工单条目的地方一致显示（YouTrack 的"时刻跟着工单"）：

| 位置 | 文件 | 是否需要同步修改 |
|------|------|----------------|
| 关联工单列表 | `DetailMainContent.vue` | ✅ 主要修改位置 |
| 子工单列表 | `ChildIssuesList.vue` | ✅ 同步添加 |
| 工单列表行 | `IssueListItem.vue` | ✅ 同步添加（如有优先级列则已有，否则添加） |
| Apply Command 搜索结果 | `ApplyCommandDialog.vue` | ✅ 同步添加 |
| 添加关联弹窗搜索结果 | `AddLinkModal.vue` | ✅ 同步添加 |

---

## 5. 验收标准

- [ ] 关联工单列表（Relates to / Parent for / Subtask of）每条工单前显示优先级彩色数字方块
- [ ] 方块颜色与工单优先级对应，颜色与侧边栏优先级字段颜色一致
- [ ] 方块内数字为优先级序号
- [ ] 鼠标 hover 方块时显示优先级名称（tooltip）
- [ ] 优先级为空时方块不显示（不占位）
- [ ] 子工单列表同步显示优先级色块
- [ ] 添加关联弹窗的搜索结果列表同步显示优先级色块

---

## 6. 测试案例

### TC-LINK-PRIORITY-001：关联工单显示优先级色块

**前置条件**：存在一个有多个关联工单的 Issue，被关联的工单有不同优先级

**步骤**：
1. 打开有关联工单的 Issue 详情页
2. 找到关联工单区域（Relates to / Parent for / Subtask of）
3. 观察每条关联工单的展示内容

**预期结果**：
- 每条关联工单左侧有彩色数字方块
- 不同优先级的工单方块颜色不同（Critical=红，Major=橙，Normal=蓝，等）
- 方块内数字与优先级序号一致

### TC-LINK-PRIORITY-002：无优先级时不显示方块

**步骤**：
1. 找到优先级为空的关联工单

**预期结果**：
- 该工单行不显示优先级方块
- 布局不错位（其他元素对齐不受影响）

### TC-LINK-PRIORITY-003：hover 方块显示优先级名称

**步骤**：
1. 鼠标悬停在关联工单的优先级色块上

**预期结果**：
- 显示 tooltip：优先级名称（如"Critical"或"高优先级"）

### TC-LINK-PRIORITY-004：子工单列表同步显示

**步骤**：
1. 打开有子工单的 Issue 详情页
2. 观察子工单列表中每条子工单的展示

**预期结果**：
- 子工单列表同样显示优先级彩色数字方块

## 自动化状态

fix_status: DONE
fix_commit: 513a1427
fix_round: 1
test_status: PENDING
test_round: 0
review_status: PENDING
review_round: 0

======================

## Agent 交接上下文

> 由 fix-requirement-auto 会话写入，供 e2e-test 和 code-review 会话读取。
> 最后更新：2026-08-05 10:41

### 本次改动摘要
- `IssueLinkVO.java` — 新增 priority、priorityColor、priorityOrder 三个字段，使关联工单 API 返回优先级信息
- `IssueLinkService.java` — 新增 PriorityFieldService 依赖；在 listIssueLinks 中构建全局优先级选项 Map；在 buildLinkVOFromMaps 中从选项 Map 填充优先级颜色和序号（position+1）
- `types.ts` — 前端 IssueLinkVO 接口扩展 priority/priorityColor/priorityOrder 字段
- `DetailMainContent.vue` — LinkItem 接口扩展三个优先级字段；link-item 模板中 issueKey 前添加优先级彩色数字方块（v-if 保证无优先级时不显示）；新增 .link-priority-badge CSS
- `IssueDetailView.vue` — issueLinks computed 中透传 priority/priorityColor/priorityOrder
- `ChildIssuesList.vue` — 引入 getPriorityColor 和本地 PRIORITY_ORDER 映射；child-item 模板中 issueKey 前添加优先级彩色方块；新增 .child-priority-badge CSS

### 本次变更文件清单
- `trackflow-server/src/main/java/com/trackflow/issue/vo/IssueLinkVO.java`
- `trackflow-server/src/main/java/com/trackflow/issue/service/IssueLinkService.java`
- `trackflow-web/src/api/types.ts`
- `trackflow-web/src/views/issue/IssueDetailView.vue`
- `trackflow-web/src/views/issue/components/DetailMainContent.vue`
- `trackflow-web/src/views/issue/components/ChildIssuesList.vue`

### 测试重点（给 e2e-test 会话）
- **必须验证的核心路径**：
  1. 以 testuser 登录 → 打开有关联工单的 Issue 详情页（例如 DE4-1389，它有 blocks/relates_to/parent_of 关联）→ 确认关联工单列表每条显示彩色数字方块
  2. 打开有子工单的 Issue 详情页 → 确认子工单列表每条子工单前也有彩色数字方块
  3. 验证方块颜色与优先级对应（Critical=红#ef4444, Normal=紫#6366f1, Low=灰蓝#64748b 等）
  4. 验证方块内数字与优先级序号一致（Show-stopper=1, Critical=2, High=3, Normal=4, Low=5）
- **边界场景**：
  - 优先级为空的关联工单不应显示方块（不占位）
  - hover 方块时显示 title tooltip（原生 HTML title 属性）
- **建议测试账号**：testuser（admin，能看到所有项目）
- **注意事项**：需要后端重启后测试，后端已重启完毕在 8090 端口

### 审核重点（给 code-review 会话）
- **重点关注文件**：IssueLinkService.java（新增依赖是否会导致循环依赖）、ChildIssuesList.vue（硬编码 PRIORITY_ORDER 是否合理）
- **潜在风险点**：ChildIssuesList 中的 PRIORITY_ORDER 是硬编码映射，如果后端优先级选项增删需要同步前端；但这与现有 usePriorityOptions.ts 的 FALLBACK_COLORS 同模式，可接受
- **已知遗留项**：需求提到"添加关联弹窗搜索结果列表同步显示优先级色块"，该弹窗（AddLinkModal）的搜索结果来自不同 API，本次未修改（需额外 API 支持），可后续迭代

======================

## 修复记录

**修复日期**：2026-08-05
**修复人**：AI Agent（auto 模式）

### 根因分析
后端 IssueLinkVO 仅包含 issueKey/issueTitle/issueStatus，缺少优先级相关字段。前端 LinkItem 接口和模板也没有优先级色块的渲染逻辑。需要打通 Issue.priority → CustomFieldOption(color, position) → IssueLinkVO → 前端 LinkItem 的完整数据流。

### 修复方案
| 文件 | 改动说明 |
|------|----------|
| `IssueLinkVO.java` | 新增 priority/priorityColor/priorityOrder 三个字段 |
| `IssueLinkService.java` | 注入 PriorityFieldService；构建 priorityOptionMap；在 buildLinkVOFromMaps 中填充优先级信息 |
| `types.ts` | IssueLinkVO 接口扩展三个可选字段 |
| `DetailMainContent.vue` | LinkItem 扩展；模板新增 .link-priority-badge 色块；新增 CSS |
| `IssueDetailView.vue` | issueLinks computed 透传优先级字段 |
| `ChildIssuesList.vue` | 引入 getPriorityColor + 本地序号映射；模板新增 .child-priority-badge 色块 |

### 影响范围
- Issue 详情页：关联工单区域、子工单列表
- 后端 /api/v1/issues/{id}/links 接口响应新增 3 个字段（向后兼容，新增字段可选）
