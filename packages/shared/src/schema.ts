/**
 * 所有结构化类型的 TypeScript 定义
 * 两端共用：扩展前端 + Spring Boot 后端
 */

// ==================== 拦截相关 ====================

export interface InterceptInput {
  sessionId: string;
  time: string;
  screenshot: string; // base64 JPEG
  before: string;
  current: string;
  after: string;
  difficulty: number; // 1=入门 2=考研 3=拔高
  part: number; // 分P编号，从1开始
}

export interface InterceptResult {
  id?: string;
  shouldIntercept: boolean;
  reason?: string;
  time: string;
  part: number; // 分P编号，从1开始
  coreConcept: string;
  evidence: string;
  question: string;
  questionType:
    | "definition"
    | "recall"
    | "factual"
    | "practice"
    | "failure_scenario"
    | "counter_intuitive"
    | "boundary_condition"
    | "code_mutation"
    | "analogy";
  difficulty: number;
  confidence: number; // 0~1
  options: Array<{ id: string; text: string }>;
  correctOptionId?: string; // 仅答题后由后端返回，正常出题响应不会暴露
  explanation?: string;
}

// ==================== 评估相关 ====================

export interface EvaluateInput {
  sessionId: string;
  attempt: number;
  question: InterceptResult;
  answer: string;
  history: EvaluateResult[];
}

export interface EvaluateResult {
  decision: "pass" | "follow_up" | "support";
  feedback: string;
  nextQuestion?: string; // decision=follow_up 时
  hint?: string; // decision=support 时
  attempt: number;
}

// ==================== 笔记相关 ====================

export type NoteStatus = "generating" | "done" | "failed";

export interface Note {
  id: string;
  sessionId: string;
  content: NoteContent | null;
  generatedAt: string | null;
  status: NoteStatus;
  contentStatus: NoteContentStatus;
  error?: string | null;
}

export interface NoteContent {
  topic: string;
  keyPoints: string[];
  formulas: string[];
  codeReferences: string[];
  summary: string;
  myWeakPoints: string[];
}

export type NoteContentStatus = "draft" | "organizing" | "organized";
export type NoteMasteryStatus = "unlearned" | "learning" | "practicing" | "mastered" | "review";

export interface Flashcard { front: string; back: string; }
export interface PracticeQuestion {
  id?: string;
  knowledgePoint?: string;
  knowledgePointId?: string;
  question: string;
  options?: Array<{ id: string; text: string }>;
  correctOptionId?: string;
  answer: string;
  difficulty?: string | number;
}

export interface QuestionBankItem {
  id: string;
  sourceDocument: string;
  sourceYear: number;
  sourceExam: string;
  sourceQuestionNo: number;
  sourcePage: number;
  subject: "数据结构" | "计算机组成原理" | "操作系统" | "计算机网络";
  questionType: "single_choice";
  questionText: string;
  options: Array<{ id: string; text: string }>;
  knowledgeTags: string[];
  assetPaths: Array<Record<string, unknown>>;
  difficulty: number;
  licenseStatus: string;
  reviewStatus: string;
}

export interface WorkspaceNote {
  id: string;
  sessionId: string | null;
  title: string;
  markdown: string;
  courseName: string | null;
  chapterName: string | null;
  sourceTitle: string | null;
  sourceUrl: string | null;
  sourceTimestamp: number | null;
  tags: string[];
  linkedNoteIds: string[];
  contentStatus: NoteContentStatus;
  masteryStatus: NoteMasteryStatus;
  inbox: boolean;
  nextReviewAt: string | null;
  studyMaterials: { flashcards: Flashcard[]; questions: PracticeQuestion[] };
  createdAt: string;
  updatedAt: string;
}

// ==================== 知识库相关 ====================

export type Mastery = "陌生的" | "模糊的" | "掌握的";

export interface KnowledgePoint {
  id: string;
  name: string;
  normalizedName: string;
  mastery: Mastery;
  firstSeen: string;
  lastReviewed: string;
  masteryScore: number;
  nextReviewAt: string;
  reviewIntervalDays: number;
  correctStreak: number;
  sources: string[];
  noteCount: number;
}

// ==================== 会话相关 ====================

export interface Session {
  id: string;
  courseId: string;
  courseTitle: string;
  platform: string;
  videoUrl: string;
  startedAt: string;
  endedAt: string | null;
  questionCount: number;
  note: Note | null;
}

// ==================== Auth 相关 ====================

export interface AuthResponse {
  token: string;
  userId: string;
  expiresAt: string;
}

// ==================== 模型配置与用量 ====================

export type AiConnectionStatus = "NOT_CONFIGURED" | "UNVERIFIED" | "VERIFIED" | "FAILED";

export interface AiProviderSettings {
  configured: boolean;
  source: "USER" | "NONE";
  provider: string;
  baseUrl: string;
  maskedApiKey?: string;
  fastVisionModel: string;
  strongTextModel: string;
  connectionStatus: AiConnectionStatus;
  lastVerifiedAt?: string;
  lastTestError?: string;
}

export interface FeatureUsage {
  feature: string;
  requests: number;
  inputTokens: number;
  outputTokens: number;
  cachedTokens: number;
  totalTokens: number;
  estimatedCostMicros: number;
  succeeded: number;
  failed: number;
}

export interface UserUsageOverview {
  periodStart: string;
  requests: number;
  inputTokens: number;
  outputTokens: number;
  cachedTokens: number;
  totalTokens: number;
  estimatedCostMicros: number;
  succeeded: number;
  failed: number;
  features: FeatureUsage[];
}

export interface AgentTrace {
  id: string;
  state: string;
  tool?: string;
  skill?: string;
  model?: string;
  promptVersion: string;
  framework: string;
  inputTokens: number;
  outputTokens: number;
  cachedTokens: number;
  steps: number;
  latencyMs: number;
  success: boolean;
  error?: string;
  createdAt: string;
}

// ==================== 通用 ====================

export type ApiResponse<T> = {
  success: boolean;
  data?: T;
  error?: string;
};

export type QuestionType = InterceptResult["questionType"];
export type Decision = EvaluateResult["decision"];

// ==================== 学习 Agent ====================

export interface AgentChatInput {
  sessionId: string;
  message: string;
  currentTime: number;
  part: number;
  before: string;
  current: string;
  after: string;
  screenshot?: string;
}

export interface AgentSource {
  title: string;
  snippet: string;
  part?: number;
  startTime?: number;
}

export interface AgentPractice {
  question: string;
  options: Array<{ id: string; text: string }>;
  correctOptionId: string;
  explanation: string;
}

export interface AgentChatResult {
  reply: string;
  tool: "explain_current" | "search_learning_memory" | "create_practice" | "seek_video" | "get_review_plan" | "request_open_idea" | "respond";
  skill: "current-course-tutor" | "review-coach" | "coding-course-coach";
  sources: AgentSource[];
  seekTo?: number;
  practice?: AgentPractice;
  ideHandoff?: { reason: string; suggestedFile?: string };
}
