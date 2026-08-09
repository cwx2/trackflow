-- V275 的首版内置模板引用了已删除的端口模型（statusName/expression/items/response/next）。
-- 内置模板是产品提供的不可编辑内容，因此直接升级为当前可执行契约；不触碰用户自定义模板。

UPDATE automation_workflow_template
SET name = '待办工单检测',
    description = '在当前执行身份可访问的范围内检查待办工单，并输出是否存在待办。',
    category = 'issue_management',
    icon = '🔎',
    definition = $pending$
{"globalVariables":{},"nodes":[
{"id":"start","type":"start","position":{"x":220,"y":180},"nodeMeta":{"title":"开始","icon":"▶","description":"工作流触发入口","color":"#10b981","category":"特殊节点"},"inputs":[],"outputs":[{"name":"trigger","valueType":"object","description":""}],"config":{}},
{"id":"search","type":"trackflow-issue-search","position":{"x":520,"y":180},"nodeMeta":{"title":"查找待办需求","icon":"🔎","description":"按项目、状态、优先级、类型和关键词筛选需求","color":"#0ea5e9","category":"TrackFlow"},"inputs":[{"name":"projectId","valueType":"number","required":false,"description":"","optional":false,"value":{"type":"ref","nodeId":"start","outputName":"trigger","path":"projectId"}},{"name":"statusIds","valueType":"string","required":false,"description":"","optional":false,"value":null},{"name":"priority","valueType":"string","required":false,"description":"","optional":false,"value":null},{"name":"issueType","valueType":"string","required":false,"description":"","optional":false,"value":null},{"name":"tagIds","valueType":"string","required":false,"description":"","optional":false,"value":null},{"name":"savedQueryId","valueType":"number","required":false,"description":"","optional":true,"value":null},{"name":"keyword","valueType":"string","required":false,"description":"","optional":true,"value":null},{"name":"assignedToMe","valueType":"boolean","required":false,"description":"","optional":true,"value":null},{"name":"sort","valueType":"string","required":false,"description":"","optional":true,"value":null},{"name":"limit","valueType":"number","required":false,"description":"","optional":true,"value":{"type":"literal","value":20}}],"outputs":[{"name":"issues","valueType":"array","description":""},{"name":"count","valueType":"number","description":""},{"name":"hasWork","valueType":"boolean","description":""}],"config":{}},
{"id":"has-work","type":"condition","position":{"x":820,"y":180},"nodeMeta":{"title":"有待处理工单？","icon":"🔀","description":"根据条件决定分支流转","color":"#f59e0b","category":"控制流"},"inputs":[{"name":"value","valueType":"any","required":true,"description":"","optional":false,"value":{"type":"ref","nodeId":"search","outputName":"hasWork"}}],"outputs":[{"name":"true","valueType":"boolean","description":""},{"name":"false","valueType":"boolean","description":""}],"config":{"operator":"equals","compareValue":"true"}},
{"id":"work-found","type":"end","position":{"x":1120,"y":100},"nodeMeta":{"title":"结束（存在待办）","icon":"⏹","description":"工作流终点","color":"#ef4444","category":"特殊节点"},"inputs":[{"name":"result","valueType":"any","required":false,"description":"","optional":false,"value":{"type":"ref","nodeId":"has-work","outputName":"true"}}],"outputs":[],"config":{}},
{"id":"no-work","type":"end","position":{"x":1120,"y":260},"nodeMeta":{"title":"结束（无待办）","icon":"⏹","description":"工作流终点","color":"#ef4444","category":"特殊节点"},"inputs":[{"name":"result","valueType":"any","required":false,"description":"","optional":false,"value":{"type":"ref","nodeId":"has-work","outputName":"false"}}],"outputs":[],"config":{}}
],"edges":[{"id":"start-search","sourceNodeId":"start","sourcePortName":"trigger","targetNodeId":"search","targetPortName":"projectId"},{"id":"search-condition","sourceNodeId":"search","sourcePortName":"hasWork","targetNodeId":"has-work","targetPortName":"value"},{"id":"condition-work","sourceNodeId":"has-work","sourcePortName":"true","targetNodeId":"work-found","targetPortName":"result"},{"id":"condition-empty","sourceNodeId":"has-work","sourcePortName":"false","targetNodeId":"no-work","targetPortName":"result"}]}
$pending$
WHERE is_builtin = TRUE AND sort_order = 1;

