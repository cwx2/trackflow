-- Seed test data - idempotent (safe to run on existing database)

-- Test users
INSERT INTO sys_user (id, keycloak_id, username, display_name, email, status, created_at, updated_at)
VALUES
  (1001, 'test-vance', 'vance', 'VanceChang', 'vance@company.com', 'active', NOW(), NOW()),
  (1002, 'test-cici', 'cici', 'CiciCheng', 'cici@company.com', 'active', NOW(), NOW()),
  (1003, 'test-mike', 'mike', 'MikeWang', 'mike@company.com', 'active', NOW(), NOW()),
  (1004, 'test-sarah', 'sarah', 'SarahLiu', 'sarah@company.com', 'active', NOW(), NOW())
ON CONFLICT (id) DO NOTHING;

-- Test project (use key conflict since key has unique constraint)
INSERT INTO project (id, name, key, description, status, issue_sequence, created_by, created_at, updated_at)
VALUES (1001, 'Backend Dev', 'DE4', 'TrackFlow backend service', 'active', 1395, 1001, NOW(), NOW())
ON CONFLICT (key) DO UPDATE SET issue_sequence = GREATEST(project.issue_sequence, 1395);

-- Get the actual project id for DE4 (might not be 1001 if project already existed)
-- We use a DO block to handle this dynamically
DO $$
DECLARE
  v_project_id BIGINT;
