import { useEffect, useMemo, useRef, useState } from "react";
import type { Note, WorkspaceNote } from "@omnistudy/shared";

interface Props {
  note: Note | null;
  onGenerate: () => void;
  sessionActive: boolean;
  refreshKey?: number;
  syncStatus?: "idle" | "flushing" | "generating" | "success" | "error";
  syncMessage?: string;
}
type View = "all" | "inbox" | "review";

const emptyDraft = (): Omit<WorkspaceNote, "id" | "createdAt" | "updatedAt"> => ({
  sessionId: null, title: "未命名笔记", markdown: "", courseName: "", chapterName: "",
  sourceTitle: "", sourceUrl: "", sourceTimestamp: null, tags: [], linkedNoteIds: [],
  contentStatus: "draft", masteryStatus: "unlearned", inbox: false, nextReviewAt: null,
  studyMaterials: { flashcards: [], questions: [] },
});

export function NotePanel({ note, onGenerate, sessionActive, refreshKey = 0, syncStatus = "idle", syncMessage }: Props) {
  const [notes, setNotes] = useState<WorkspaceNote[]>([]);
  const [selected, setSelected] = useState<WorkspaceNote | null>(null);
  const [draft, setDraft] = useState<any>(null);
  const [view, setView] = useState<View>("all");
  const [query, setQuery] = useState("");
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [generating, setGenerating] = useState<"flashcards" | "questions" | null>(null);
  const editorRef = useRef<HTMLTextAreaElement>(null);

  useEffect(() => { loadNotes(); }, [view]);
  useEffect(() => { loadNotes(); }, [refreshKey]);
  useEffect(() => { if (note?.status === "done") loadNotes(); }, [note?.status]);

  async function loadNotes(search = query) {
    setLoading(true);
    const res = await chrome.runtime.sendMessage({ type: "notes:list", payload: {
      query: search || undefined, inbox: view === "inbox", review: view === "review",
    }});
    if (res?.success) {
      const loadedNotes = Array.isArray(res.data)
        ? res.data.map(normalizeWorkspaceNote)
        : [];
      setNotes(loadedNotes);
      if (selected) {
        const fresh = loadedNotes.find((n: WorkspaceNote) => n.id === selected.id);
        if (fresh) { setSelected(fresh); setDraft({ ...fresh }); }
      }
    }
    setLoading(false);
  }

  function openNote(n: WorkspaceNote) {
    const normalized = normalizeWorkspaceNote(n);
    setSelected(normalized);
    setDraft({ ...normalized, tags: [...normalized.tags], linkedNoteIds: [...normalized.linkedNoteIds] });
  }
  function newNote(inbox = false) { setSelected(null); setDraft({ ...emptyDraft(), inbox }); }

  async function save() {
    if (!draft) return;
    setSaving(true);
    const message = selected
      ? { type: "notes:update", payload: { id: selected.id, note: draft } }
      : { type: "notes:create", payload: draft };
    const res = await chrome.runtime.sendMessage(message);
    setSaving(false);
    if (!res?.success) return alert("保存失败：" + res?.error);
    setSelected(res.data); setDraft({ ...res.data }); await loadNotes();
  }

  async function remove() {
    if (!selected || !confirm(`删除「${selected.title}」？`)) return;
    const res = await chrome.runtime.sendMessage({ type: "notes:delete", payload: { id: selected.id } });
    if (res?.success) { setSelected(null); setDraft(null); await loadNotes(); }
  }

  async function generateMaterial(kind: "flashcards" | "questions") {
    if (!selected) { alert("请先保存笔记"); return; }
    setGenerating(kind);
    const res = await chrome.runtime.sendMessage({ type: "notes:generate-material", payload: { id: selected.id, kind } });
    setGenerating(null);
    if (!res?.success) return alert("生成失败：" + res?.error);
    setSelected(res.data); setDraft({ ...res.data });
  }

  function insertMarkdown(before: string, after = "") {
    const el = editorRef.current; if (!el) return;
    const start = el.selectionStart, end = el.selectionEnd;
    const value = draft.markdown || "", selectedText = value.slice(start, end) || "输入内容";
    setDraft({ ...draft, markdown: value.slice(0, start) + before + selectedText + after + value.slice(end) });
    requestAnimationFrame(() => { el.focus(); el.setSelectionRange(start + before.length, start + before.length + selectedText.length); });
  }

  const backlinks = useMemo(() => selected ? notes.filter(n => n.linkedNoteIds?.includes(selected.id)) : [], [notes, selected]);

  if (draft) return <Editor draft={draft} setDraft={setDraft} notes={notes} selected={selected}
    saving={saving} generating={generating} backlinks={backlinks} editorRef={editorRef}
    onBack={() => { setDraft(null); setSelected(null); }} onSave={save} onDelete={remove}
    onGenerate={generateMaterial} onInsert={insertMarkdown} />;

  return (
    <div className="flex-1 min-h-0 flex flex-col bg-slate-50">
      <div className="p-3 bg-white border-b space-y-3">
        <div className="flex gap-2">
          <div className="flex-1 flex bg-slate-100 rounded-lg px-3 items-center">
            <span className="text-slate-400">⌕</span>
            <input value={query} onChange={e => setQuery(e.target.value)} onKeyDown={e => e.key === "Enter" && loadNotes()}
              placeholder="搜索标题、正文、课程…" className="w-full bg-transparent px-2 py-2 outline-none text-sm" />
          </div>
          <button onClick={() => newNote(false)} className="px-3 rounded-lg bg-primary-600 text-white text-sm">＋ 新建</button>
        </div>
        <div className="flex gap-1">
          {([['all','全部'],['inbox','收件箱'],['review','今日复习']] as const).map(([key,label]) =>
            <button key={key} onClick={() => setView(key)} className={`px-3 py-1.5 rounded-full text-xs ${view === key ? 'bg-primary-100 text-primary-700 font-medium' : 'text-slate-500 hover:bg-slate-100'}`}>{label}</button>)}
          <div className="flex-1" />
          <button onClick={() => newNote(true)} className="text-xs text-amber-700 bg-amber-50 px-2 rounded-full">⚡ 快速记录</button>
        </div>
      </div>

      <div className="flex-1 overflow-y-auto p-3 space-y-2">
        {sessionActive && <div className="bg-indigo-50 border border-indigo-100 rounded-xl p-3 text-sm text-indigo-700">
          <div className="font-medium">✨ AI 正在自动整理当前课程</div>
          <p className="text-xs text-indigo-500 mt-1">字幕、章节、标签、薄弱点和复习材料都会自动处理。</p>
          <button onClick={onGenerate} disabled={syncStatus === "flushing" || syncStatus === "generating"} className="mt-2 text-xs underline disabled:opacity-50">
            {syncStatus === "flushing" ? "正在上传当前字幕…" : syncStatus === "generating" ? "AI 正在整理…" : "立即刷新笔记"}
          </button>
          {syncMessage && <p className={`text-xs mt-1 ${syncStatus === "error" ? "text-red-600" : "text-indigo-600"}`}>{syncMessage}</p>}
        </div>}
        {loading && <p className="text-center text-slate-400 py-10">加载中…</p>}
        {!loading && notes.length === 0 && <div className="text-center text-slate-400 py-14"><div className="text-4xl mb-3">{view === 'review' ? '☀️' : '📝'}</div><p>{view === 'review' ? '今天没有待复习内容' : '从一条快速记录开始吧'}</p></div>}
        {notes.map(n => <button key={n.id} onClick={() => openNote(n)} className="w-full text-left bg-white border border-slate-100 hover:border-primary-200 rounded-xl p-3 shadow-sm transition">
          <div className="flex items-start gap-2"><h3 className="font-semibold text-slate-800 flex-1 truncate">{n.title}</h3>{n.inbox && <span className="text-[10px] text-amber-700 bg-amber-50 px-1.5 py-0.5 rounded">收件箱</span>}</div>
          <p className="text-xs text-slate-500 line-clamp-2 mt-1">{plainPreview(n.markdown) || '暂无正文'}</p>
          <div className="flex items-center gap-2 mt-2 text-[10px] text-slate-400"><span>{n.courseName || '未分类'}</span>{n.chapterName && <span>· {n.chapterName}</span>}<span className="flex-1"/><span>{masteryLabel[n.masteryStatus]}</span></div>
        </button>)}
      </div>
    </div>
  );
}

