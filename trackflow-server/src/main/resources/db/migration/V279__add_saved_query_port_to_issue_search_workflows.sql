-- 将已有工作流升级为查找待办需求节点的新正式端口契约。
-- 这是一次持久化定义迁移，不在执行时对缺失端口做兼容猜测。

CREATE OR REPLACE FUNCTION tf_upgrade_issue_search_saved_query_port(definition_json JSONB)
RETURNS JSONB
LANGUAGE SQL
IMMUTABLE
AS $$
    SELECT CASE
        WHEN definition_json IS NULL OR NOT (definition_json ? 'nodes') THEN definition_json
        ELSE jsonb_set(
            definition_json,
            '{nodes}',
            COALESCE((
                SELECT jsonb_agg(
                    CASE
                        WHEN node ->> 'type' = 'trackflow-issue-search'
                             AND NOT EXISTS (
                                SELECT 1
                                FROM jsonb_array_elements(
                                    CASE WHEN jsonb_typeof(node -> 'inputs') = 'array'
                                         THEN node -> 'inputs' ELSE '[]'::jsonb END
                                ) AS input_port
                                WHERE input_port ->> 'name' = 'savedQueryId'
                             )
                        THEN jsonb_set(
                            node,
                            '{inputs}',
                            (CASE WHEN jsonb_typeof(node -> 'inputs') = 'array'
                                  THEN node -> 'inputs' ELSE '[]'::jsonb END)
                            || jsonb_build_array(jsonb_build_object(
                                'name', 'savedQueryId',
                                'label', '完整筛选',
                                'valueType', 'number',
                                'required', false,
                                'description', '完整保存筛选 ID；设置后按保存筛选（含自定义字段）查询',
                                'optional', true,
                                'value', NULL
                            ))
                        )
                        ELSE node
                    END
                )
                FROM jsonb_array_elements(definition_json -> 'nodes') AS node
            ), '[]'::jsonb),
            true
        )
    END;
$$;

UPDATE automation_workflow
SET definition = tf_upgrade_issue_search_saved_query_port(definition),
    published_definition = tf_upgrade_issue_search_saved_query_port(published_definition)
WHERE definition ? 'nodes'
   OR published_definition ? 'nodes';

DROP FUNCTION tf_upgrade_issue_search_saved_query_port(JSONB);
