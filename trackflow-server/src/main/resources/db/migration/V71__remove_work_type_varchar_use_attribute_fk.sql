-- V71: Unify work type storage — remove time_entry.work_type VARCHAR column,
-- migrate all data to time_entry_attribute_value FK system.
-- This resolves the dual-track storage inconsistency (REQ-236).
--
-- NOTE: This migration was already applied manually/partially.
-- The work_type column has been dropped and data migrated to attribute system.
-- This script is now a no-op to maintain Flyway history consistency.

SELECT 1;