function Editor({ draft, setDraft, notes, selected, saving, generating, backlinks, editorRef, onBack, onSave, onDelete, onGenerate, onInsert }: any) {
  const [preview, setPreview] = useState(false);
  const tagsValue = (draft.tags || []).join(", ");
  return <div className="flex-1 min-h-0 flex flex-col bg-white">
    <div className="flex items-center gap-2 px-3 py-2 border-b"><button onClick={onBack} className="text-slate-500">←</button><span className="text-xs text-slate-400 flex-1">{selected ? '编辑笔记' : draft.inbox ? '快速记录' : '新建笔记'}</span>{selected && <button onClick={onDelete} className="text-xs text-red-400">删除</button>}<button onClick={onSave} disabled={saving} className="px-3 py-1.5 bg-primary-600 text-white rounded-lg text-xs disabled:opacity-50">{saving ? '保存中…' : '保存'}</button></div>
    <div className="flex-1 overflow-y-auto">
      <div className="p-4 space-y-3 border-b bg-slate-50/60">
        <input value={draft.title} onChange={e => setDraft({...draft,title:e.target.value})} placeholder="笔记标题" className="w-full text-xl font-bold bg-transparent outline-none" />
        <div className="grid grid-cols-2 gap-2"><Field label="课程" value={draft.courseName || ''} onChange={(v:string)=>setDraft({...draft,courseName:v})}/><Field label="章节" value={draft.chapterName || ''} onChange={(v:string)=>setDraft({...draft,chapterName:v})}/></div>
        <div className="grid grid-cols-2 gap-2">
          <Select label="内容" value={draft.contentStatus} onChange={(v:string)=>setDraft({...draft,contentStatus:v})} options={{draft:'草稿',organizing:'待整理',organized:'已完善'}} />
          <Select label="掌握" value={draft.masteryStatus} onChange={(v:string)=>setDraft({...draft,masteryStatus:v})} options={masteryLabel} />
        </div>
        <Field label="标签（逗号分隔）" value={tagsValue} onChange={(v:string)=>setDraft({...draft,tags:v.split(/[,，]/).map(x=>x.trim()).filter(Boolean)})}/>
        <details className="text-xs text-slate-500"><summary className="cursor-pointer">来源与关联</summary><div className="space-y-2 pt-2"><Field label="来源标题" value={draft.sourceTitle || ''} onChange={(v:string)=>setDraft({...draft,sourceTitle:v})}/><Field label="来源链接" value={draft.sourceUrl || ''} onChange={(v:string)=>setDraft({...draft,sourceUrl:v})}/><label className="block"><span>双向链接</span><select multiple value={draft.linkedNoteIds || []} onChange={e=>setDraft({...draft,linkedNoteIds:Array.from(e.target.selectedOptions).map((o:any)=>o.value)})} className="mt-1 w-full border rounded-lg p-2 bg-white h-20">{notes.filter((n:any)=>n.id!==selected?.id).map((n:any)=><option key={n.id} value={n.id}>{n.title}</option>)}</select></label></div></details>
        <label className="flex items-center gap-2 text-xs text-slate-600"><input type="checkbox" checked={!!draft.inbox} onChange={e=>setDraft({...draft,inbox:e.target.checked})}/>放入待整理收件箱</label>
      </div>
      <div className="sticky top-0 z-10 flex items-center gap-1 px-3 py-2 border-b bg-white">
        {[['H2','## '],['B','**','**'],['•','- '],['☑','- [ ] '],['❝','> '],['{}','```\n','\n```']].map((x:any)=><button key={x[0]} title="插入 Markdown 块" onClick={()=>onInsert(x[1],x[2]||'')} className="w-8 h-7 rounded hover:bg-slate-100 text-xs font-medium">{x[0]}</button>)}
        <div className="flex-1"/><button onClick={()=>setPreview(!preview)} className="text-xs text-primary-600 px-2">{preview ? '编辑' : '预览'}</button>
      </div>
      {preview ? <MarkdownPreview value={draft.markdown}/> : <textarea ref={editorRef} value={draft.markdown} onChange={e=>setDraft({...draft,markdown:e.target.value})} placeholder="用 Markdown 记录知识。输入 ## 创建标题，- [ ] 创建任务…" className="w-full min-h-[320px] resize-none p-4 outline-none font-mono text-sm leading-7"/>}
      <div className="p-4 border-t space-y-3">
        <div className="flex gap-2"><button onClick={()=>onGenerate('flashcards')} disabled={!!generating} className="flex-1 py-2 rounded-lg bg-violet-50 text-violet-700 text-xs">{generating==='flashcards'?'生成中…':'✦ 生成闪卡'}</button><button onClick={()=>onGenerate('questions')} disabled={!!generating} className="flex-1 py-2 rounded-lg bg-blue-50 text-blue-700 text-xs">{generating==='questions'?'生成中…':'✦ 生成练习题'}</button></div>
        <Materials materials={draft.studyMaterials}/>
        <QuestionBankPractice note={draft}/>
        {draft.sourceUrl && <a href={draft.sourceUrl} target="_blank" className="block text-xs text-primary-600 truncate">↗ {draft.sourceTitle || draft.sourceUrl}{draft.sourceTimestamp != null ? ` · ${formatTime(draft.sourceTimestamp)}` : ''}</a>}
        {backlinks.length > 0 && <div className="text-xs text-slate-500">反向链接：{backlinks.map((n:any)=>n.title).join('、')}</div>}
      </div>
    </div>
  </div>;
}

