-- 将单节课 AI 摘要升级为可编辑、可检索的个人笔记库。
ALTER TABLE notes ALTER COLUMN session_id DROP NOT NULL;
ALTER TABLE notes ADD COLUMN user_id UUID REFERENCES users(id) ON DELETE CASCADE;
ALTER TABLE notes ADD COLUMN title VARCHAR(500) NOT NULL DEFAULT '未命名笔记';
ALTER TABLE notes ADD COLUMN markdown TEXT NOT NULL DEFAULT '';
ALTER TABLE notes ADD COLUMN course_name VARCHAR(500);
ALTER TABLE notes ADD COLUMN chapter_name VARCHAR(500);
ALTER TABLE notes ADD COLUMN source_title VARCHAR(500);
ALTER TABLE notes ADD COLUMN source_url TEXT;
ALTER TABLE notes ADD COLUMN source_timestamp INTEGER;
ALTER TABLE notes ADD COLUMN tags JSONB NOT NULL DEFAULT '[]';
ALTER TABLE notes ADD COLUMN linked_note_ids UUID[] NOT NULL DEFAULT '{}';
ALTER TABLE notes ADD COLUMN content_status VARCHAR(30) NOT NULL DEFAULT 'draft';
ALTER TABLE notes ADD COLUMN mastery_status VARCHAR(30) NOT NULL DEFAULT 'unlearned';
ALTER TABLE notes ADD COLUMN inbox BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE notes ADD COLUMN next_review_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE notes ADD COLUMN study_materials JSONB NOT NULL DEFAULT '{"flashcards":[],"questions":[]}';
ALTER TABLE notes ADD COLUMN updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW();

UPDATE notes n
SET user_id = s.user_id,
    title = COALESCE(s.video_title, n.content_json->>'topic', '课程笔记'),
    markdown = CONCAT(
        '# ', COALESCE(n.content_json->>'topic', s.video_title, '课程笔记'), E'\n\n',
        COALESCE(n.content_json->>'summary', ''), E'\n\n',
        CASE WHEN jsonb_array_length(COALESCE(n.content_json->'keyPoints', '[]')) > 0
          THEN '## 关键概念' || E'\n' || COALESCE((SELECT string_agg('- ' || value, E'\n') FROM jsonb_array_elements_text(n.content_json->'keyPoints')), '')
          ELSE '' END
    ),
    source_title = s.video_title,
    source_url = s.video_url,
    content_status = CASE WHEN n.status = 'done' THEN 'organized' ELSE 'draft' END
FROM sessions s WHERE n.session_id = s.id;

ALTER TABLE notes ALTER COLUMN user_id SET NOT NULL;
CREATE INDEX idx_notes_user_updated ON notes(user_id, updated_at DESC);
CREATE INDEX idx_notes_user_review ON notes(user_id, next_review_at);
CREATE INDEX idx_notes_search ON notes USING GIN (
  to_tsvector('simple', COALESCE(title, '') || ' ' || COALESCE(markdown, '') || ' ' || COALESCE(course_name, '') || ' ' || COALESCE(chapter_name, ''))
);
