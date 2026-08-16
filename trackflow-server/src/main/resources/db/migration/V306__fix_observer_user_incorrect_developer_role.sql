-- Fix huanglei (id=1008) having incorrect developer role in TF1 project
-- Per test-accounts.md, huanglei should only be observer in all projects.
-- The developer role in TF1 was introduced by e2e test automation and causes
-- nav:batch_ops to be granted globally, showing batch checkboxes incorrectly.

-- Step 1: Change huanglei's role in TF1 from developer to observer
UPDATE project_member
SET role_id = (SELECT id FROM sys_role WHERE code = 'observer')
WHERE user_id = 1008
  AND project_id = (SELECT id FROM project WHERE key = 'TF1')
  AND role_id = (SELECT id FROM sys_role WHERE code = 'developer');

-- Step 2: Also fix MA1 project if huanglei has non-observer role there
UPDATE project_member
SET role_id = (SELECT id FROM sys_role WHERE code = 'observer')
WHERE user_id = 1008
  AND project_id = (SELECT id FROM project WHERE key = 'MA1')
  AND role_id != (SELECT id FROM sys_role WHERE code = 'observer');
