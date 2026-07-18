-- ============================================================
-- V115: Quick Action System (快捷动作系统)
-- 支持配置化表单弹窗 + 邮件模板 + 执行记录
-- ============================================================

-- 快捷动作定义
CREATE TABLE quick_action_definition (
    id              BIGSERIAL PRIMARY KEY,
    project_id      BIGINT REFERENCES project(id) ON DELETE CASCADE,  -- NULL = 全局
    action_key      VARCHAR(50) NOT NULL,
    label           VARCHAR(100) NOT NULL,
    icon            VARCHAR(50),
    sort_order      INT NOT NULL DEFAULT 0,
    form_schema     JSONB NOT NULL DEFAULT '[]',
    actions         JSONB NOT NULL DEFAULT '[]',
    visibility      JSONB DEFAULT '{}',
    status_transition_to VARCHAR(50),  -- 可选：执行后触发状态变更（status name）
    enabled         BOOLEAN NOT NULL DEFAULT true,
    created_by      BIGINT,
    updated_by      BIGINT,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_qad_project ON quick_action_definition(project_id);
CREATE INDEX idx_qad_action_key ON quick_action_definition(action_key);
CREATE UNIQUE INDEX idx_qad_project_key ON quick_action_definition(project_id, action_key) WHERE project_id IS NOT NULL;
CREATE UNIQUE INDEX idx_qad_global_key ON quick_action_definition(action_key) WHERE project_id IS NULL;

COMMENT ON TABLE quick_action_definition IS '快捷动作定义（配置化表单+邮件模板触发）';
COMMENT ON COLUMN quick_action_definition.project_id IS 'NULL=全局定义，非NULL=项目级覆盖';
COMMENT ON COLUMN quick_action_definition.action_key IS '动作标识，如 send_application / cancel';
COMMENT ON COLUMN quick_action_definition.form_schema IS '表单字段配置 JSON 数组';
COMMENT ON COLUMN quick_action_definition.actions IS '可执行操作列表（评论并发邮件/仅评论等）';
COMMENT ON COLUMN quick_action_definition.visibility IS '可见性规则（roles + issueStatuses）';
COMMENT ON COLUMN quick_action_definition.status_transition_to IS '执行后可触发的目标状态名称';

-- 邮件模板
CREATE TABLE mail_template (
    id              BIGSERIAL PRIMARY KEY,
    project_id      BIGINT REFERENCES project(id) ON DELETE CASCADE,  -- NULL = 全局
    action_key      VARCHAR(50) NOT NULL,
    name            VARCHAR(100) NOT NULL,
    subject_template VARCHAR(500) NOT NULL,
    body_template   TEXT NOT NULL,
    recipients_rule JSONB DEFAULT '{}',
    sort_order      INT NOT NULL DEFAULT 0,
    enabled         BOOLEAN NOT NULL DEFAULT true,
    created_by      BIGINT,
    updated_by      BIGINT,
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_mt_project ON mail_template(project_id);
CREATE INDEX idx_mt_action_key ON mail_template(action_key);

COMMENT ON TABLE mail_template IS '邮件模板（与快捷动作关联）';
COMMENT ON COLUMN mail_template.action_key IS '关联的快捷动作 key';
COMMENT ON COLUMN mail_template.subject_template IS '邮件主题模板，支持 {issueKey} 等变量';
COMMENT ON COLUMN mail_template.body_template IS '邮件正文模板（HTML），支持变量替换';
COMMENT ON COLUMN mail_template.recipients_rule IS '收件人规则 JSON';

-- 快捷动作执行记录
CREATE TABLE quick_action_log (
    id              BIGSERIAL PRIMARY KEY,
    issue_id        BIGINT NOT NULL REFERENCES issue(id) ON DELETE CASCADE,
    action_key      VARCHAR(50) NOT NULL,
    operator_id     BIGINT NOT NULL,
    form_data       JSONB DEFAULT '{}',
    mail_template_id BIGINT REFERENCES mail_template(id) ON DELETE SET NULL,
    result_type     VARCHAR(30) NOT NULL,  -- comment_and_mail / only_comment
    comment_id      BIGINT,
    mail_sent       BOOLEAN NOT NULL DEFAULT false,
    mail_error      TEXT,
    status_before   VARCHAR(50),
    status_after    VARCHAR(50),
    created_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_qal_issue ON quick_action_log(issue_id);
CREATE INDEX idx_qal_operator ON quick_action_log(operator_id);
CREATE INDEX idx_qal_created ON quick_action_log(created_at DESC);

COMMENT ON TABLE quick_action_log IS '快捷动作执行记录';
COMMENT ON COLUMN quick_action_log.result_type IS '执行类型：comment_and_mail / only_comment';
COMMENT ON COLUMN quick_action_log.comment_id IS '生成的评论 ID';

-- ============================================================
-- 预置数据：Send Application + Cancel 两个快捷动作
-- ============================================================

-- Send Application 动作
INSERT INTO quick_action_definition (action_key, label, icon, sort_order, form_schema, actions, visibility)
VALUES (
    'send_application',
    'Send Application',
    'icon-send',
    1,
    '[
      {"key":"status_to","label":"Status to","type":"radio","required":true,"options":["Don''t Change","Done (Local Env)","No Test","Testing"]},
      {"key":"sprint","label":"Sprint","type":"select","required":false,"optionsSource":"sprints"},
      {"key":"time_spent","label":"Time spent","type":"input","required":true,"placeholder":"1w 1d 1h 1m"},
      {"key":"feature_type","label":"Is New feature or Cancel feature?","type":"select","required":true,"options":["New Feature","Cancel Feature","Bug Fix","Improvement"]},
      {"key":"mail_template","label":"Mail Template","type":"radio","required":true,"optionsSource":"mail_templates"},
      {"key":"modify_type","label":"Modify Type","type":"checkbox","required":true,"options":["Logic","Wording","UI"]},
      {"key":"details","label":"Application Details","type":"textarea","required":false,"defaultValue":"Done on xx test site(s), to be applied to xx product site(s) when it is deployed on xx/xx.\n\nScreenshot or Video:\n\nChatGPT prompts:\n\nChatGPT mockup screenshot:"}
    ]'::jsonb,
    '[
      {"key":"comment_and_mail","label":"评论并发邮件","type":"primary"},
      {"key":"only_comment","label":"仅评论","type":"secondary"}
    ]'::jsonb,
    '{"roles":["developer","tech_lead","tester"],"issueStatuses":["In Progress","Testing","Reopened"]}'::jsonb
);

-- Cancel 动作
INSERT INTO quick_action_definition (action_key, label, icon, sort_order, form_schema, actions, visibility)
VALUES (
    'cancel',
    'Cancel',
    'icon-close-circle',
    2,
    '[
      {"key":"cancellation_reason","label":"Cancellation Reason","type":"select","required":true,"options":["Technical difficulty","Unreasonable requirement","Duplicate issue","Out of scope","Client withdrew"]},
      {"key":"mockup_url","label":"Mockup from ChatGPT URL","type":"input","required":true,"placeholder":"https://..."},
      {"key":"prompts","label":"Prompts","type":"textarea","required":true},
      {"key":"comment","label":"Comment","type":"textarea","required":true,"defaultValue":"Cancel this YT.\nReason:\n\nSupporter: "}
    ]'::jsonb,
    '[
      {"key":"comment_and_mail","label":"评论并发邮件","type":"primary"},
      {"key":"only_comment","label":"仅评论","type":"secondary"}
    ]'::jsonb,
    '{"roles":["tech_lead","product_manager","project_admin"],"issueStatuses":["Open","In Progress","Reopened"]}'::jsonb
);

