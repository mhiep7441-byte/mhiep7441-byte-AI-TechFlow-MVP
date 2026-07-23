import { useEffect, useMemo, useState } from 'react';
import { Navigate, NavLink, Route, Routes, useLocation } from 'react-router-dom';
import {
  CalendarDays, CheckCircle2, ChevronRight, CirclePlay, Clock3, LayoutDashboard,
  Clapperboard, Globe2, ListTodo, Plus, Search, ShieldCheck, Sparkles, Trash2,
  RefreshCw, TrendingUp, UserRound, Video, WandSparkles, X, Layers3, Bot,
} from 'lucide-react';
import { useTechFlowData } from './hooks/useTechFlowData';

const emptyTask = {
  title: '', description: '', topic: '', priority: 'MEDIUM', status: 'TODO', dueDate: '',
  visualStyle: '', characterDescription: '', researchSources: '', targetDurationSeconds: 60,
};
const emptyPublication = { taskId: '', platform: 'TIKTOK', status: 'PENDING', scheduledAt: '', note: '' };
const emptyCampaign = {
  name: '', theme: '', description: '', episodeCount: 5, targetDurationSeconds: 60,
  visualStyle: '', characterDescription: '', status: 'PLANNING',
};
const labels = { TODO: 'Cần làm', IN_PROGRESS: 'Đang làm', GENERATING: 'Đang tạo', DRAFT_REQUIRES_REVIEW: 'Chờ duyệt', DONE: 'Hoàn tất', FAILED: 'Thất bại' };
const publicationLabels = { PENDING: 'Đang lên lịch', READY: 'Sẵn sàng', PUBLISHED: 'Đã đăng', FAILED: 'Thất bại' };
const statuses = Object.keys(labels);
const lanes = [
  { id: 'plan', title: 'Kế hoạch', hint: 'Ý tưởng và việc cần làm', statuses: ['TODO'], color: 'slate' },
  { id: 'production', title: 'Đang sản xuất', hint: 'Nội dung đang được xử lý', statuses: ['IN_PROGRESS', 'GENERATING'], color: 'cyan' },
  { id: 'review', title: 'Duyệt & hoàn tất', hint: 'Kiểm tra trước khi xuất bản', statuses: ['DRAFT_REQUIRES_REVIEW', 'DONE', 'FAILED'], color: 'violet' },
];
const pageMeta = {
  '/': ['Tổng quan nội dung', 'Theo dõi toàn bộ quy trình sản xuất trong một nơi.'],
  '/pipeline': ['Video pipeline', 'Kiểm soát tiến độ từ ý tưởng đến bản nháp.'],
  '/videos': ['Thư viện video', 'Xem lại, duyệt và quản lý các bản nháp video.'],
  '/trends': ['Xu hướng công nghệ', 'Nguồn ý tưởng được cập nhật để bạn kiểm chứng và sản xuất.'],
  '/campaigns': ['Campaign & series', 'Xây chuỗi nội dung nhất quán thành nhiều tập.'],
  '/calendar': ['Lịch nội dung', 'Lên lịch xuất bản sau khi video đã được duyệt.'],
};