function Field({label,value,onChange}:any){return <label className="block text-[11px] text-slate-500"><span>{label}</span><input value={value} onChange={e=>onChange(e.target.value)} className="mt-1 w-full border border-slate-200 rounded-lg px-2.5 py-2 bg-white text-xs text-slate-700 outline-none focus:border-primary-400"/></label>}
function Select({label,value,onChange,options}:any){return <label className="block text-[11px] text-slate-500"><span>{label}</span><select value={value} onChange={e=>onChange(e.target.value)} className="mt-1 w-full border border-slate-200 rounded-lg px-2 py-2 bg-white text-xs">{Object.entries(options).map(([k,v]:any)=><option key={k} value={k}>{v}</option>)}</select></label>}
function MarkdownPreview({value}:{value:string}){return <div className="p-4 min-h-[320px] text-sm leading-7 text-slate-700">{value.split('\n').map((line,i)=>line.startsWith('# ') ? <h1 key={i} className="text-xl font-bold mt-3">{line.slice(2)}</h1> : line.startsWith('## ') ? <h2 key={i} className="text-lg font-semibold mt-3">{line.slice(3)}</h2> : line.startsWith('- [ ] ') ? <div key={i}>☐ {line.slice(6)}</div> : line.startsWith('- ') ? <div key={i}>• {line.slice(2)}</div> : line ? <p key={i}>{line}</p> : <br key={i}/>)}</div>}
function Materials({materials}:any){
  if(!materials)return null;
  return <div className="space-y-2">
    {materials.flashcards?.map((c:any,i:number)=><details key={'f'+i} className="bg-violet-50 rounded-lg p-2 text-xs"><summary className="cursor-pointer text-violet-800">闪卡 · {c.front}</summary><p className="mt-2 text-slate-600">{c.back}</p></details>)}
    {materials.questions?.map((q:any,i:number)=><ReviewQuestion key={q.id || 'q'+i} question={q}/>)}
  </div>
}

