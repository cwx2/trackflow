// @ts-nocheck
/**
 * useKanbanBoard — Public API for the Kanban Board composable.
 *
 * This file serves as the thin entry point that:
 * 1. Re-exports shared types consumed by sub-composables
 * 2. Delegates to the implementation in useKanbanBoardImpl.ts
 *
 * Sub-composables (called internally by the implementation):
 * - useBoardFullscreen.ts — TV mode / fullscreen toggle
 * - useBoardKeyboard.ts  — Keyboard shortcuts
 * - useBoardFilter.ts    — Search / filter / debounce
 * - useBoardDrag.ts      — Drag state and events
 * - useBoardData.ts      — Data loading (projects, statuses, sprints, issues)
 * - useBoardSwimlane.ts  — Swimlane grouping and ordering
 * - useBoardColumns.ts   — Column config, WIP, progress, card field helpers
 *
 * Implementation details live in useKanbanBoardImpl.ts (~3500 lines).
 * This separation keeps the public module interface concise while the
 * implementation file handles the full business logic orchestration.
 */

// ===== Shared Types (consumed by sub-composables via `import type { ... } from './useKanbanBoard'`) =====
export type { BoardIssue, SwimlaneGroupBy, CardSize, EffectiveColumn, SwimlaneRow } from './types'

// ===== Re-export the composable function =====
export { useKanbanBoardImpl as useKanbanBoard } from './useKanbanBoardImpl'