function TaskCard({ task, actions }) {
  const remove = () => window.confirm('Xóa công việc này?') && actions.deleteTask(task.id);
  const needsReview = task.status === 'DRAFT_REQUIRES_REVIEW';
  return <article>
    <div className="card-top"><span className={`priority ${task.priority}`}>{task.priority}</span><button className="icon" onClick={remove} aria-label="Xóa"><Trash2 /></button></div>
    <h3>{task.title}</h3>
    {task.description && <p>{task.description}</p>}
    {task.topic && <div className="topic"><Sparkles />{task.topic}</div>}
    {(task.campaignId || task.targetDurationSeconds || task.aiProvider) && <div className="task-metadata">
      {task.campaignId && <span><Layers3 />Tập {task.episodeNumber}</span>}
      <span><Clock3 />{task.targetDurationSeconds || 60} giây</span>
      {task.aiProvider && <span><Bot />{task.aiProvider}</span>}
    </div>}
    {(task.visualStyle || task.characterDescription || task.researchSources) && <div className="creative-brief" aria-label="Tóm tắt định hướng video">
      {task.visualStyle && <span><Clapperboard />{task.visualStyle}</span>}
      {task.characterDescription && <span><UserRound />{task.characterDescription}</span>}
      {task.researchSources && <span><Globe2 />{task.researchSources.split(/\r?\n/).filter(Boolean).length} nguồn nghiên cứu</span>}
    </div>}
    {task.errorMessage && <div className="error">{task.errorMessage}</div>}
    {task.outputPath && <div className="video-result">
      <div className="preview-head"><span><Video />BẢN XEM TRƯỚC</span><strong>{needsReview ? 'CẦN DUYỆT' : labels[task.status]}</strong></div>
       {task.qualityScore != null && <div className={`quality-badge ${task.qualityStatus === 'PASS' ? 'pass' : 'needs-review'}`} role="status"><ShieldCheck />Quality Gate: {task.qualityScore}/100 · {task.qualityStatus === 'PASS' ? 'Đạt' : 'Cần kiểm tra'}</div>}
       <video controls preload="metadata" src={task.outputPath} />
      <a href={task.outputPath} target="_blank" rel="noreferrer"><CirclePlay />Xem hoặc tải video</a>
    </div>}
    {needsReview && <div className="review-notice" role="status"><ShieldCheck /><span><b>Bản nháp an toàn</b><small>Kiểm tra cảnh, nhân vật, phụ đề và nguồn trước khi xuất bản.</small></span></div>}
    <footer><span>{task.dueDate ? <><CalendarDays />{task.dueDate}</> : 'Chưa có hạn'}</span><select value={task.status} disabled={task.status === 'GENERATING' || needsReview} onChange={(event) => actions.updateTask(task, event.target.value)}>{statuses.filter((status) => status !== 'GENERATING').map((status) => <option key={status} value={status}>{labels[status]}</option>)}</select></footer>
    {needsReview && <button className="review-confirm" onClick={() => window.confirm('Bạn đã xem kỹ video và xác nhận nội dung?') && actions.confirmReview(task)}><CheckCircle2 />Xác nhận đã duyệt</button>}
    {task.topic && task.status !== 'GENERATING' && !needsReview && <button className="generate" onClick={() => actions.generateDraft(task.id)}><CirclePlay />{task.outputPath ? 'Tạo lại video' : 'Tạo video nháp'}</button>}
  </article>;
}

function TaskBoard({ tasks, actions }) {
  const [query, setQuery] = useState('');
  const shown = useMemo(() => tasks.filter((task) => `${task.title} ${task.topic || ''}`.toLowerCase().includes(query.toLowerCase())), [tasks, query]);
  return <section className="workspace"><div className="section-head"><div><p>QUY TRÌNH NỘI DUNG</p><h2>Bảng công việc</h2></div><div className="toolbar"><div className="search"><Search /><input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Tìm công việc..." /></div><span>{shown.length} công việc</span></div></div><section className="board">{lanes.map((lane) => { const items = shown.filter((task) => lane.statuses.includes(task.status)); return <div className={`column ${lane.color}`} key={lane.id}><div className="column-title"><div><i /><span><b>{lane.title}</b><small>{lane.hint}</small></span></div><strong>{items.length}</strong></div><div className="cards">{items.map((task) => <TaskCard key={task.id} task={task} actions={actions} />)}{!items.length && <div className="lane-empty"><span><Plus /></span><b>Chưa có công việc</b><small>{lane.id === 'plan' ? 'Tạo công việc đầu tiên để bắt đầu.' : 'Công việc sẽ xuất hiện tại đây.'}</small></div>}</div></div>; })}</section></section>;
}

