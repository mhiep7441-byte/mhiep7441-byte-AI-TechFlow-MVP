import { useMemo, useState } from 'react';
import { Navigate, NavLink, Route, Routes, useLocation } from 'react-router-dom';
import {
  CalendarDays, CheckCircle2, ChevronRight, CirclePlay, Clock3, LayoutDashboard,
  Clapperboard, Globe2, ListTodo, Plus, Search, ShieldCheck, Sparkles, Trash2,
  UserRound, Video, WandSparkles, X,
} from 'lucide-react';
import { useTechFlowData } from './hooks/useTechFlowData';

const emptyTask = {
  title: '', description: '', topic: '', priority: 'MEDIUM', status: 'TODO', dueDate: '',
  visualStyle: '', characterDescription: '', researchSources: '',
};
const emptyPublication = { taskId: '', platform: 'TIKTOK', status: 'PENDING', scheduledAt: '', note: '' };
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
    {(task.visualStyle || task.characterDescription || task.researchSources) && <div className="creative-brief" aria-label="Tóm tắt định hướng video">
      {task.visualStyle && <span><Clapperboard />{task.visualStyle}</span>}
      {task.characterDescription && <span><UserRound />{task.characterDescription}</span>}
      {task.researchSources && <span><Globe2 />{task.researchSources.split(/\r?\n/).filter(Boolean).length} nguồn nghiên cứu</span>}
    </div>}
    {task.errorMessage && <div className="error">{task.errorMessage}</div>}
    {task.outputPath && <div className="video-result">
      <div className="preview-head"><span><Video />BẢN XEM TRƯỚC</span><strong>{needsReview ? 'CẦN DUYỆT' : labels[task.status]}</strong></div>
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

function CalendarView({ items, actions, openPublication }) {
  const grouped = useMemo(() => items.reduce((result, item) => { const day = item.scheduledAt ? item.scheduledAt.slice(0, 10) : 'Chưa lên ngày'; (result[day] ||= []).push(item); return result; }, {}), [items]);
  return <section className="workspace calendar-view"><div className="section-head"><div><p>LỊCH XUẤT BẢN</p><h2>Kế hoạch nội dung</h2></div><button className="primary" onClick={openPublication}><Plus />Thêm lịch đăng</button></div><div className="calendar-list">{Object.entries(grouped).map(([day, rows]) => <div className="calendar-day" key={day}><div className="date-box"><CalendarDays /><b>{day}</b><small>{rows.length} nội dung</small></div><div className="schedule-items">{rows.map((item) => <article className="schedule-card" key={item.id}><div><span className={`platform ${item.platform}`}>{item.platform}</span><h3>{item.taskTitle}</h3><p>{item.note || 'Không có ghi chú'}</p></div><div className="schedule-meta"><span className={`publication-status ${item.status}`}>{publicationLabels[item.status]}</span><time>{item.scheduledAt ? item.scheduledAt.slice(11, 16) : '--:--'}</time><button className="icon" onClick={() => window.confirm('Xóa lịch đăng này?') && actions.deletePublication(item.id)}><Trash2 /></button></div></article>)}</div></div>)}{!items.length && <div className="big-empty"><CalendarDays /><h3>Chưa có lịch xuất bản</h3><p>Thêm lịch đăng sau khi video đã được bạn duyệt.</p><button className="primary" onClick={openPublication}><Plus />Tạo lịch đầu tiên</button></div>}</div></section>;
}

function TaskModal({ onClose, onSave }) {
  const [form, setForm] = useState(emptyTask);
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
    <div className="row"><label>Ưu tiên<select value={form.priority} onChange={(event) => setForm({ ...form, priority: event.target.value })}><option value="LOW">Thấp</option><option value="MEDIUM">Trung bình</option><option value="HIGH">Cao</option></select></label><label>Hạn hoàn thành<input type="date" min={new Date().toISOString().slice(0, 10)} value={form.dueDate} onChange={(event) => setForm({ ...form, dueDate: event.target.value })} /></label></div><button className="primary submit">Tạo công việc</button></form></div>;
}

function PublicationModal({ tasks, onClose, onSave }) {
  const [form, setForm] = useState(emptyPublication);
  const reviewReadyTasks = tasks.filter((task) => task.status === 'DONE' && task.outputPath);
  const submit = async (event) => { event.preventDefault(); await onSave({ ...form, taskId: Number(form.taskId), scheduledAt: form.scheduledAt || null }); onClose(); };
  return <div className="modal-bg"><form className="modal" onSubmit={submit}><button type="button" className="close" onClick={onClose}><X /></button><p>LỊCH XUẤT BẢN MỚI</p><h2>Lên lịch nội dung</h2><div className="review-gate-copy"><ShieldCheck /><span>Chỉ video đã được bạn duyệt mới có thể lên lịch.</span></div><label>Video/công việc<select required value={form.taskId} onChange={(event) => setForm({ ...form, taskId: event.target.value })}><option value="">Chọn công việc</option>{reviewReadyTasks.map((task) => <option key={task.id} value={task.id}>{task.title}</option>)}</select></label>{!reviewReadyTasks.length && <p className="field-hint">Chưa có video hoàn tất. Hãy tạo và duyệt một bản nháp trước.</p>}<div className="row"><label>Nền tảng<select value={form.platform} onChange={(event) => setForm({ ...form, platform: event.target.value })}><option value="TIKTOK">TikTok</option><option value="YOUTUBE">YouTube</option><option value="OTHER">Khác</option></select></label><label>Ngày giờ<input required type="datetime-local" min={new Date().toISOString().slice(0, 16)} value={form.scheduledAt} onChange={(event) => setForm({ ...form, scheduledAt: event.target.value })} /></label></div><label>Ghi chú<textarea rows="3" value={form.note} onChange={(event) => setForm({ ...form, note: event.target.value })} /></label><button className="primary submit" disabled={!reviewReadyTasks.length}>Lưu lịch đăng</button></form></div>;
}

export default function App() {
  const { pathname } = useLocation();
  const { tasks, publications, loading, error, stats, actions } = useTechFlowData();
  const [taskModal, setTaskModal] = useState(false);
  const [publicationModal, setPublicationModal] = useState(false);
  const meta = pageMeta[pathname] || pageMeta['/'];
  return <div className="shell"><aside><div className="brand"><span><Sparkles /></span><div>TechFlow<small>AI CONTENT STUDIO</small></div></div><nav><NavLink to="/" end><LayoutDashboard />Tổng quan</NavLink><NavLink to="/pipeline"><CirclePlay />Video pipeline</NavLink><NavLink to="/videos"><Video />Thư viện video</NavLink><NavLink to="/calendar"><CalendarDays />Lịch nội dung</NavLink></nav><div className="safe"><ShieldCheck /><div><b>Chế độ an toàn</b><small>Luôn duyệt trước khi đăng</small></div></div></aside><main><header><div><p>AI TECHFLOW STUDIO</p><h1>{meta[0]}</h1><span>{meta[1]}</span></div>{pathname !== '/calendar' && <button className="primary" onClick={() => setTaskModal(true)}><Plus />Tạo công việc</button>}</header>{error && <div className="global-error">{error}</div>}{loading && !tasks.length && <div className="global-error">Đang tải dữ liệu...</div>}<Routes><Route path="/" element={<Overview tasks={tasks} stats={stats} actions={actions} openTask={() => setTaskModal(true)} />} /><Route path="/pipeline" element={<TaskBoard tasks={tasks} actions={actions} />} /><Route path="/videos" element={<TaskBoard tasks={tasks} actions={actions} />} /><Route path="/calendar" element={<CalendarView items={publications} actions={actions} openPublication={() => setPublicationModal(true)} />} /><Route path="*" element={<Navigate to="/" replace />} /></Routes></main>{taskModal && <TaskModal onClose={() => setTaskModal(false)} onSave={actions.createTask} />}{publicationModal && <PublicationModal tasks={tasks} onClose={() => setPublicationModal(false)} onSave={actions.createPublication} />}</div>;
}