-- Send Application 邮件模板
INSERT INTO mail_template (action_key, name, subject_template, body_template, recipients_rule, sort_order)
VALUES
('send_application', 'Done in Daily', '[{projectName}] {issueKey} - Application: Done in Daily', '<div style="font-family:sans-serif;max-width:600px;margin:0 auto;padding:24px"><h3 style="color:#1f2328;margin:0 0 16px">{issueKey}: {issueTitle}</h3><p><b>Applicant:</b> {applicant}</p><p><b>Modify Type:</b> {modifyType}</p><p><b>Time Spent:</b> {timeSpent}</p><hr style="border:none;border-top:1px solid #d1d9e0;margin:16px 0"/><div>{details}</div><hr style="border:none;border-top:1px solid #d1d9e0;margin:16px 0"/><p style="color:#8b949e;font-size:12px">This email was sent by TrackFlow Quick Action system.</p></div>', '{"type":"project_coordinators"}', 1),
('send_application', 'Done in Daily without logic changes', '[{projectName}] {issueKey} - Done (no logic)', '<div style="font-family:sans-serif;max-width:600px;margin:0 auto;padding:24px"><h3 style="color:#1f2328;margin:0 0 16px">{issueKey}: {issueTitle}</h3><p><b>Applicant:</b> {applicant}</p><p><b>Note:</b> Done without logic changes</p><p><b>Time Spent:</b> {timeSpent}</p><hr style="border:none;border-top:1px solid #d1d9e0;margin:16px 0"/><div>{details}</div><hr style="border:none;border-top:1px solid #d1d9e0;margin:16px 0"/><p style="color:#8b949e;font-size:12px">This email was sent by TrackFlow Quick Action system.</p></div>', '{"type":"project_coordinators"}', 2),
('send_application', 'Done in Test Environment', '[{projectName}] {issueKey} - Done in Test Env', '<div style="font-family:sans-serif;max-width:600px;margin:0 auto;padding:24px"><h3 style="color:#1f2328;margin:0 0 16px">{issueKey}: {issueTitle}</h3><p><b>Applicant:</b> {applicant}</p><p><b>Environment:</b> Test</p><p><b>Modify Type:</b> {modifyType}</p><p><b>Time Spent:</b> {timeSpent}</p><hr style="border:none;border-top:1px solid #d1d9e0;margin:16px 0"/><div>{details}</div><hr style="border:none;border-top:1px solid #d1d9e0;margin:16px 0"/><p style="color:#8b949e;font-size:12px">This email was sent by TrackFlow Quick Action system.</p></div>', '{"type":"project_coordinators"}', 3),
('send_application', 'Done in Production Environment', '[{projectName}] {issueKey} - Done in Production', '<div style="font-family:sans-serif;max-width:600px;margin:0 auto;padding:24px"><h3 style="color:#1f2328;margin:0 0 16px">{issueKey}: {issueTitle}</h3><p><b>Applicant:</b> {applicant}</p><p><b>Environment:</b> Production</p><p><b>Modify Type:</b> {modifyType}</p><p><b>Time Spent:</b> {timeSpent}</p><hr style="border:none;border-top:1px solid #d1d9e0;margin:16px 0"/><div>{details}</div><hr style="border:none;border-top:1px solid #d1d9e0;margin:16px 0"/><p style="color:#8b949e;font-size:12px">This email was sent by TrackFlow Quick Action system.</p></div>', '{"type":"project_coordinators"}', 4),
('send_application', 'Flutter done in Test', '[{projectName}] {issueKey} - Flutter done in Test', '<div style="font-family:sans-serif;max-width:600px;margin:0 auto;padding:24px"><h3 style="color:#1f2328;margin:0 0 16px">{issueKey}: {issueTitle}</h3><p><b>Applicant:</b> {applicant}</p><p><b>Platform:</b> Flutter</p><p><b>Environment:</b> Test</p><p><b>Modify Type:</b> {modifyType}</p><p><b>Time Spent:</b> {timeSpent}</p><hr style="border:none;border-top:1px solid #d1d9e0;margin:16px 0"/><div>{details}</div><hr style="border:none;border-top:1px solid #d1d9e0;margin:16px 0"/><p style="color:#8b949e;font-size:12px">This email was sent by TrackFlow Quick Action system.</p></div>', '{"type":"project_coordinators"}', 5),
('send_application', 'Flutter App done in Test', '[{projectName}] {issueKey} - Flutter App in Test', '<div style="font-family:sans-serif;max-width:600px;margin:0 auto;padding:24px"><h3 style="color:#1f2328;margin:0 0 16px">{issueKey}: {issueTitle}</h3><p><b>Applicant:</b> {applicant}</p><p><b>Platform:</b> Flutter App</p><p><b>Environment:</b> Test</p><p><b>Modify Type:</b> {modifyType}</p><p><b>Time Spent:</b> {timeSpent}</p><hr style="border:none;border-top:1px solid #d1d9e0;margin:16px 0"/><div>{details}</div><hr style="border:none;border-top:1px solid #d1d9e0;margin:16px 0"/><p style="color:#8b949e;font-size:12px">This email was sent by TrackFlow Quick Action system.</p></div>', '{"type":"project_coordinators"}', 6),
('send_application', 'iOS done in Test without logic changes', '[{projectName}] {issueKey} - iOS done (no logic)', '<div style="font-family:sans-serif;max-width:600px;margin:0 auto;padding:24px"><h3 style="color:#1f2328;margin:0 0 16px">{issueKey}: {issueTitle}</h3><p><b>Applicant:</b> {applicant}</p><p><b>Platform:</b> iOS</p><p><b>Note:</b> Without logic changes</p><p><b>Time Spent:</b> {timeSpent}</p><hr style="border:none;border-top:1px solid #d1d9e0;margin:16px 0"/><div>{details}</div><hr style="border:none;border-top:1px solid #d1d9e0;margin:16px 0"/><p style="color:#8b949e;font-size:12px">This email was sent by TrackFlow Quick Action system.</p></div>', '{"type":"project_coordinators"}', 7);

-- Cancel 邮件模板
INSERT INTO mail_template (action_key, name, subject_template, body_template, recipients_rule, sort_order)
VALUES
('cancel', 'Issue Cancelled', '[{projectName}] {issueKey} - Cancelled', '<div style="font-family:sans-serif;max-width:600px;margin:0 auto;padding:24px"><h3 style="color:#cf222e;margin:0 0 16px">❌ {issueKey}: {issueTitle} - Cancelled</h3><p><b>Cancelled by:</b> {applicant}</p><p><b>Reason:</b> {cancellationReason}</p><hr style="border:none;border-top:1px solid #d1d9e0;margin:16px 0"/><div>{details}</div><hr style="border:none;border-top:1px solid #d1d9e0;margin:16px 0"/><p style="color:#8b949e;font-size:12px">This email was sent by TrackFlow Quick Action system.</p></div>', '{"type":"project_coordinators"}', 1);