function Overview({ tasks, stats, actions, openTask }) {
  return <><section className="hero-card"><div className="hero-icon"><WandSparkles /></div><div><small>BẮT ĐẦU NHANH</small><h2>Tạo video công nghệ tiếp theo</h2><p>Nhập chủ đề, duyệt kịch bản và xuất video dọc 1080×1920.</p></div><button onClick={openTask}>Tạo nội dung <ChevronRight /></button></section><section className="stats"><div><span className="stat-icon blue"><ListTodo /></span><small>Tổng công việc</small><strong>{stats.total}</strong><em>Tất cả nội dung</em></div><div><span className="stat-icon cyan"><Clock3 /></span><small>Đang xử lý</small><strong>{stats.processing}</strong><em>Trong pipeline</em></div><div><span className="stat-icon amber"><Video /></span><small>Chờ duyệt</small><strong>{stats.reviewing}</strong><em>Cần bạn kiểm tra</em></div><div><span className="stat-icon green"><CheckCircle2 /></span><small>Hoàn tất</small><strong>{stats.completed}</strong><em>Sẵn sàng sử dụng</em></div></section><TaskBoard tasks={tasks} actions={actions} /></>;
}

function normalizeTrend(value, index) {
  if (typeof value === 'string') return { id: `trend-${index}`, topic: value, title: value };
  const topic = value?.topic || value?.query || value?.name || value?.title || value?.headline || '';
  return {
    id: value?.id || value?.slug || `trend-${index}`,
    topic: String(topic).trim(),
    title: String(value?.headline || value?.title || topic).trim(),
    summary: value?.summary || value?.description || value?.reason || '',
    source: value?.sourceUrl || value?.url || (String(value?.source || '').startsWith('http') ? value.source : ''),
    score: value?.score ?? value?.relevance ?? value?.heat ?? null,
    tags: Array.isArray(value?.tags) ? value.tags : [],
  };
}

