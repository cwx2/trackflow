-- 自动化工作流模板表 + 内置种子数据

CREATE TABLE automation_workflow_template (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    description TEXT,
    category    VARCHAR(50),
    icon        VARCHAR(20),
    definition  TEXT NOT NULL,
    sort_order  INT DEFAULT 0,
    is_builtin  BOOLEAN DEFAULT true,
    created_at  TIMESTAMP DEFAULT NOW()
);

COMMENT ON TABLE automation_workflow_template IS '自动化工作流模板';
COMMENT ON COLUMN automation_workflow_template.category IS '模板分类：ai_task, notification, issue_management';
COMMENT ON COLUMN automation_workflow_template.icon IS '模板图标（emoji）';
COMMENT ON COLUMN automation_workflow_template.definition IS '模板定义（与 automation_workflow.definition 格式相同）';
COMMENT ON COLUMN automation_workflow_template.is_builtin IS '内置模板不允许删除';

-- 内置模板1：定时巡检并 AI 处理待办工单
INSERT INTO automation_workflow_template (name, description, category, icon, definition, sort_order)
VALUES (
    '定时巡检并 AI 处理待办工单',
    '定时搜索待处理状态的工单，逐个交由 AI Agent 分析并自动评论处理建议，完成后自动流转状态。',
    'ai_task',
    '🔄',
    '{"globalVariables":{},"nodes":[{"id":"start-1","type":"start","position":{"x":250,"y":50},"nodeMeta":{"title":"开始","icon":"▶️","description":"工作流入口","color":"#52c41a"},"inputs":[],"outputs":[],"config":{}},{"id":"search-1","type":"trackflow-issue-search","position":{"x":250,"y":180},"nodeMeta":{"title":"搜索待处理工单","icon":"🔍","description":"搜索状态为待处理的工单","color":"#1890ff"},"inputs":[{"name":"statusName","valueType":"string","required":true,"description":"工单状态名称","value":{"type":"literal","value":"待处理"}}],"outputs":[{"name":"issues","valueType":"array","description":"匹配的工单列表"}],"config":{}},{"id":"condition-1","type":"condition","position":{"x":250,"y":310},"nodeMeta":{"title":"有待处理工单？","icon":"❓","description":"判断是否有搜索结果","color":"#faad14"},"inputs":[{"name":"expression","valueType":"string","required":true,"description":"条件表达式","value":{"type":"literal","value":"issues.length > 0"}}],"outputs":[{"name":"result","valueType":"boolean","description":"条件结果"}],"config":{}},{"id":"loop-1","type":"loop","position":{"x":250,"y":440},"nodeMeta":{"title":"遍历工单","icon":"🔁","description":"逐个处理搜索到的工单","color":"#722ed1"},"inputs":[{"name":"items","valueType":"array","required":true,"description":"要遍历的列表","value":{"type":"ref","nodeId":"search-1","outputName":"issues"}}],"outputs":[{"name":"currentItem","valueType":"object","description":"当前遍历项"}],"config":{}},{"id":"agent-1","type":"role-agent","position":{"x":250,"y":570},"nodeMeta":{"title":"AI 分析工单","icon":"🤖","description":"AI Agent 分析工单内容并给出处理建议","color":"#eb2f96"},"inputs":[{"name":"task","valueType":"string","required":true,"description":"Agent 任务描述","value":{"type":"literal","value":"分析此工单内容，给出处理建议和优先级评估"}}],"outputs":[{"name":"response","valueType":"string","description":"Agent 响应内容"}],"config":{}},{"id":"comment-1","type":"trackflow-issue-comment","position":{"x":250,"y":700},"nodeMeta":{"title":"添加 AI 评论","icon":"💬","description":"将 AI 分析结果作为评论添加到工单","color":"#13c2c2"},"inputs":[{"name":"content","valueType":"string","required":true,"description":"评论内容","value":{"type":"ref","nodeId":"agent-1","outputName":"response"}}],"outputs":[],"config":{}},{"id":"end-1","type":"end","position":{"x":250,"y":830},"nodeMeta":{"title":"结束","icon":"⏹️","description":"工作流结束","color":"#f5222d"},"inputs":[],"outputs":[],"config":{}}],"edges":[{"id":"e1","sourceNodeId":"start-1","sourcePortName":"next","targetNodeId":"search-1","targetPortName":"input"},{"id":"e2","sourceNodeId":"search-1","sourcePortName":"next","targetNodeId":"condition-1","targetPortName":"input"},{"id":"e3","sourceNodeId":"condition-1","sourcePortName":"true","targetNodeId":"loop-1","targetPortName":"input"},{"id":"e4","sourceNodeId":"loop-1","sourcePortName":"body","targetNodeId":"agent-1","targetPortName":"input"},{"id":"e5","sourceNodeId":"agent-1","sourcePortName":"next","targetNodeId":"comment-1","targetPortName":"input"},{"id":"e6","sourceNodeId":"comment-1","sourcePortName":"next","targetNodeId":"end-1","targetPortName":"input"},{"id":"e7","sourceNodeId":"condition-1","sourcePortName":"false","targetNodeId":"end-1","targetPortName":"input"}]}',
    1
);

