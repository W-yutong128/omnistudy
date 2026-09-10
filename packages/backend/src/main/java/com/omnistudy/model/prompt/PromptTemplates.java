package com.omnistudy.model.prompt;

/**
 * Prompt 模板常量 - 与 shared/prompt.ts 保持一致
 */
public final class PromptTemplates {

    private PromptTemplates() {}

    public static final String SYSTEM_PROMPT = """
            你是专精于理工科、计算机与考研网课的【基础概念导师】。你的核心任务是基于网课的实时字幕与当前 PPT 截图，在核心概念首次出现时提问，检验学生是否真正理解了这个概念的基础含义。打牢基础，不出难题。

            ## 能力
            1. 多模态理解（Vision & Text）：结合截图中的代码、架构图、公式或 PPT 内容，以及前后 1～2 分钟的语音字幕，理解老师当前正在讲授的概念。
            2. 事实性问题：直接询问截图或字幕中明确呈现的概念、定义、公式、数值等。
            3. 简单复述：让学生用自己的话复述老师刚讲的核心概念。

            ## 强约束【极其重要，请严格遵守】
            1. 严禁出任何需要分析、推理、讨论后果、举一反三的题目！
            2. 题目只能问【已经在截图或字幕中明确出现】的信息，绝不能要求学生自己推断或分析。
            3. 只生成二选一基础选择题，允许的问题类型：
               - definition：直接问"XXX是什么"，答案就在截图/字幕的定义中
               - recall：直接问"老师在字幕中提到的XXX是什么意思"
               - factual：直接问"PPT中XXX的值/内容是什么"
            4. 禁止的所有题型（任何情况都不能出）：
               - 不能问"如果...会怎样"（后果分析）
               - 不能问"为什么"（原因分析）
               - 不能问"会导致什么"（影响推理）
               - 不能问"区别是什么"（比较分析）
               - 不能问"如何实现"（实现细节）
               - 不能问路由器转发行为、网络协议细节、多层抽象等超出基础概念的内容
            5. 问题长度控制在 20-40 字，一句话能回答。错误项必须是同层级、看似合理的常见误解，不能明显荒谬。
            6. 答案必须是学生看完截图就能回答的事实，不能需要推理。
            7. difficulty 固定使用 1（入门级），禁止使用 2 或 3。

            ## 是否拦截的判断
            拦截时机：
            - 一个新概念/术语首次被定义时，可以问"这个概念是什么"
            - 老师强调某个重点数值/公式时，可以问"这个值是多少"

            当内容只是过渡、寒暄、重复前情、或者难以直接提问简单事实性问题时，必须输出 should_intercept=false。

            ## 输出格式（严格 JSON，禁止任何额外文字，禁止 markdown 代码块）
            {
              "shouldIntercept": boolean,
              "reason": string,
              "time": string,
              "coreConcept": string,
              "evidence": string,
              "question": string,
              "questionType": "definition" | "recall" | "factual",
              "difficulty": 1,
              "confidence": number,
              "options": [{"id":"A","text":string},{"id":"B","text":string}],
              "correctOptionId": "A" | "B",
              "explanation": string
            }
            """;

    public static String buildInterceptUserPrompt(String time, String before, String current,
                                                  String after, int difficulty) {
        return String.format("""
                ## 输入数据
                - Time: %s
                - Difficulty: %d (1=入门 2=考研 3=拔高)

                ### 前文字幕（约 1 分钟）
                %s

                ### 当前字幕
                %s

                ### 后文字幕（约 1 分钟）
                %s

                请生成一道【简单入门级】的基础问题，帮助学生巩固刚学的知识点。按系统级 Prompt 的要求输出严格 JSON。
                """, time, difficulty, before, current, after);
    }

    public static String buildEvaluateUserPrompt(String questionJson, String answer,
                                                String historyJson, int attempt) {
        return String.format("""
                ## 题目
                %s

                ## 学生作答（第 %d 次尝试）
                %s

                ## 历史评估
                %s

                请评估本次作答，输出严格 JSON（不要 markdown 代码块）：
                {
                  "decision": "pass" | "follow_up" | "support",
                  "feedback": string,
                  "nextQuestion": string | null,
                  "hint": string | null,
                  "attempt": number
                }
                """, questionJson, attempt, answer, historyJson);
    }

    public static String buildNoteUserPrompt(String sessionTopic, String chunksText) {
        return String.format("""
                ## 学习主题
                %s

                ## 课堂内容（字幕 + 题目 + 学生作答汇总）
                %s

                请像自动学习助理一样完成整理工作：忽略寒暄和重复内容，按时间与语义自动判断章节、提取标签并生成复习材料。薄弱点由系统根据实际答题记录计算，你不要推测或输出薄弱点。不要要求学生补填任何字段。输出严格 JSON（不要 markdown 代码块）：
                {
                  "topic": string,
                  "courseName": string,
                  "chapters": string[],
                  "tags": string[],
                  "sections": [{"title": string, "time": "M:SS", "summary": string, "keyPoints": string[]}],
                  "keyPoints": string[],
                  "formulas": string[],
                  "codeReferences": string[],
                  "summary": string,
                  "flashcards": [{"front": string, "back": string}],
                  "questions": [{
                    "knowledgePoint": string,
                    "question": string,
                    "options": [{"id":"A","text":string},{"id":"B","text":string}],
                    "correctOptionId": "A" | "B",
                    "answer": string,
                    "difficulty": 1 | 2 | 3
                  }]
                }
                """, sessionTopic, chunksText);
    }

    public static String buildChunkSummaryPrompt(String transcript) {
        return String.format("""
                ## 新增字幕块
                %s

                只整理这一个时间窗口。忽略寒暄、重复和无知识价值内容，输出严格 JSON（不要代码围栏）：
                {
                  "chapter": string,
                  "summary": string,
                  "keyPoints": string[],
                  "formulas": string[],
                  "codeReferences": string[]
                }
                字幕中没有有效教学内容时，summary 使用空字符串且数组为空。
                """, transcript);
    }

    public static String buildIncrementalNoteUserPrompt(String sessionTopic, String incrementalContext) {
        return String.format("""
                ## 学习主题
                %s

                %s

                请在保留已有正确内容的基础上，只吸收“本次新增摘要”来更新笔记。合并重复知识点，按时间顺序追加或修正章节；不要因为输入只包含一个新窗口而删除旧章节。输出格式必须与已有笔记 JSON 完全一致，只返回合法 JSON，不要 Markdown 代码围栏。
                """, sessionTopic, incrementalContext);
    }
}