function TrendsView({ onCreateTask }) {
  const [trends, setTrends] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [warnings, setWarnings] = useState([]);
  const load = async () => {
    setLoading(true); setError('');
    try {
      const response = await fetch('/api/trends');
      if (!response.ok) throw new Error((await response.text()) || `HTTP ${response.status}`);
      const payload = await response.json();
      const rows = Array.isArray(payload) ? payload : (payload?.trends || payload?.topics || payload?.items || payload?.results || payload?.data || []);
      setWarnings(Array.isArray(payload?.warnings) ? payload.warnings.filter(Boolean) : []);
      setTrends(rows.map(normalizeTrend).filter((trend) => trend.topic));
    } catch (reason) {
      setError(`Không thể tải xu hướng: ${reason.message}`);
    } finally { setLoading(false); }
  };
  useEffect(() => { load(); }, []);
  return <section className="workspace trends-view">
    <div className="section-head"><div><p>RESEARCH AGENT</p><h2>Ý tưởng đang được quan tâm</h2></div><button className="secondary-action" onClick={load} disabled={loading}><RefreshCw className={loading ? 'spin' : ''} />Cập nhật</button></div>
    {loading && <div className="trends-state"><div className="loader" /><p>Đang lấy xu hướng từ nguồn research...</p></div>}
    {!loading && error && <div className="trends-state trends-error" role="alert"><TrendingUp /><h3>Chưa tải được xu hướng</h3><p>{error}</p><button className="primary" onClick={load}>Thử lại</button></div>}
    {!loading && !error && !trends.length && <div className="trends-state"><TrendingUp /><h3>Chưa có xu hướng</h3><p>Research Agent chưa trả về dữ liệu. Bạn vẫn có thể tạo chủ đề thủ công.</p><button className="primary" onClick={() => onCreateTask('')}>Tạo công việc</button></div>}
    {!loading && !error && warnings.length > 0 && <div className="trends-warning" role="status"><ShieldCheck /><span>{warnings[0]}</span></div>}
    {!loading && !error && trends.length > 0 && <div className="trend-grid">{trends.map((trend) => <article className="trend-card" key={trend.id}><div className="trend-card-top"><span><TrendingUp />ĐỀ XUẤT</span>{trend.score !== null && <strong>{trend.score}</strong>}</div><h3>{trend.title || trend.topic}</h3>{trend.summary && <p>{trend.summary}</p>}<div className="trend-topic">{trend.topic}</div>{trend.tags.length > 0 && <div className="trend-tags">{trend.tags.slice(0, 4).map((tag) => <span key={tag}>#{String(tag).replace(/^#/, '')}</span>)}</div>}<div className="trend-card-foot">{trend.source ? <a href={trend.source} target="_blank" rel="noreferrer">Mở nguồn</a> : <span>Chưa có nguồn</span>}<button className="generate" onClick={() => onCreateTask(trend.topic)}><Plus />Tạo công việc</button></div></article>)}</div>}
  </section>;
}

function CalendarView({ items, actions, openPublication }) {
  const grouped = useMemo(() => items.reduce((result, item) => { const day = item.scheduledAt ? item.scheduledAt.slice(0, 10) : 'Chưa lên ngày'; (result[day] ||= []).push(item); return result; }, {}), [items]);
  return <section className="workspace calendar-view"><div className="section-head"><div><p>LỊCH XUẤT BẢN</p><h2>Kế hoạch nội dung</h2></div><button className="primary" onClick={openPublication}><Plus />Thêm lịch đăng</button></div><div className="calendar-list">{Object.entries(grouped).map(([day, rows]) => <div className="calendar-day" key={day}><div className="date-box"><CalendarDays /><b>{day}</b><small>{rows.length} nội dung</small></div><div className="schedule-items">{rows.map((item) => <article className="schedule-card" key={item.id}><div><span className={`platform ${item.platform}`}>{item.platform}</span><h3>{item.taskTitle}</h3><p>{item.note || 'Không có ghi chú'}</p></div><div className="schedule-meta"><span className={`publication-status ${item.status}`}>{publicationLabels[item.status]}</span><time>{item.scheduledAt ? item.scheduledAt.slice(11, 16) : '--:--'}</time><button className="icon" onClick={() => window.confirm('Xóa lịch đăng này?') && actions.deletePublication(item.id)}><Trash2 /></button></div></article>)}</div></div>)}{!items.length && <div className="big-empty"><CalendarDays /><h3>Chưa có lịch xuất bản</h3><p>Thêm lịch đăng sau khi video đã được bạn duyệt.</p><button className="primary" onClick={openPublication}><Plus />Tạo lịch đầu tiên</button></div>}</div></section>;
}

function CampaignView({ campaigns, tasks, actions, openCampaign }) {
  const taskCount = (campaignId) => tasks.filter((task) => task.campaignId === campaignId).length;
  return <section className="workspace campaigns-view">
    <div className="section-head"><div><p>CONTENT ENGINE</p><h2>Campaign và series nhiều tập</h2></div><button className="primary" onClick={openCampaign}><Plus />Tạo campaign</button></div>
    <div className="campaign-intro"><Layers3 /><div><b>Một chủ đề, nhiều góc kể chuyện</b><span>Gemini hoặc OpenAI viết từng tập theo thời lượng mục tiêu. Mỗi video vẫn qua Quality Gate và bước duyệt thủ công.</span></div></div>
    <div className="campaign-grid">{campaigns.map((campaign) => {
      const generated = taskCount(campaign.id);
      return <article className="campaign-card" key={campaign.id}>
        <div className="campaign-top"><span className={`campaign-status ${campaign.status}`}>{campaign.status}</span><button className="icon" onClick={() => window.confirm('Xóa campaign? Các video đã tạo vẫn được giữ lại.') && actions.deleteCampaign(campaign.id)}><Trash2 /></button></div>
        <h3>{campaign.name}</h3><p>{campaign.description || campaign.theme}</p>
        <div className="campaign-theme">{campaign.theme}</div>
        <div className="campaign-stats"><span><Layers3 /><b>{campaign.episodeCount}</b> tập</span><span><Clock3 /><b>{campaign.targetDurationSeconds}</b> giây/tập</span><span><Video /><b>{generated}</b> đã tạo</span></div>
        <button className="generate" disabled={generated > 0} onClick={() => actions.createCampaignEpisodes(campaign.id)}><WandSparkles />{generated > 0 ? 'Đã tạo danh sách tập' : 'Tạo toàn bộ tập'}</button>
      </article>;
    })}{!campaigns.length && <div className="big-empty campaign-empty"><Layers3 /><h3>Chưa có campaign</h3><p>Tạo một series để biến chủ đề lớn thành chuỗi video có cấu trúc.</p><button className="primary" onClick={openCampaign}><Plus />Tạo campaign đầu tiên</button></div>}</div>
  </section>;
}

function CampaignModal({ onClose, onSave }) {
  const [form, setForm] = useState(emptyCampaign);
  const submit = async (event) => {
    event.preventDefault();
    await onSave({
      ...form,
      name: form.name.trim(),
      theme: form.theme.trim(),
      episodeCount: Number(form.episodeCount),
      targetDurationSeconds: Number(form.targetDurationSeconds),
    });
    onClose();
  };
  return <div className="modal-bg"><form className="modal" onSubmit={submit}>
    <button type="button" className="close" onClick={onClose}><X /></button><p>CAMPAIGN MỚI</p><h2>Xây series nội dung</h2>
    <label>Tên campaign<input required maxLength="160" value={form.name} onChange={(event) => setForm({ ...form, name: event.target.value })} placeholder="Ví dụ: 7 ngày làm chủ AI Agent" /></label>
    <label>Chủ đề xuyên suốt<input required maxLength="500" value={form.theme} onChange={(event) => setForm({ ...form, theme: event.target.value })} placeholder="Kiến thức và cách ứng dụng AI Agent cho người mới" /></label>
    <label>Mô tả series<textarea rows="3" maxLength="2000" value={form.description} onChange={(event) => setForm({ ...form, description: event.target.value })} /></label>
    <div className="brief-grid"><label>Số tập<input required type="number" min="1" max="30" value={form.episodeCount} onChange={(event) => setForm({ ...form, episodeCount: event.target.value })} /></label><label>Thời lượng mỗi tập<select value={form.targetDurationSeconds} onChange={(event) => setForm({ ...form, targetDurationSeconds: event.target.value })}><option value="60">60 giây</option><option value="90">90 giây</option><option value="180">3 phút</option><option value="300">5 phút</option><option value="600">10 phút</option></select></label></div>
    <div className="brief-grid"><label>Phong cách hình ảnh<input maxLength="240" value={form.visualStyle} onChange={(event) => setForm({ ...form, visualStyle: event.target.value })} placeholder="Editorial 2D, chuyển cảnh neon..." /></label><label>Nhân vật xuyên suốt<input maxLength="240" value={form.characterDescription} onChange={(event) => setForm({ ...form, characterDescription: event.target.value })} placeholder="Host nữ công nghệ, áo xanh..." /></label></div>
    <button className="primary submit"><Layers3 />Tạo campaign</button>
  </form></div>;
}

function TaskModal({ onClose, onSave, initialTopic = '' }) {
  const [form, setForm] = useState(() => ({ ...emptyTask, topic: initialTopic }));
  const [validationError, setValidationError] = useState('');
  const submit = async (event) => {
    event.preventDefault();
    const sourceLines = form.researchSources.split(/\r?\n/).map((source) => source.trim()).filter(Boolean);
    const invalidSource = sourceLines.find((source) => {
      try { const parsed = new URL(source); return !['http:', 'https:'].includes(parsed.protocol); } catch { return true; }
    });
    if (invalidSource) {
      setValidationError(`Nguồn nghiên cứu không hợp lệ: ${invalidSource}`);
      return;
    }
    setValidationError('');
    await onSave({ ...form, title: form.title.trim(), topic: form.topic.trim(), dueDate: form.dueDate || null });
    onClose();
  };
  return <div className="modal-bg"><form className="modal" onSubmit={submit}><button type="button" className="close" onClick={onClose}><X /></button><p>CÔNG VIỆC MỚI</p><h2>Lên kế hoạch nội dung</h2>
    <label>Tiêu đề<input required maxLength="160" value={form.title} onChange={(event) => setForm({ ...form, title: event.target.value })} placeholder="Ví dụ: 3 cách dùng Codex hiệu quả" /></label>
    <label>Chủ đề video<input required maxLength="500" value={form.topic} onChange={(event) => setForm({ ...form, topic: event.target.value })} placeholder="Ví dụ: AI agent kiểm thử mã nguồn" /><small className="field-hint">Chủ đề là dữ liệu đầu vào để AI research và viết kịch bản.</small></label>
    <label>Mô tả<textarea rows="3" maxLength="2000" value={form.description} onChange={(event) => setForm({ ...form, description: event.target.value })} /></label>
    <div className="brief-grid"><label>Phong cách hình ảnh<input maxLength="240" value={form.visualStyle} onChange={(event) => setForm({ ...form, visualStyle: event.target.value })} placeholder="Studio neon, motion graphics..." /></label><label>Nhân vật / host<input maxLength="240" value={form.characterDescription} onChange={(event) => setForm({ ...form, characterDescription: event.target.value })} placeholder="Nữ host công nghệ, thân thiện..." /></label></div>
    <label>Nguồn nghiên cứu<textarea rows="2" maxLength="1000" value={form.researchSources} onChange={(event) => { setValidationError(''); setForm({ ...form, researchSources: event.target.value }); }} placeholder="Mỗi URL một dòng (khuyến nghị nguồn chính thức)" /><small className="field-hint">Nguồn giúp bạn đối chiếu thông tin trước khi duyệt bản nháp.</small></label>
    {validationError && <div className="form-error" role="alert">{validationError}</div>}
    <div className="row"><label>Thời lượng mục tiêu<select value={form.targetDurationSeconds} onChange={(event) => setForm({ ...form, targetDurationSeconds: Number(event.target.value) })}><option value="60">60 giây</option><option value="90">90 giây</option><option value="180">3 phút</option><option value="300">5 phút</option><option value="600">10 phút</option></select></label><label>Ưu tiên<select value={form.priority} onChange={(event) => setForm({ ...form, priority: event.target.value })}><option value="LOW">Thấp</option><option value="MEDIUM">Trung bình</option><option value="HIGH">Cao</option></select></label></div>
    <label>Hạn hoàn thành<input type="date" min={new Date().toISOString().slice(0, 10)} value={form.dueDate} onChange={(event) => setForm({ ...form, dueDate: event.target.value })} /></label><button className="primary submit">Tạo công việc</button></form></div>;
}

function PublicationModal({ tasks, onClose, onSave }) {
  const [form, setForm] = useState(emptyPublication);
  const reviewReadyTasks = tasks.filter((task) => task.status === 'DONE' && task.outputPath);
  const submit = async (event) => { event.preventDefault(); await onSave({ ...form, taskId: Number(form.taskId), scheduledAt: form.scheduledAt || null }); onClose(); };
  return <div className="modal-bg"><form className="modal" onSubmit={submit}><button type="button" className="close" onClick={onClose}><X /></button><p>LỊCH XUẤT BẢN MỚI</p><h2>Lên lịch nội dung</h2><div className="review-gate-copy"><ShieldCheck /><span>Chỉ video đã được bạn duyệt mới có thể lên lịch.</span></div><label>Video/công việc<select required value={form.taskId} onChange={(event) => setForm({ ...form, taskId: event.target.value })}><option value="">Chọn công việc</option>{reviewReadyTasks.map((task) => <option key={task.id} value={task.id}>{task.title}</option>)}</select></label>{!reviewReadyTasks.length && <p className="field-hint">Chưa có video hoàn tất. Hãy tạo và duyệt một bản nháp trước.</p>}<div className="row"><label>Nền tảng<select value={form.platform} onChange={(event) => setForm({ ...form, platform: event.target.value })}><option value="TIKTOK">TikTok</option><option value="YOUTUBE">YouTube</option><option value="OTHER">Khác</option></select></label><label>Ngày giờ<input required type="datetime-local" min={new Date().toISOString().slice(0, 16)} value={form.scheduledAt} onChange={(event) => setForm({ ...form, scheduledAt: event.target.value })} /></label></div><label>Ghi chú<textarea rows="3" value={form.note} onChange={(event) => setForm({ ...form, note: event.target.value })} /></label><button className="primary submit" disabled={!reviewReadyTasks.length}>Lưu lịch đăng</button></form></div>;
}

export default function App() {
  const { pathname } = useLocation();
  const { tasks, publications, campaigns, loading, error, stats, actions } = useTechFlowData();
  const [taskModal, setTaskModal] = useState(false);
  const [initialTopic, setInitialTopic] = useState('');
  const [publicationModal, setPublicationModal] = useState(false);
  const [campaignModal, setCampaignModal] = useState(false);
  const openTask = (topic = '') => { setInitialTopic(topic); setTaskModal(true); };
  const closeTask = () => { setTaskModal(false); setInitialTopic(''); };
  const meta = pageMeta[pathname] || pageMeta['/'];
  return <div className="shell">
    <aside><div className="brand"><span><Sparkles /></span><div>TechFlow<small>AI CONTENT STUDIO</small></div></div><nav>
      <NavLink to="/" end><LayoutDashboard />Tổng quan</NavLink>
      <NavLink to="/pipeline"><CirclePlay />Video pipeline</NavLink>
      <NavLink to="/campaigns"><Layers3 />Campaign & series</NavLink>
      <NavLink to="/videos"><Video />Thư viện video</NavLink>
      <NavLink to="/trends"><TrendingUp />Xu hướng</NavLink>
      <NavLink to="/calendar"><CalendarDays />Lịch nội dung</NavLink>
    </nav><div className="safe"><ShieldCheck /><div><b>Chế độ an toàn</b><small>Luôn duyệt trước khi đăng</small></div></div></aside>
    <main><header><div><p>AI TECHFLOW STUDIO</p><h1>{meta[0]}</h1><span>{meta[1]}</span></div>{!['/calendar', '/campaigns'].includes(pathname) && <button className="primary" onClick={() => openTask()}><Plus />Tạo công việc</button>}</header>
      {error && <div className="global-error">{error}</div>}
      {loading && !tasks.length && <div className="global-error">Đang tải dữ liệu...</div>}
      <Routes>
        <Route path="/" element={<Overview tasks={tasks} stats={stats} actions={actions} openTask={() => openTask()} />} />
        <Route path="/pipeline" element={<TaskBoard tasks={tasks} actions={actions} />} />
        <Route path="/campaigns" element={<CampaignView campaigns={campaigns} tasks={tasks} actions={actions} openCampaign={() => setCampaignModal(true)} />} />
        <Route path="/videos" element={<TaskBoard tasks={tasks} actions={actions} />} />
        <Route path="/trends" element={<TrendsView onCreateTask={openTask} />} />
        <Route path="/calendar" element={<CalendarView items={publications} actions={actions} openPublication={() => setPublicationModal(true)} />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </main>
    {taskModal && <TaskModal onClose={closeTask} onSave={actions.createTask} initialTopic={initialTopic} />}
    {campaignModal && <CampaignModal onClose={() => setCampaignModal(false)} onSave={actions.createCampaign} />}
    {publicationModal && <PublicationModal tasks={tasks} onClose={() => setPublicationModal(false)} onSave={actions.createPublication} />}
  </div>;
}