-- 内置模板2：新工单自动 AI 分析并评论建议
INSERT INTO automation_workflow_template (name, description, category, icon, definition, sort_order)
VALUES (
    '新工单自动 AI 分析并评论建议',
    '当新工单创建时，自动触发 AI Agent 分析工单内容，并以评论形式给出处理建议。',
    'ai_task',
    '🧠',
    '{"globalVariables":{},"nodes":[{"id":"start-1","type":"start","position":{"x":250,"y":50},"nodeMeta":{"title":"开始","icon":"▶️","description":"新工单创建时触发","color":"#52c41a"},"inputs":[],"outputs":[],"config":{}},{"id":"get-1","type":"trackflow-issue-get","position":{"x":250,"y":180},"nodeMeta":{"title":"获取工单详情","icon":"📋","description":"获取触发工单的完整信息","color":"#1890ff"},"inputs":[{"name":"issueId","valueType":"string","required":true,"description":"工单 ID","value":null}],"outputs":[{"name":"issue","valueType":"object","description":"工单详情对象"}],"config":{}},{"id":"agent-1","type":"role-agent","position":{"x":250,"y":310},"nodeMeta":{"title":"AI 分析","icon":"🤖","description":"分析工单内容，给出处理建议","color":"#eb2f96"},"inputs":[{"name":"task","valueType":"string","required":true,"description":"Agent 任务描述","value":{"type":"literal","value":"分析此工单，给出处理建议、优先级评估和可能的解决方案"}}],"outputs":[{"name":"response","valueType":"string","description":"Agent 分析结果"}],"config":{}},{"id":"comment-1","type":"trackflow-issue-comment","position":{"x":250,"y":440},"nodeMeta":{"title":"添加建议评论","icon":"💬","description":"将 AI 建议作为评论发布","color":"#13c2c2"},"inputs":[{"name":"content","valueType":"string","required":true,"description":"评论内容","value":{"type":"ref","nodeId":"agent-1","outputName":"response"}}],"outputs":[],"config":{}},{"id":"end-1","type":"end","position":{"x":250,"y":570},"nodeMeta":{"title":"结束","icon":"⏹️","description":"工作流结束","color":"#f5222d"},"inputs":[],"outputs":[],"config":{}}],"edges":[{"id":"e1","sourceNodeId":"start-1","sourcePortName":"next","targetNodeId":"get-1","targetPortName":"input"},{"id":"e2","sourceNodeId":"get-1","sourcePortName":"next","targetNodeId":"agent-1","targetPortName":"input"},{"id":"e3","sourceNodeId":"agent-1","sourcePortName":"next","targetNodeId":"comment-1","targetPortName":"input"},{"id":"e4","sourceNodeId":"comment-1","sourcePortName":"next","targetNodeId":"end-1","targetPortName":"input"}]}',
    2
);

-- 内置模板3：工单状态变更时自动通知负责人
INSERT INTO automation_workflow_template (name, description, category, icon, definition, sort_order)
VALUES (
    '工单状态变更时自动通知负责人',
    '当工单状态发生变更时，检查是否有负责人，若有则通过 HTTP 请求发送通知。',
    'notification',
    '🔔',
    '{"globalVariables":{},"nodes":[{"id":"start-1","type":"start","position":{"x":250,"y":50},"nodeMeta":{"title":"开始","icon":"▶️","description":"工单状态变更时触发","color":"#52c41a"},"inputs":[],"outputs":[],"config":{}},{"id":"get-1","type":"trackflow-issue-get","position":{"x":250,"y":180},"nodeMeta":{"title":"获取工单详情","icon":"📋","description":"获取变更后的工单信息","color":"#1890ff"},"inputs":[{"name":"issueId","valueType":"string","required":true,"description":"工单 ID","value":null}],"outputs":[{"name":"issue","valueType":"object","description":"工单详情对象"}],"config":{}},{"id":"condition-1","type":"condition","position":{"x":250,"y":310},"nodeMeta":{"title":"有负责人？","icon":"❓","description":"判断工单是否已分配负责人","color":"#faad14"},"inputs":[{"name":"expression","valueType":"string","required":true,"description":"条件表达式","value":{"type":"literal","value":"issue.assigneeId != null"}}],"outputs":[{"name":"result","valueType":"boolean","description":"条件结果"}],"config":{}},{"id":"http-1","type":"http-request","position":{"x":250,"y":440},"nodeMeta":{"title":"发送通知","icon":"📤","description":"通过 HTTP 接口发送状态变更通知","color":"#fa8c16"},"inputs":[{"name":"url","valueType":"string","required":true,"description":"通知接口地址","value":{"type":"literal","value":"http://localhost:8090/api/v1/notifications/send"}},{"name":"method","valueType":"string","required":true,"description":"HTTP 方法","value":{"type":"literal","value":"POST"}}],"outputs":[{"name":"response","valueType":"object","description":"HTTP 响应"}],"config":{}},{"id":"end-1","type":"end","position":{"x":250,"y":570},"nodeMeta":{"title":"结束","icon":"⏹️","description":"工作流结束","color":"#f5222d"},"inputs":[],"outputs":[],"config":{}}],"edges":[{"id":"e1","sourceNodeId":"start-1","sourcePortName":"next","targetNodeId":"get-1","targetPortName":"input"},{"id":"e2","sourceNodeId":"get-1","sourcePortName":"next","targetNodeId":"condition-1","targetPortName":"input"},{"id":"e3","sourceNodeId":"condition-1","sourcePortName":"true","targetNodeId":"http-1","targetPortName":"input"},{"id":"e4","sourceNodeId":"http-1","sourcePortName":"next","targetNodeId":"end-1","targetPortName":"input"},{"id":"e5","sourceNodeId":"condition-1","sourcePortName":"false","targetNodeId":"end-1","targetPortName":"input"}]}',
    3
);
