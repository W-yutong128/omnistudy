-- Older versions stored M:SS timestamps with Float.parseFloat, which made every
-- value containing ':' fall back to zero. Recover those rows from prompt_json.
UPDATE questions
SET t = split_part(prompt_json ->> 'time', ':', 1)::real * 60
      + split_part(prompt_json ->> 'time', ':', 2)::real
WHERE t = 0
  AND (prompt_json ->> 'time') ~ '^[0-9]+:[0-9]{2}(\.[0-9]+)?$';
