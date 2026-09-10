-- Add part column to questions table for multi-part video support
ALTER TABLE questions ADD COLUMN IF NOT EXISTS part INTEGER NOT NULL DEFAULT 1;
