-- ============================================================================
-- V100: Add indexes on issue.resolved_at for report query performance
-- ============================================================================
-- Issue: REQ-386
-- Problem: Report queries (selectResolvedTrend, selectResolutionTimeTrend,
--          selectResolutionTimeByGroup) filter on resolved_at range but no index exists.
-- Solution: Add partial indexes to cover resolved_at range queries.
-- ============================================================================

-- Single-column partial index: covers resolved_at range scans
-- Partial: only indexes rows where resolved_at IS NOT NULL (excludes unresolved issues)
CREATE INDEX idx_issue_resolved_at
    ON issue (resolved_at)
    WHERE resolved_at IS NOT NULL;

-- Composite partial index: optimal for project-scoped resolved_at queries
-- Covers: WHERE project_id = ? AND resolved_at >= ? AND resolved_at <= ? AND deleted_at IS NULL
CREATE INDEX idx_issue_project_resolved
    ON issue (project_id, resolved_at)
    WHERE resolved_at IS NOT NULL AND deleted_at IS NULL;