UPDATE automation_workflow_template
SET name = '人工审批流程',
    description = '验证审批挂起、批准或拒绝分支与恢复执行。',
    category = 'general',
    icon = '🛡️',
    definition = $approval$
{"globalVariables":{},"nodes":[
{"id":"start","type":"start","position":{"x":220,"y":180},"nodeMeta":{"title":"开始","icon":"▶","description":"工作流触发入口","color":"#10b981","category":"特殊节点"},"inputs":[],"outputs":[{"name":"trigger","valueType":"object","description":""}],"config":{}},
{"id":"approval","type":"approval","position":{"x":560,"y":180},"nodeMeta":{"title":"人工审批","icon":"🛡️","description":"高风险操作前暂停并等待审批","color":"#dc2626","category":"控制流"},"inputs":[{"name":"title","valueType":"string","required":true,"description":"","optional":false,"value":{"type":"literal","value":"确认执行自动化操作"}},{"name":"description","valueType":"string","required":false,"description":"","optional":false,"value":{"type":"literal","value":"请确认本次自动化请求的输入和影响范围。"}},{"name":"payload","valueType":"object","required":false,"description":"","optional":false,"value":{"type":"ref","nodeId":"start","outputName":"trigger"}}],"outputs":[{"name":"approved","valueType":"boolean","description":""},{"name":"rejected","valueType":"boolean","description":""},{"name":"comment","valueType":"string","description":""}],"config":{"riskLevel":"medium","expiryHours":24}},
{"id":"approved-end","type":"end","position":{"x":900,"y":100},"nodeMeta":{"title":"结束（批准）","icon":"⏹","description":"工作流终点","color":"#ef4444","category":"特殊节点"},"inputs":[{"name":"result","valueType":"any","required":false,"description":"","optional":false,"value":{"type":"ref","nodeId":"approval","outputName":"approved"}}],"outputs":[],"config":{}},
{"id":"rejected-end","type":"end","position":{"x":900,"y":260},"nodeMeta":{"title":"结束（拒绝）","icon":"⏹","description":"工作流终点","color":"#ef4444","category":"特殊节点"},"inputs":[{"name":"result","valueType":"any","required":false,"description":"","optional":false,"value":{"type":"ref","nodeId":"approval","outputName":"rejected"}}],"outputs":[],"config":{}}
],"edges":[{"id":"start-approval","sourceNodeId":"start","sourcePortName":"trigger","targetNodeId":"approval","targetPortName":"payload"},{"id":"approval-approved","sourceNodeId":"approval","sourcePortName":"approved","targetNodeId":"approved-end","targetPortName":"result"},{"id":"approval-rejected","sourceNodeId":"approval","sourcePortName":"rejected","targetNodeId":"rejected-end","targetPortName":"result"}]}
$approval$
WHERE is_builtin = TRUE AND sort_order = 2;

UPDATE automation_workflow_template
SET name = '延时执行',
    description = '等待五秒后结束，用于验证延时任务的执行与恢复链路。',
    category = 'general',
    icon = '⏱',
    definition = $delay$
{"globalVariables":{},"nodes":[
{"id":"start","type":"start","position":{"x":220,"y":180},"nodeMeta":{"title":"开始","icon":"▶","description":"工作流触发入口","color":"#10b981","category":"特殊节点"},"inputs":[],"outputs":[{"name":"trigger","valueType":"object","description":""}],"config":{}},
{"id":"delay","type":"delay","position":{"x":560,"y":180},"nodeMeta":{"title":"延时等待","icon":"⏱","description":"暂停指定时长后继续","color":"#64748b","category":"控制流"},"inputs":[{"name":"duration","valueType":"number","required":false,"description":"","optional":false,"value":{"type":"literal","value":5}}],"outputs":[{"name":"done","valueType":"boolean","description":""}],"config":{"seconds":5}},
{"id":"end","type":"end","position":{"x":900,"y":180},"nodeMeta":{"title":"结束","icon":"⏹","description":"工作流终点","color":"#ef4444","category":"特殊节点"},"inputs":[{"name":"result","valueType":"any","required":false,"description":"","optional":false,"value":{"type":"ref","nodeId":"delay","outputName":"done"}}],"outputs":[],"config":{}}
],"edges":[{"id":"start-delay","sourceNodeId":"start","sourcePortName":"trigger","targetNodeId":"delay","targetPortName":"duration"},{"id":"delay-end","sourceNodeId":"delay","sourcePortName":"done","targetNodeId":"end","targetPortName":"result"}]}
$delay$
WHERE is_builtin = TRUE AND sort_order = 3;
