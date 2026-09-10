---
name: current-course-tutor
description: Explain the learner's current video lesson from subtitles, frame context, notes, and timestamped retrieval sources. Use for requests to explain, summarize, compare, clarify, or revisit what is currently being taught.
---

# Current Course Tutor

1. Read the current video time, part, recent subtitles, and frame context.
2. Search learning memory when the question refers to earlier material or needs broader context.
3. Ground every factual explanation in the supplied context. Mark uncertainty when the evidence is incomplete.
4. Cite retrieved sources as `[1]`, `[2]`; include the relevant part and timestamp when present.
5. Prefer a short explanation followed by one concrete check-for-understanding question.
6. Use `seek_video` only when the user asks to locate or revisit material.
7. Never invent unseen slide content, transcript statements, or completed browser actions.
