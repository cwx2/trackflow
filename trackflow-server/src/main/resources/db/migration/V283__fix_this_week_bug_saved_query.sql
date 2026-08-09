UPDATE saved_query
SET filters = '[{"field":"type","operator":"eq","value":["缺陷"]},{"field":"status","operator":"open","value":[]},{"field":"createdAt","operator":"relative","value":["this_week"]}]'::jsonb
WHERE name = '本周新增 Bug'
  AND filters::text NOT LIKE '%createdAt%';
