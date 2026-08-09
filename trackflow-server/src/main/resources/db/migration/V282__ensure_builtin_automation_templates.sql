-- V280 仅修复已存在的内置模板；历史环境若模板记录被清理，会导致“从模板创建”为空。
-- 本迁移以 sort_order 作为内置模板的稳定标识：已有记录更新到正式契约，缺失记录补齐。
CREATE TEMP TABLE tf_builtin_automation_template_seed (
    sort_order  INT PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    description TEXT,
    category    VARCHAR(50),
    icon        VARCHAR(20),
    definition  TEXT NOT NULL
) ON COMMIT DROP;

INSERT INTO tf_builtin_automation_template_seed
        (sort_order, name, description, category, icon, definition)
VALUES
(1, '待办工单检测', '查询当前执行身份有权限访问的待办工单，并按是否存在待办结束流程。', 'issue_management', '🔍', $pending$
{"globalVariables":{},"nodes":[
{"id":"start","type":"start","position":{"x":220,"y":180},"nodeMeta":{"title":"开始","icon":"▶","description":"工作流触发入口","color":"#10b981","category":"特殊节点"},"inputs":[],"outputs":[{"name":"trigger","valueType":"object","description":""}],"config":{}},
{"id":"search","type":"trackflow-issue-search","position":{"x":520,"y":180},"nodeMeta":{"title":"查找待办需求","icon":"🔍","description":"查询执行身份可访问的待办工单","color":"#0ea5e9","category":"TrackFlow"},"inputs":[{"name":"projectId","valueType":"number","required":false,"description":"","optional":false,"value":{"type":"ref","nodeId":"start","outputName":"trigger","path":"projectId"}},{"name":"statusIds","valueType":"string","required":false,"description":"","optional":false,"value":null},{"name":"priority","valueType":"string","required":false,"description":"","optional":false,"value":null},{"name":"issueType","valueType":"string","required":false,"description":"","optional":false,"value":null},{"name":"tagIds","valueType":"string","required":false,"description":"","optional":false,"value":null},{"name":"savedQueryId","valueType":"number","required":false,"description":"","optional":true,"value":null},{"name":"keyword","valueType":"string","required":false,"description":"","optional":true,"value":null},{"name":"assignedToMe","valueType":"boolean","required":false,"description":"","optional":true,"value":null},{"name":"sort","valueType":"string","required":false,"description":"","optional":true,"value":null},{"name":"limit","valueType":"number","required":false,"description":"","optional":true,"value":{"type":"literal","value":20}}],"outputs":[{"name":"issues","valueType":"array","description":""},{"name":"count","valueType":"number","description":""},{"name":"hasWork","valueType":"boolean","description":""}],"config":{}},
{"id":"has-work","type":"condition","position":{"x":820,"y":180},"nodeMeta":{"title":"有待处理工单？","icon":"❓","description":"根据搜索结果选择分支","color":"#f59e0b","category":"控制流"},"inputs":[{"name":"value","valueType":"any","required":true,"description":"","optional":false,"value":{"type":"ref","nodeId":"search","outputName":"hasWork"}}],"outputs":[{"name":"true","valueType":"boolean","description":""},{"name":"false","valueType":"boolean","description":""}],"config":{"operator":"equals","compareValue":"true"}},
{"id":"work-found","type":"end","position":{"x":1120,"y":100},"nodeMeta":{"title":"结束（存在待办）","icon":"⏹","description":"流程结束","color":"#ef4444","category":"特殊节点"},"inputs":[{"name":"result","valueType":"any","required":false,"description":"","optional":false,"value":{"type":"ref","nodeId":"has-work","outputName":"true"}}],"outputs":[],"config":{}},
{"id":"no-work","type":"end","position":{"x":1120,"y":260},"nodeMeta":{"title":"结束（无待办）","icon":"⏹","description":"流程结束","color":"#ef4444","category":"特殊节点"},"inputs":[{"name":"result","valueType":"any","required":false,"description":"","optional":false,"value":{"type":"ref","nodeId":"has-work","outputName":"false"}}],"outputs":[],"config":{}}
],"edges":[{"id":"start-search","sourceNodeId":"start","sourcePortName":"trigger","targetNodeId":"search","targetPortName":"projectId"},{"id":"search-condition","sourceNodeId":"search","sourcePortName":"hasWork","targetNodeId":"has-work","targetPortName":"value"},{"id":"condition-work","sourceNodeId":"has-work","sourcePortName":"true","targetNodeId":"work-found","targetPortName":"result"},{"id":"condition-empty","sourceNodeId":"has-work","sourcePortName":"false","targetNodeId":"no-work","targetPortName":"result"}]}
$pending$),
(2, '人工审批流程', '创建审批并在批准或拒绝后分别结束；复制后即可试运行。', 'general', '🛡️', $approval$
{"globalVariables":{},"nodes":[
{"id":"start","type":"start","position":{"x":220,"y":180},"nodeMeta":{"title":"开始","icon":"▶","description":"工作流触发入口","color":"#10b981","category":"特殊节点"},"inputs":[],"outputs":[{"name":"trigger","valueType":"object","description":""}],"config":{}},
{"id":"approval","type":"approval","position":{"x":560,"y":180},"nodeMeta":{"title":"人工审批","icon":"🛡️","description":"暂停并等待审批结果","color":"#dc2626","category":"控制流"},"inputs":[{"name":"title","valueType":"string","required":true,"description":"","optional":false,"value":{"type":"literal","value":"确认执行自动化操作"}},{"name":"description","valueType":"string","required":false,"description":"","optional":false,"value":{"type":"literal","value":"请确认本次自动化请求的输入和影响范围。"}},{"name":"payload","valueType":"object","required":false,"description":"","optional":false,"value":{"type":"ref","nodeId":"start","outputName":"trigger"}}],"outputs":[{"name":"approved","valueType":"boolean","description":""},{"name":"rejected","valueType":"boolean","description":""},{"name":"comment","valueType":"string","description":""}],"config":{"riskLevel":"medium","expiryHours":24}},
{"id":"approved-end","type":"end","position":{"x":900,"y":100},"nodeMeta":{"title":"结束（批准）","icon":"⏹","description":"流程结束","color":"#ef4444","category":"特殊节点"},"inputs":[{"name":"result","valueType":"any","required":false,"description":"","optional":false,"value":{"type":"ref","nodeId":"approval","outputName":"approved"}}],"outputs":[],"config":{}},
{"id":"rejected-end","type":"end","position":{"x":900,"y":260},"nodeMeta":{"title":"结束（拒绝）","icon":"⏹","description":"流程结束","color":"#ef4444","category":"特殊节点"},"inputs":[{"name":"result","valueType":"any","required":false,"description":"","optional":false,"value":{"type":"ref","nodeId":"approval","outputName":"rejected"}}],"outputs":[],"config":{}}
],"edges":[{"id":"start-approval","sourceNodeId":"start","sourcePortName":"trigger","targetNodeId":"approval","targetPortName":"payload"},{"id":"approval-approved","sourceNodeId":"approval","sourcePortName":"approved","targetNodeId":"approved-end","targetPortName":"result"},{"id":"approval-rejected","sourceNodeId":"approval","sourcePortName":"rejected","targetNodeId":"rejected-end","targetPortName":"result"}]}
$approval$),
(3, '延时执行', '等待五秒后结束，用于验证延时、恢复和执行历史。', 'general', '⏱️', $delay$
{"globalVariables":{},"nodes":[
{"id":"start","type":"start","position":{"x":220,"y":180},"nodeMeta":{"title":"开始","icon":"▶","description":"工作流触发入口","color":"#10b981","category":"特殊节点"},"inputs":[],"outputs":[{"name":"trigger","valueType":"object","description":""}],"config":{}},
{"id":"delay","type":"delay","position":{"x":560,"y":180},"nodeMeta":{"title":"延时等待","icon":"⏱️","description":"暂停五秒后继续","color":"#64748b","category":"控制流"},"inputs":[{"name":"duration","valueType":"number","required":false,"description":"","optional":false,"value":{"type":"literal","value":5}}],"outputs":[{"name":"done","valueType":"boolean","description":""}],"config":{"seconds":5}},
{"id":"end","type":"end","position":{"x":900,"y":180},"nodeMeta":{"title":"结束","icon":"⏹","description":"流程结束","color":"#ef4444","category":"特殊节点"},"inputs":[{"name":"result","valueType":"any","required":false,"description":"","optional":false,"value":{"type":"ref","nodeId":"delay","outputName":"done"}}],"outputs":[],"config":{}}
],"edges":[{"id":"start-delay","sourceNodeId":"start","sourcePortName":"trigger","targetNodeId":"delay","targetPortName":"duration"},{"id":"delay-end","sourceNodeId":"delay","sourcePortName":"done","targetNodeId":"end","targetPortName":"result"}]}
$delay$);

UPDATE automation_workflow_template target
SET name = seed.name,
    description = seed.description,
    category = seed.category,
    icon = seed.icon,
    definition = seed.definition,
    is_builtin = TRUE
FROM tf_builtin_automation_template_seed seed
WHERE target.is_builtin IS TRUE
  AND target.sort_order = seed.sort_order;

INSERT INTO automation_workflow_template
        (name, description, category, icon, definition, sort_order, is_builtin)
SELECT seed.name, seed.description, seed.category, seed.icon,
       seed.definition, seed.sort_order, TRUE
FROM tf_builtin_automation_template_seed seed
WHERE NOT EXISTS (
    SELECT 1
    FROM automation_workflow_template existing
    WHERE existing.is_builtin IS TRUE
      AND existing.sort_order = seed.sort_order
);
