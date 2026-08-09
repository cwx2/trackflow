-- Fix automation-created link activity records that display numeric IDs instead of issue keys.
-- The old format was "linkType → numericId", the correct format is "linkType issueKey".

UPDATE issue_activity ia
SET new_value = SPLIT_PART(ia.new_value, ' → ', 1) || ' ' || i.issue_key
FROM issue i
WHERE ia.action = 'link_added'
  AND ia.field_name = 'link'
  AND ia.source = 'automation'
  AND ia.new_value LIKE '%→%'
  AND i.id = CAST(SPLIT_PART(ia.new_value, ' → ', 2) AS BIGINT);