function ReviewQuestion({question:q}:any){
  const [selected,setSelected]=useState<string|null>(null);
  const [result,setResult]=useState<any>(null);
  const [submitting,setSubmitting]=useState(false);
  async function answer(optionId:string){
    if(!q.id||submitting||result)return;
    setSelected(optionId);setSubmitting(true);
    const res=await chrome.runtime.sendMessage({type:"review:answer",payload:{questionId:q.id,optionId}});
    setSubmitting(false);
    if(!res?.success)return alert("提交失败："+res?.error);
    setResult(res.data);
  }
  return <div className="bg-blue-50 rounded-lg p-3 text-xs space-y-2">
    <div className="text-blue-900 font-medium">练习 · {q.question}</div>
    {q.knowledgePoint&&<div className="text-[10px] text-blue-500">知识点：{q.knowledgePoint}</div>}
    {q.options?.map((o:any)=><button key={o.id} onClick={()=>answer(o.id)} disabled={!q.id||submitting||!!result}
      className={`block w-full text-left border rounded-lg px-2.5 py-2 ${selected===o.id?'border-blue-500 bg-white':'border-blue-100 bg-blue-50/50'} disabled:opacity-70`}>
      {o.id}. {o.text}
    </button>)}
    {!q.id&&<p className="text-amber-600">旧版题目仅供查看，请重新生成后作答。</p>}
    {result&&<div className={`rounded-lg p-2 ${result.correct?'bg-emerald-50 text-emerald-700':'bg-amber-50 text-amber-700'}`}>
      <div className="font-medium">{result.correct?'回答正确':'回答错误'} · 正确答案 {result.correctOptionId}</div>
      <p className="mt-1">{result.explanation}</p>
      {result.masteryScore!=null&&<p className="mt-1 text-[10px]">掌握度 {Math.round(result.masteryScore*100)}% · 下次复习 {formatReviewDate(result.nextReviewAt)}</p>}
    </div>}
  </div>
}

