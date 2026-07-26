-- ============================================================================
-- V197: Add covering index for Sprint statistics query performance
-- ============================================================================
-- Issue: REQ-581
-- Problem: selectSprintsWithStats SQL scans all issues across all sprints in
--          a project (O(N) full scan) as IN subquery prevents index usage.
-- Solution: (1) SQL changed to use JOIN instead of IN subquery in SprintMapper.xml.
--           (2) Add partial covering index on (sprint_id, status_id) to accelerate
--               the GROUP BY aggregation in the stats subquery.
-- ============================================================================

-- Partial composite index: covers the stats subquery's WHERE + GROUP BY columns
-- Partial: only indexes non-deleted issues (reduces index size significantly)
CREATE INDEX idx_issue_sprint_status
    ON issue (sprint_id, status_id)
    WHERE deleted_at IS NULL;