BEGIN
  SELECT id INTO v_project_id FROM project WHERE key = 'DE4';

  -- Project members
  INSERT INTO project_member (id, project_id, user_id, role_id, joined_at)
  VALUES
    (1001, v_project_id, 1001, 2, NOW()),
    (1002, v_project_id, 1002, 3, NOW()),
    (1003, v_project_id, 1003, 3, NOW()),
    (1004, v_project_id, 1004, 4, NOW())
  ON CONFLICT (project_id, user_id) DO NOTHING;

  -- Sprints
  INSERT INTO sprint (id, project_id, name, goal, status, start_date, end_date, created_by, created_at, updated_at)
  VALUES
    (1001, v_project_id, 'Sprint 22 (6/17-6/30)', 'User management', 'completed', '2026-06-17', '2026-06-30', 1001, NOW(), NOW()),
    (1002, v_project_id, 'Sprint 23 (7/1-7/14)', 'Issue detail page', 'active', '2026-07-01', '2026-07-14', 1001, NOW(), NOW()),
    (1003, v_project_id, 'Sprint 24 (7/15-7/28)', 'Board and reports', 'planned', '2026-07-15', '2026-07-28', 1001, NOW(), NOW())
  ON CONFLICT (id) DO NOTHING;

  -- Tags
  INSERT INTO issue_tag (id, project_id, name, color, created_by, created_at)
  VALUES
    (1001, v_project_id, 'Wording', '#7c3aed', 1001, NOW()),
    (1002, v_project_id, 'P1', '#ef4444', 1001, NOW()),
    (1003, v_project_id, 'SUG', '#0891b2', 1001, NOW()),
    (1004, v_project_id, 'Mobile', '#16a34a', 1001, NOW()),
    (1005, v_project_id, 'API', '#f59e0b', 1001, NOW()),
    (1006, v_project_id, 'Backend', '#6366f1', 1001, NOW()),
    (1007, v_project_id, 'Frontend', '#ec4899', 1001, NOW()),
    (1008, v_project_id, 'Performance', '#8b5cf6', 1001, NOW())
  ON CONFLICT (id) DO NOTHING;

  -- Issues
  INSERT INTO issue (id, project_id, issue_key, title, description, issue_type, status_id, priority, assignee_id, reporter_id, sprint_id, due_date, estimated_hours, spent_hours, created_by, created_at, updated_at)
  VALUES
    (1001, v_project_id, 'DE4-1389', '[API](7/9)SUG-Wording: User feedback page wording error',
     E'## Problem\n\nReviews page text does not match behavior.\n\n### Impact\n\n- Platforms: SSG, ESG\n- Refund rate +12%\n\n### Steps\n\n1. Login SSG\n2. Cancel subscription\n3. Go to Reviews\n4. See incorrect text',
     'Bug', 2, 'Critical', 1001, 1002, 1002, '2026-07-13', 4, 2, 1002, '2026-07-09 10:00:00', NOW()),
    (1002, v_project_id, 'DE4-1390', 'SSG wording fix',
     'Fix subscription status wording on SSG.', 'Task', 1, 'High', 1001, 1001, 1002, '2026-07-12', 2, 0, 1001, '2026-07-09 10:30:00', NOW()),
    (1003, v_project_id, 'DE4-1391', 'ESG wording fix',
     'Fix subscription status wording on ESG.', 'Task', 1, 'High', 1003, 1001, 1002, '2026-07-12', 2, 0, 1001, '2026-07-09 10:35:00', NOW()),
    (1004, v_project_id, 'DE4-1245', 'Refund process automation',
     'Auto-verify and process refunds.', 'Feature', 2, 'Normal', 1003, 1002, 1002, '2026-07-20', 16, 8, 1002, '2026-06-20 09:00:00', NOW()),
    (1005, v_project_id, 'DE4-1102', 'Wording review workflow optimization',
     'Add AI pre-review.', 'Feature', 5, 'Normal', 1002, 1001, 1001, NULL, 8, 10, 1001, '2026-06-01 09:00:00', '2026-06-28 16:00:00')
  ON CONFLICT (id) DO NOTHING;

  -- Tag relations
  INSERT INTO issue_tag_relation (id, issue_id, tag_id, created_at) VALUES
    (1001, 1001, 1001, NOW()), (1002, 1001, 1002, NOW()),
    (1003, 1001, 1003, NOW()), (1004, 1001, 1004, NOW()),
    (1005, 1002, 1001, NOW()), (1006, 1003, 1001, NOW()),
    (1007, 1004, 1005, NOW()), (1008, 1004, 1006, NOW())
  ON CONFLICT DO NOTHING;

  -- Links
  INSERT INTO issue_link (id, source_issue_id, target_issue_id, link_type, created_by, created_at) VALUES
    (1001, 1001, 1004, 'blocks', 1001, NOW()),
    (1002, 1001, 1005, 'relates_to', 1001, NOW()),
    (1003, 1001, 1002, 'parent_of', 1001, NOW()),
    (1004, 1001, 1003, 'parent_of', 1001, NOW())
  ON CONFLICT DO NOTHING;

  -- Comments
  INSERT INTO issue_comment (id, issue_id, user_id, content, source, created_at, updated_at) VALUES
    (1001, 1001, 1001, 'Started working. Will submit PR tomorrow.', 'web', '2026-07-10 08:00:00', '2026-07-10 08:00:00'),
    (1002, 1001, 1002, 'PR reviewed. Suggest renaming i18n key. LGTM', 'web', '2026-07-11 08:00:00', '2026-07-11 08:00:00'),
    (1003, 1001, 1001, 'Key renamed. New commit pushed.', 'web', '2026-07-11 08:30:00', '2026-07-11 08:30:00')
  ON CONFLICT (id) DO NOTHING;

  -- Activities
  INSERT INTO issue_activity (id, issue_id, user_id, action, field_name, old_value, new_value, created_at) VALUES
    (1001, 1001, 1002, 'created', NULL, NULL, NULL, '2026-07-09 10:00:00'),
    (1002, 1001, 1002, 'updated', 'priority', 'Normal', 'Critical', '2026-07-09 11:00:00'),
    (1003, 1001, 1001, 'status_changed', 'status', 'Open', 'In Progress', '2026-07-10 08:00:00'),
    (1004, 1001, 1001, 'assigned', 'assignee', NULL, 'VanceChang', '2026-07-10 08:00:00'),
    (1005, 1001, 1001, 'commented', NULL, NULL, NULL, '2026-07-10 08:00:00'),
    (1006, 1001, 1002, 'commented', NULL, NULL, NULL, '2026-07-11 08:00:00'),
    (1007, 1001, 1001, 'commented', NULL, NULL, NULL, '2026-07-11 08:30:00')
  ON CONFLICT (id) DO NOTHING;

END $$;