function QuestionBankPractice({note}:any){
  const [open,setOpen]=useState(false);
  const [query,setQuery]=useState((note.tags||[])[0]||note.title||"");
  const [subject,setSubject]=useState("");
  const [items,setItems]=useState<any[]>([]);
  const [loading,setLoading]=useState(false);
  const [delivered,setDelivered]=useState<any>(null);
  async function search(){
    setLoading(true);setDelivered(null);
    const res=await chrome.runtime.sendMessage({type:"question-bank:search",payload:{query:query||undefined,subject:subject||undefined,limit:10}});
    setLoading(false);
    if(!res?.success)return alert("检索失败："+res?.error);
    setItems(res.data||[]);
  }
  async function deliver(item:any){
    setLoading(true);
    const res=await chrome.runtime.sendMessage({type:"question-bank:deliver",payload:{id:item.id}});
    setLoading(false);
    if(!res?.success)return alert("加载失败："+res?.error);
    setDelivered({...res.data,knowledgePoint:item.knowledgeTags?.[0]});
  }
  return <div className="border border-emerald-100 rounded-xl p-3 text-xs space-y-2">
    <button onClick={()=>setOpen(!open)} className="w-full flex items-center text-left text-emerald-700 font-medium"><span className="flex-1">◎ 408 真题实战</span><span>{open?'收起':'检索新题'}</span></button>
    {open&&<>
      <div className="flex gap-1"><input value={query} onChange={e=>setQuery(e.target.value)} onKeyDown={e=>e.key==='Enter'&&search()} placeholder="知识点，如 TCP、Cache" className="min-w-0 flex-1 border rounded-lg px-2 py-1.5 outline-none"/><select value={subject} onChange={e=>setSubject(e.target.value)} className="border rounded-lg px-1 bg-white"><option value="">全部科目</option><option>数据结构</option><option>计算机组成原理</option><option>操作系统</option><option>计算机网络</option></select><button onClick={search} disabled={loading} className="px-2 rounded-lg bg-emerald-600 text-white">{loading?'…':'检索'}</button></div>
      {delivered?<ReviewQuestion question={delivered}/>:<div className="space-y-1.5">{items.map(item=><button key={item.id} onClick={()=>deliver(item)} disabled={loading} className="w-full text-left bg-emerald-50 hover:bg-emerald-100 rounded-lg p-2"><div className="text-emerald-800 line-clamp-2">{item.questionText}</div><div className="text-[10px] text-emerald-500 mt-1">{item.sourceYear} 真题 · {item.subject} · {item.knowledgeTags?.join(' / ')}</div></button>)}{!loading&&items.length===0&&<p className="text-slate-400 text-center py-2">输入知识点检索尚未做过的真题</p>}</div>}
    </>}
  </div>
}
const masteryLabel:any={unlearned:'未学习',learning:'理解中',practicing:'待练习',mastered:'已掌握',review:'需复习'};
function normalizeWorkspaceNote(note: Partial<WorkspaceNote>): WorkspaceNote {
  return {
    ...emptyDraft(),
    ...note,
    id: note.id || "",
    tags: Array.isArray(note.tags) ? note.tags : [],
    linkedNoteIds: Array.isArray(note.linkedNoteIds) ? note.linkedNoteIds : [],
    studyMaterials: {
      flashcards: Array.isArray(note.studyMaterials?.flashcards) ? note.studyMaterials.flashcards : [],
      questions: Array.isArray(note.studyMaterials?.questions) ? note.studyMaterials.questions : [],
    },
    createdAt: note.createdAt || "",
    updatedAt: note.updatedAt || "",
  };
}
const plainPreview=(v:string)=>v.replace(/[#>*`\[\]-]/g,' ').replace(/\s+/g,' ').trim();
const formatTime=(s:number)=>`${Math.floor(s/60)}:${String(s%60).padStart(2,'0')}`;
const formatReviewDate=(v?:string)=>v?new Date(v).toLocaleDateString('zh-CN',{month:'numeric',day:'numeric'}):'待安排';
