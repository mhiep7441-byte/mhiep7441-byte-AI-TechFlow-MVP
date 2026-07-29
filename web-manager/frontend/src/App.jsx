import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link, Navigate, NavLink, Outlet, Route, Routes, useLocation, useNavigate, useParams } from 'react-router-dom';
import {
  ArrowLeft, ArrowRight, BarChart3, CalendarDays, Check, ChevronLeft, ChevronRight,
  CirclePlay, Clock3, Copy, ExternalLink, Film, Gauge, Image, LayoutDashboard, ListFilter,
  LogOut, Menu, MoreHorizontal, PencilLine, Plus, RefreshCw, Search, Send, Settings2,
  ShieldCheck, Sparkles, Trash2, UserRound, UsersRound, WandSparkles, X, Layers3, Bot,
  Upload, BookOpen, Star,
} from 'lucide-react';
import { api, apiForm } from './api';
import { useAuth } from './AuthContext';
import { evidenceSummary, safeJson } from './utils';
import {
  AdminDashboardPage, AdminFeedbackPage, ProfilePage, ResearchNotebookPage,
  UserHomePage, VideoFeedbackWidget, YouTubePublishModal,
} from './WorkspacePages';

const taskDefaults = {
  title: '', description: '', topic: '', caption: '', hashtags: '',
  status: 'TODO', priority: 'MEDIUM', dueDate: '', targetDurationSeconds: 180,
  visualStyle: '', characterDescription: '', audioMode: 'narrated',
  videoProvider: 'kenburns', aspectRatio: '9:16', renderQuality: 'draft',
};
const campaignDefaults = {
  name: '', theme: '', description: '', episodeCount: 5, targetDurationSeconds: 180,
  visualStyle: '', characterDescription: '', audience: '', cadence: 'MANUAL',
  productionEnabled: false, nextRunAt: '', status: 'PLANNING', audioMode: 'narrated',
  videoProvider: 'kenburns', aspectRatio: '9:16', renderQuality: 'draft',
};
const statusLabels = {
  TODO: 'Ý tưởng', IN_PROGRESS: 'Đang làm', GENERATING: 'Đang dựng',
  DRAFT_REQUIRES_REVIEW: 'Chờ duyệt', DONE: 'Hoàn tất', FAILED: 'Lỗi',
};
const priorityLabels = { LOW: 'Thấp', MEDIUM: 'Trung bình', HIGH: 'Cao' };
const campaignProvider = (campaign) => safeJson(campaign?.seriesPlanJson, {}).provider || 'chưa chạy';

function RenderProfileFields({ form, setForm }) {
  return <div className="render-profile-grid">
    <label>Âm thanh<select value={form.audioMode} onChange={(e) => setForm({ ...form, audioMode: e.target.value })}><option value="narrated">Giọng đọc + phụ đề</option><option value="silent_animation">Hoạt hình không thoại + BGM</option></select></label>
    <label>Chuyển động<select value={form.videoProvider} onChange={(e) => setForm({ ...form, videoProvider: e.target.value })}><option value="kenburns">Ken Burns nhanh</option><option value="seedance2_fast">Seedance 2 Fast</option><option value="veo">Google Veo</option></select></label>
    <label>Khung hình<select value={form.aspectRatio} onChange={(e) => setForm({ ...form, aspectRatio: e.target.value })}><option value="9:16">Dọc 9:16</option><option value="16:9">Ngang 16:9</option></select></label>
    <label>Chất lượng<select value={form.renderQuality} onChange={(e) => setForm({ ...form, renderQuality: e.target.value })}><option value="draft">Draft nhanh</option><option value="hd">HD</option><option value="2k">2K</option></select></label>
  </div>;
}

function Loader({ label = 'Đang tải dữ liệu' }) {
  return <div className="loader-state"><span className="spinner" /><p>{label}</p></div>;
}

function EmptyState({ icon: Icon = Film, title, description, action }) {
  return <div className="empty-state"><span><Icon /></span><h3>{title}</h3><p>{description}</p>{action}</div>;
}

function Pagination({ page, totalPages, onChange }) {
  if (totalPages <= 1) return null;
  return <div className="pagination">
    <button disabled={page === 0} onClick={() => onChange(page - 1)}><ChevronLeft /> Trước</button>
    <span>Trang <b>{page + 1}</b> / {totalPages}</span>
    <button disabled={page + 1 >= totalPages} onClick={() => onChange(page + 1)}>Sau <ChevronRight /></button>
  </div>;
}

function LoginPage() {
  const { user, config, login, register } = useAuth();
  const navigate = useNavigate();
  const [mode, setMode] = useState('login');
  const [form, setForm] = useState({ displayName: '', email: '', password: '' });
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  if (user) return <Navigate to="/" replace />;

  const submit = async (event) => {
    event.preventDefault(); setBusy(true); setError('');
    try {
      if (mode === 'login') await login({ email: form.email, password: form.password });
      else await register(form);
      navigate('/', { replace: true });
    } catch (reason) { setError(reason.message); }
    finally { setBusy(false); }
  };

  return <main className="auth-page">
    <section className="auth-showcase">
      <div className="auth-brand"><span>TF</span> AI TechFlow Studio</div>
      <div className="showcase-copy"><p>CONTENT OPERATING SYSTEM</p><h1>Biến ý tưởng thành nội dung có thể tăng trưởng.</h1><span>Một nơi để lên kế hoạch, dựng video, duyệt nội dung và chuẩn bị xuất bản.</span></div>
      <div className="showcase-grid">
        <div className="showcase-card"><small>01 / CREATE</small><WandSparkles /><b>Kịch bản có cấu trúc</b><span>Luôn có bước kiểm chứng và duyệt.</span></div>
        <div className="showcase-card cream"><small>02 / PRODUCE</small><Film /><b>Video dọc tự động</b><span>Giọng Việt, phụ đề và cloud delivery.</span></div>
        <div className="showcase-card lilac"><small>03 / GROW</small><BarChart3 /><b>Vận hành như một studio</b><span>Dashboard, lịch đăng và phân quyền.</span></div>
      </div>
      <p className="auth-foot">Designed for creators who ship consistently.</p>
    </section>
    <section className="auth-panel">
      <form onSubmit={submit}>
        <div className="auth-kicker">AI TECHFLOW / VIETNAM</div>
        <h2>{mode === 'login' ? 'Chào mừng trở lại.' : 'Tạo workspace của bạn.'}</h2>
        <p>{mode === 'login' ? 'Đăng nhập để tiếp tục vận hành nội dung.' : 'Bắt đầu miễn phí, nâng cấp khi kênh phát triển.'}</p>
        {error && <div className="form-error">{error}</div>}
        {mode === 'register' && <label>Họ tên<input required maxLength="120" value={form.displayName} onChange={(e) => setForm({ ...form, displayName: e.target.value })} /></label>}
        <label>Email<input required type="email" autoComplete="email" value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} /></label>
        <label>Mật khẩu<input required minLength="10" type="password" autoComplete={mode === 'login' ? 'current-password' : 'new-password'} value={form.password} onChange={(e) => setForm({ ...form, password: e.target.value })} /></label>
        <button className="button dark full" disabled={busy}>{busy ? 'Đang xử lý...' : mode === 'login' ? 'Đăng nhập' : 'Tạo tài khoản'} <ArrowRight /></button>
        {config.googleEnabled && <a className="google-button" href="/oauth2/authorization/google"><b>G</b> Tiếp tục với Google</a>}
        <button type="button" className="text-button" onClick={() => { setMode(mode === 'login' ? 'register' : 'login'); setError(''); }}>
          {mode === 'login' ? 'Chưa có tài khoản? Đăng ký' : 'Đã có tài khoản? Đăng nhập'}
        </button>
      </form>
    </section>
  </main>;
}

function Protected({ admin = false }) {
  const { user, loading } = useAuth();
  if (loading) return <Loader label="Đang xác thực" />;
  if (!user) return <Navigate to="/login" replace />;
  if (admin && user.role !== 'ADMIN') return <Navigate to="/" replace />;
  return <Outlet />;
}

function AppShell() {
  const { user, logout } = useAuth();
  const { pathname } = useLocation();
  const navigate = useNavigate();
  const [mobileOpen, setMobileOpen] = useState(false);
  const pageTitle = pathname.startsWith('/videos/') ? 'Video Studio'
    : pathname.startsWith('/videos') ? 'Thư viện video'
      : pathname.startsWith('/campaigns') ? 'Campaign & Series'
      : pathname.startsWith('/research') ? 'Research Notebook'
      : pathname.startsWith('/calendar') ? 'Lịch nội dung'
        : pathname.startsWith('/profile') ? 'Hồ sơ người dùng' : 'Workspace của tôi';
  const doLogout = async () => { await logout(); navigate('/login'); };
  const close = () => setMobileOpen(false);
  return <div className="app-shell">
    <aside className={mobileOpen ? 'sidebar open' : 'sidebar'}>
      <Link className="brand" to="/" onClick={close}><span>TF</span><div>TechFlow<small>CONTENT STUDIO</small></div></Link>
      <nav>
        <NavLink to="/" end onClick={close}><LayoutDashboard /> Workspace</NavLink>
        <NavLink to="/videos" onClick={close}><Film /> Video Studio</NavLink>
        <NavLink to="/campaigns" onClick={close}><Layers3 /> Campaign & Series</NavLink>
        <NavLink to="/research" onClick={close}><BookOpen /> Research Notebook</NavLink>
        <NavLink to="/calendar" onClick={close}><CalendarDays /> Lịch nội dung</NavLink>
        <NavLink to="/profile" onClick={close}><UserRound /> Hồ sơ & kết nối</NavLink>
      </nav>
      <div className="sidebar-note"><ShieldCheck /><div><b>Review-first</b><span>Không đăng khi chưa duyệt.</span></div></div>
      <div className="sidebar-user"><div className="avatar">{user.displayName.slice(0, 1).toUpperCase()}</div><div><b>{user.displayName}</b><span>{user.role}</span></div><button onClick={doLogout} aria-label="Đăng xuất"><LogOut /></button></div>
    </aside>
    {mobileOpen && <button className="sidebar-backdrop" aria-label="Đóng menu" onClick={close} />}
    <div className="app-content">
      <header className="topbar"><button className="menu-button" onClick={() => setMobileOpen(true)}><Menu /></button><div><span>WORKSPACE / {user.role}</span><h1>{pageTitle}</h1></div><div className="top-actions">{user.role === 'ADMIN' && <Link className="button outline" to="/admin"><Gauge /> Admin</Link>}<Link className="button outline" to="/videos"><Search /> Tìm video</Link><Link className="button dark" to="/videos?new=1"><Plus /> Tạo nội dung</Link></div></header>
      <Outlet />
    </div>
  </div>;
}


function AdminShell() {
  const { user, logout } = useAuth();
  const { pathname } = useLocation();
  const navigate = useNavigate();
  const [mobileOpen, setMobileOpen] = useState(false);
  const pageTitle = pathname.startsWith('/admin/users') ? 'Quản lý người dùng'
    : pathname.startsWith('/admin/feedback') ? 'Phản hồi video'
      : 'Admin Dashboard';
  const doLogout = async () => { await logout(); navigate('/login'); };
  const close = () => setMobileOpen(false);
  return <div className="app-shell admin-shell">
    <aside className={mobileOpen ? 'sidebar open admin-sidebar' : 'sidebar admin-sidebar'}>
      <Link className="brand" to="/admin" onClick={close}><span>TF</span><div>Admin<small>CONTROL CENTER</small></div></Link>
      <nav>
        <NavLink to="/admin" end onClick={close}><Gauge /> Tổng quan</NavLink>
        <NavLink to="/admin/users" onClick={close}><UsersRound /> Người dùng</NavLink>
        <NavLink to="/admin/feedback" onClick={close}><Star /> Phản hồi video</NavLink>
        <div className="nav-section-label">WORKSPACE</div>
        <NavLink to="/" onClick={close}><LayoutDashboard /> Quay lại workspace</NavLink>
      </nav>
      <div className="sidebar-note"><ShieldCheck /><div><b>Admin only</b><span>Quản trị tách khỏi giao diện user.</span></div></div>
      <div className="sidebar-user"><div className="avatar">{user.displayName.slice(0, 1).toUpperCase()}</div><div><b>{user.displayName}</b><span>{user.role}</span></div><button onClick={doLogout} aria-label="Đăng xuất"><LogOut /></button></div>
    </aside>
    {mobileOpen && <button className="sidebar-backdrop" aria-label="Đóng menu" onClick={close} />}
    <div className="app-content">
      <header className="topbar admin-topbar"><button className="menu-button" onClick={() => setMobileOpen(true)}><Menu /></button><div><span>ADMIN / {user.role}</span><h1>{pageTitle}</h1></div><div className="top-actions"><Link className="button outline" to="/"><LayoutDashboard /> Workspace</Link></div></header>
      <Outlet />
    </div>
  </div>;
}

function DashboardPage() {
  const [dashboard, setDashboard] = useState(null);
  const [recent, setRecent] = useState([]);
  const [error, setError] = useState('');
  useEffect(() => {
    Promise.all([api('/api/dashboard'), api('/api/tasks?size=4')])
      .then(([stats, tasks]) => { setDashboard(stats); setRecent(tasks.content); })
      .catch((reason) => setError(reason.message));
  }, []);
  if (!dashboard && !error) return <Loader />;
  return <div className="page dashboard-page">
    {error && <div className="alert error">{error}</div>}
    <section className="hero-section">
      <div><p>TECHFLOW OPERATING SYSTEM</p><h2>Từ một chủ đề đến video sẵn sàng để tăng trưởng.</h2><span>Lập kế hoạch, tạo bản nháp, duyệt và lên lịch trong một quy trình rõ ràng.</span><div><Link className="button light" to="/videos?new=1">Tạo video mới <ArrowRight /></Link><Link className="hero-link" to="/videos">Xem pipeline</Link></div></div>
      <div className="hero-visual"><div className="phone-card"><small>DRAFT / 9:16</small><Sparkles /><b>Video công nghệ<br />trong vài phút.</b><span>VOICE • CAPTION • CLOUD</span></div><span className="floating-tag">AI-assisted</span></div>
    </section>
    <section className="metric-grid">
      {[
        ['Tổng video', dashboard?.total, Gauge, 'Tất cả nội dung'],
        ['Đang sản xuất', dashboard?.processing, Clock3, 'Worker đang chạy'],
        ['Chờ duyệt', dashboard?.review, ShieldCheck, 'Cần quyết định'],
        ['Đã hoàn tất', dashboard?.done, Check, 'Sẵn sàng sử dụng'],
      ].map(([label, value, Icon, hint]) => <article className="metric-card" key={label}><div><Icon /><span>{label}</span></div><strong>{value ?? 0}</strong><small>{hint}</small></article>)}
    </section>
    <section className="feature-section"><div className="section-heading"><div><span>THE WORKFLOW</span><h2>Một studio, bốn năng lực cốt lõi.</h2></div><p>Thiết kế để vận hành nội dung đều đặn, không phải tạo một video rồi dừng.</p></div><div className="feature-grid">
      {[
        ['01', 'Kịch bản có cấu trúc', 'Ý tưởng, hook, narration và caption nằm cùng một nơi.', WandSparkles],
        ['02', 'Video cloud-ready', 'FFmpeg tối ưu, giọng Việt và lưu trữ Cloudinary.', Film],
        ['03', 'Duyệt trước khi đăng', 'Trạng thái rõ ràng giúp tránh nội dung sai hoặc chưa hoàn thiện.', ShieldCheck],
        ['04', 'Lịch xuất bản', 'Chuẩn bị TikTok và YouTube theo lịch vận hành.', Send],
      ].map(([number, title, text, Icon]) => <article className="feature-card" key={number}><div><small>{number}</small><Icon /></div><h3>{title}</h3><p>{text}</p></article>)}
      </div>
    </section>
    <section className="recent-section"><div className="section-heading compact"><div><span>RECENT WORK</span><h2>Video gần đây</h2></div><Link to="/videos">Xem tất cả <ArrowRight /></Link></div>
      <div className="recent-list">{recent.map((task) => <Link to={`/videos/${task.id}`} className="recent-row" key={task.id}><div className="video-thumb">{task.outputPath ? <video muted preload="metadata" src={task.outputPath} /> : <Film />}</div><div><b>{task.title}</b><span>{task.topic || 'Chưa có chủ đề'}</span></div><span className={`status ${task.status}`}>{statusLabels[task.status]}</span><ArrowRight /></Link>)}{!recent.length && <EmptyState title="Chưa có video" description="Tạo nội dung đầu tiên để bắt đầu." />}</div>
    </section>
  </div>;
}

function TaskModal({ onClose, onCreated }) {
  const [form, setForm] = useState(taskDefaults);
  const [showAdvanced, setShowAdvanced] = useState(false);
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const submit = async (event) => {
    event.preventDefault(); setBusy(true); setError('');
    try {
      const created = await api('/api/tasks', {
        method: 'POST',
        body: {
          ...form,
          title: form.title.trim() || form.topic.trim().slice(0, 160),
          description: form.description.trim() || `Gemini sẽ nghiên cứu và biên soạn video 3 phút từ chủ đề: ${form.topic.trim()}`,
          dueDate: form.dueDate || null,
        },
      });
      await api(`/api/tasks/${created.id}/generate`, { method: 'POST' });
      onCreated({ ...created, status: 'GENERATING' });
    }
    catch (reason) { setError(reason.message); }
    finally { setBusy(false); }
  };
  return <div className="modal-backdrop"><form className="modal" onSubmit={submit}>
    <div className="modal-head"><div><span>GEMINI VIDEO AGENT</span><h2>Tạo video 3 phút</h2></div><button type="button" onClick={onClose}><X /></button></div>
    {error && <div className="form-error">{error}</div>}
    <label>Ý tưởng video<textarea autoFocus required rows="5" maxLength="500" value={form.topic} onChange={(e) => setForm({ ...form, topic: e.target.value })} placeholder="Ví dụ: Chó cảnh sát Bobo giải cứu một bạn nhỏ bị lạc và dạy 3 kỹ năng an toàn." /></label>
    <button type="button" className="advanced-toggle" onClick={() => setShowAdvanced(!showAdvanced)}><Settings2 /> {showAdvanced ? 'Ẩn tùy chỉnh' : 'Tùy chỉnh nâng cao'}</button>
    {showAdvanced && <div className="advanced-campaign-fields">
      <label>Tiêu đề<input maxLength="160" value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} placeholder="Để trống để dùng ý tưởng làm tiêu đề" /></label>
      <label>Mô tả<textarea rows="2" maxLength="2000" value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} /></label>
      <div className="form-grid"><label>Thời lượng<select value={form.targetDurationSeconds} onChange={(e) => setForm({ ...form, targetDurationSeconds: Number(e.target.value) })}><option value="60">60 giây / 6 cảnh</option><option value="90">90 giây / 9 cảnh</option><option value="180">3 phút / 18 cảnh</option></select></label><label>Ưu tiên<select value={form.priority} onChange={(e) => setForm({ ...form, priority: e.target.value })}>{Object.entries(priorityLabels).map(([value, label]) => <option value={value} key={value}>{label}</option>)}</select></label></div>
      <div className="form-grid"><label>Phong cách hình ảnh<input maxLength="240" value={form.visualStyle} onChange={(e) => setForm({ ...form, visualStyle: e.target.value })} placeholder="Cinematic 3D animation..." /></label><label>Nhân vật / host<input maxLength="240" value={form.characterDescription} onChange={(e) => setForm({ ...form, characterDescription: e.target.value })} /></label></div>
      <RenderProfileFields form={form} setForm={setForm} />
    </div>}
    <button className="button dark full" disabled={busy || !form.topic.trim()}>{busy ? 'Gemini đang bắt đầu pipeline...' : 'Generate video bằng AI'} <WandSparkles /></button>
  </form></div>;
}

function VideosPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const [pageData, setPageData] = useState(null);
  const [page, setPage] = useState(0);
  const [query, setQuery] = useState('');
  const [status, setStatus] = useState('');
  const [error, setError] = useState('');
  const [showCreate, setShowCreate] = useState(new URLSearchParams(location.search).get('new') === '1');
  const load = useCallback(() => {
    const params = new URLSearchParams({ page, size: 9, query });
    if (status) params.set('status', status);
    api(`/api/tasks?${params}`).then(setPageData).catch((r) => setError(r.message));
  }, [page, query, status]);
  useEffect(() => { load(); }, [load]);
  const remove = async (task) => {
    if (!window.confirm(`Xóa "${task.title}"?`)) return;
    try { await api(`/api/tasks/${task.id}`, { method: 'DELETE' }); load(); }
    catch (r) { setError(r.message); }
  };
  return <div className="page">
    <section className="page-intro"><div><span>CONTENT LIBRARY</span><h2>Quản lý toàn bộ video.</h2><p>Tìm, lọc, mở studio chỉnh sửa và theo dõi trạng thái sản xuất.</p></div><button className="button dark" onClick={() => setShowCreate(true)}><Plus /> Tạo video</button></section>
    {error && <div className="alert error">{error}</div>}
    <section className="filter-bar"><div className="search-field"><Search /><input value={query} onChange={(e) => { setQuery(e.target.value); setPage(0); }} placeholder="Tìm theo tiêu đề hoặc chủ đề..." /></div><div className="select-field"><ListFilter /><select value={status} onChange={(e) => { setStatus(e.target.value); setPage(0); }}><option value="">Tất cả trạng thái</option>{Object.entries(statusLabels).map(([v, l]) => <option value={v} key={v}>{l}</option>)}</select></div></section>
    {!pageData ? <Loader /> : pageData.content.length ? <><section className="video-grid">{pageData.content.map((task) => <article className="video-card" key={task.id}>
      <Link className="video-poster" to={`/videos/${task.id}`}>{task.outputPath ? <video muted preload="metadata" src={task.outputPath} /> : <div className="poster-placeholder"><Sparkles /><b>{task.topic || 'AI TechFlow'}</b><span>DRAFT</span></div>}<span className={`status ${task.status}`}>{statusLabels[task.status]}</span></Link>
      <div className="video-card-body"><div className="card-meta"><span>{task.campaignId ? `Tập ${task.episodeNumber}` : priorityLabels[task.priority]}</span><span>{task.targetDurationSeconds || 60}s • {task.aiProvider || 'AI'}</span></div><h3><Link to={`/videos/${task.id}`}>{task.title}</Link></h3><p>{task.description || 'Chưa có mô tả.'}</p><div className="card-actions"><Link to={`/videos/${task.id}`}><PencilLine /> Mở Studio</Link><button onClick={() => remove(task)}><Trash2 /></button></div></div>
    </article>)}</section><Pagination page={pageData.number} totalPages={pageData.totalPages} onChange={setPage} /></> : <EmptyState title="Không tìm thấy video" description="Thử bộ lọc khác hoặc tạo nội dung mới." action={<button className="button dark" onClick={() => setShowCreate(true)}><Plus /> Tạo video</button>} />}
    {showCreate && <TaskModal onClose={() => setShowCreate(false)} onCreated={(task) => { setShowCreate(false); navigate(`/videos/${task.id}`); }} />}
  </div>;
}

function VideoStudioPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const [task, setTask] = useState(null);
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');
  const [tab, setTab] = useState('overview');
  const [showYoutubePublish, setShowYoutubePublish] = useState(false);
  const load = useCallback(() => api(`/api/tasks/${id}`).then(setTask).catch((r) => setError(r.message)), [id]);
  useEffect(() => { load(); }, [load]);
  useEffect(() => { if (!task || task.status !== 'GENERATING') return; const t = setInterval(load, 8000); return () => clearInterval(t); }, [task, load]);
  const storyboard = useMemo(() => safeJson(task?.storyboardJson, {}), [task]);
  const research = useMemo(() => safeJson(task?.researchJson, {}), [task]);
  const scenes = storyboard.scenes || [];
  const characters = storyboard.characters || [];
  const tabs = [
    ['overview', 'Tổng quan'], ['script', 'Kịch bản'], ['cast', 'Nhân vật'], ['scenes', 'Cảnh quay'],
    ['images', 'Hình ảnh'], ['voice', 'Giọng đọc'], ['subtitles', 'Phụ đề'],
    ['preview', 'Xem trước'], ['quality', 'Chất lượng'], ['files', 'Tải về'],
  ];
  const remove = async () => {
    if (!window.confirm(`Xóa "${task.title}"?`)) return;
    try { await api(`/api/tasks/${id}`, { method: 'DELETE' }); navigate('/videos'); }
    catch (r) { setError(r.message); }
  };
  const regenerate = async () => {
    try { await api(`/api/tasks/${id}/generate`, { method: 'POST' }); setMessage('Đã bắt đầu tạo lại video.'); load(); }
    catch (r) { setError(r.message); }
  };
  if (!task && !error) return <Loader />;
  if (error && !task) return <div className="page"><div className="alert error">{error}</div><Link to="/videos"><ArrowLeft /> Quay lại</Link></div>;
  return <div className="page">
    <div className="studio-breadcrumb"><Link to="/videos"><ArrowLeft /> Thư viện</Link><span>/</span><span>{task.title}</span></div>
    {error && <div className="alert error">{error}</div>}
    {message && <div className="alert success">{message}</div>}
    <div className="inky-studio-header"><div><small>VIDEO STUDIO • ID #{task.id}</small><h2>{task.title}</h2><span className={`status ${task.status}`}>{statusLabels[task.status]}</span></div><div className="header-actions">
      <button className="button outline" onClick={regenerate}><RefreshCw /> Tạo lại</button>
      {task.outputPath && <button className="button outline" onClick={() => setShowYoutubePublish(true)}><Upload /> YouTube</button>}
      <button className="button outline" onClick={remove}><Trash2 /> Xóa</button>
    </div></div>
    <nav className="inky-tabs">{tabs.map(([key, label]) => <button key={key} className={`inky-tab-button${tab === key ? ' active' : ''}`} onClick={() => setTab(key)}>{label}</button>)}</nav>
    <div className="inky-tab-content">
      {tab === 'overview' && <div className="tab-pane">
        {task.status === 'GENERATING' && <Loader label="Gemini đang dựng video — tự động cập nhật mỗi 8 giây..." />}
        {task.outputPath && <div className="widescreen-player"><video controls preload="metadata" src={task.outputPath} /></div>}
        <div className="download-grid" style={{marginTop: 20}}>
          <div className="download-tile"><Film /><div><b>Chủ đề</b><small>{task.topic || '—'}</small></div></div>
          <div className="download-tile"><Clock3 /><div><b>Thời lượng</b><small>{task.targetDurationSeconds}s • {task.aspectRatio} • {task.renderQuality}</small></div></div>
          <div className="download-tile"><UserRound /><div><b>Nhân vật</b><small>{task.characterDescription || 'AI tự chọn'}</small></div></div>
          <div className="download-tile"><Sparkles /><div><b>Phong cách</b><small>{task.visualStyle || 'AI tự chọn'}</small></div></div>
        </div>
        {task.outputPath && <VideoFeedbackWidget task={task} />}
      </div>}
      {tab === 'script' && <div className="tab-pane">
        <h3>Kịch bản ({scenes.length} cảnh)</h3>
        {storyboard.hook && <div className="download-tile" style={{marginBottom: 16}}><Sparkles /><div><b>Hook</b><small>{storyboard.hook}</small></div></div>}
        {scenes.length ? scenes.map((s, i) => <div key={i} className="scene-card-item"><header><b>Cảnh {s.scene_number || i + 1}</b><span>{s.title || ''}</span></header><p>{s.narration || s.text || '—'}</p></div>) : <EmptyState title="Chưa có kịch bản" description="Bấm Tạo lại để Gemini viết kịch bản." />}
      </div>}
      {tab === 'cast' && <div className="tab-pane">
        <h3>Nhân vật ({characters.length})</h3>
        {characters.length ? <div className="cast-grid">{characters.map((c, i) => <div key={i} className="character-card"><span className="char-badge">{c.role || 'MAIN'}</span><h4>{c.name || `Nhân vật ${i + 1}`}</h4><p>{c.description || '—'}</p></div>)}</div> : <EmptyState icon={UserRound} title="Không có nhân vật" description="AI sẽ tự tạo nhân vật khi dựng video." />}
      </div>}
      {tab === 'scenes' && <div className="tab-pane">
        <h3>Cảnh quay ({scenes.length})</h3>
        <div className="scene-cards-list">{scenes.map((s, i) => <div key={i} className="scene-card-item">
          <header><b>Cảnh {s.scene_number || i + 1}</b><span>{s.title || ''}</span>{s.duration_hint && <small>{s.duration_hint}s</small>}</header>
          <p><b>Narration:</b> {s.narration || s.text || '—'}</p>
          {s.environment && <p><b>Bối cảnh:</b> {s.environment}</p>}
          {s.characters?.length > 0 && <p><b>Nhân vật:</b> {s.characters.map((c) => c.character_id || c.name).join(', ')}</p>}
        </div>)}</div>
      </div>}
      {tab === 'images' && <div className="tab-pane">
        <h3>Hình ảnh minh hoạ</h3>
        {task.imageSetUrl ? <a href={task.imageSetUrl} target="_blank" rel="noreferrer" className="button dark"><Image /> Mở bộ ảnh trên Cloudinary</a> : <EmptyState icon={Image} title="Chưa có hình ảnh" description="Hình ảnh sẽ được tạo khi Gemini dựng video." />}
      </div>}
      {tab === 'voice' && <div className="tab-pane">
        <h3>Giọng đọc</h3>
        {task.narrationUrl ? <><audio controls src={task.narrationUrl} style={{width: '100%', marginTop: 12}} /><a href={task.narrationUrl} target="_blank" rel="noreferrer" className="button outline" style={{marginTop: 12}}>Tải file WAV</a></> : <EmptyState title="Chưa có giọng đọc" description="Audio sẽ được tạo khi dựng video." />}
      </div>}
      {tab === 'subtitles' && <div className="tab-pane">
        <h3>Phụ đề</h3>
        {task.subtitleUrl ? <a href={task.subtitleUrl} target="_blank" rel="noreferrer" className="button dark">Tải phụ đề SRT</a> : <EmptyState title="Chưa có phụ đề" description="Phụ đề sẽ được tạo khi dựng video." />}
      </div>}
      {tab === 'preview' && <div className="tab-pane">
        <h3>Xem trước video</h3>
        {task.outputPath ? <div className="widescreen-player"><video controls preload="metadata" src={task.outputPath} /></div> : <EmptyState icon={CirclePlay} title="Chưa có video" description="Video sẽ hiện ở đây sau khi dựng xong." />}
      </div>}
      {tab === 'quality' && <div className="tab-pane">
        <h3>Báo cáo chất lượng</h3>
        <div className="quality-card-expanded">
          {task.qualityScore != null && <span className="score-badge">Điểm: {task.qualityScore}/100</span>}
          <p><b>Fact-check:</b> {task.factCheckStatus}</p>
          {research.summary && <p><b>Tóm tắt nghiên cứu:</b> {research.summary}</p>}
          {task.sourceUrls && <p><b>Nguồn:</b> {task.sourceUrls}</p>}
        </div>
      </div>}
      {tab === 'files' && <div className="tab-pane">
        <h3>Tải về tài nguyên</h3>
        <div className="download-grid">
          {task.outputPath && <a href={task.outputPath} target="_blank" rel="noreferrer" className="download-tile"><Film /><div><b>Final Video (MP4)</b><small>Video đã render xong</small></div></a>}
          {task.projectArchiveUrl && <a href={task.projectArchiveUrl} target="_blank" rel="noreferrer" className="download-tile"><Layers3 /><div><b>Project ZIP</b><small>Toàn bộ tài nguyên dự án</small></div></a>}
          {task.scriptUrl && <a href={task.scriptUrl} target="_blank" rel="noreferrer" className="download-tile"><PencilLine /><div><b>Kịch bản (MD)</b><small>Approved script</small></div></a>}
          {task.narrationUrl && <a href={task.narrationUrl} target="_blank" rel="noreferrer" className="download-tile"><CirclePlay /><div><b>Narration (WAV)</b><small>File giọng đọc</small></div></a>}
          {task.subtitleUrl && <a href={task.subtitleUrl} target="_blank" rel="noreferrer" className="download-tile"><Copy /><div><b>Phụ đề (SRT)</b><small>Subtitles</small></div></a>}
          {task.assetManifestUrl && <a href={task.assetManifestUrl} target="_blank" rel="noreferrer" className="download-tile"><Settings2 /><div><b>Render Manifest</b><small>JSON metadata</small></div></a>}
          {!task.outputPath && !task.projectArchiveUrl && !task.scriptUrl && <EmptyState title="Chưa có file để tải" description="Tài nguyên sẽ xuất hiện sau khi Gemini dựng xong video." />}
        </div>
      </div>}
    </div>
    {showYoutubePublish && task && <YouTubePublishModal task={task} onClose={() => setShowYoutubePublish(false)} onPublished={(result) => { setShowYoutubePublish(false); setMessage(`${result.message}. Video ID: ${result.videoId}`); }} />}
  </div>;
}

export function TikTokPublishModal({ task, onClose, onPublished }) {
  const [creator, setCreator] = useState(null);
  const [form, setForm] = useState({
    consent: false,
    privacyLevel: 'SELF_ONLY',
    title: `${task.caption || task.title} ${task.hashtags || ''}`.trim(),
    disableComment: false,
    disableDuet: false,
    disableStitch: false,
  });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  useEffect(() => {
    api('/api/tiktok/creator-info').then((value) => {
      setCreator(value);
      const options = value.privacyLevelOptions || [];
      setForm((current) => ({
        ...current,
        privacyLevel: options.includes('SELF_ONLY') ? 'SELF_ONLY' : (options[0] || ''),
        disableComment: value.commentDisabled,
        disableDuet: value.duetDisabled,
        disableStitch: value.stitchDisabled,
      }));
    }).catch((reason) => setError(reason.message));
  }, []);
  const submit = async (event) => {
    event.preventDefault(); setBusy(true); setError('');
    try { onPublished(await api(`/api/tasks/${task.id}/publish/tiktok`, { method: 'POST', body: form })); }
    catch (reason) { setError(reason.message); }
    finally { setBusy(false); }
  };
  const privacyLabels = {
    PUBLIC_TO_EVERYONE: 'Mọi người',
    MUTUAL_FOLLOW_FRIENDS: 'Bạn bè theo dõi lẫn nhau',
    FOLLOWER_OF_CREATOR: 'Người theo dõi',
    SELF_ONLY: 'Chỉ mình tôi',
  };
  return <div className="modal-backdrop"><form className="modal tiktok-modal" onSubmit={submit}>
    <div className="modal-head"><div><span>TIKTOK DIRECT POST</span><h2>Duyệt & đăng video</h2></div><button type="button" onClick={onClose}><X /></button></div>
    {error && <div className="form-error">{error}</div>}
    {!creator && !error ? <Loader label="Đang lấy quyền đăng từ TikTok" /> : creator && <>
      <div className="creator-card"><div><b>@{creator.username || creator.nickname || 'tiktok'}</b><small>Tối đa {creator.maxVideoPostDurationSec || 60} giây • TikTok quyết định quyền hiển thị hợp lệ</small></div><ShieldCheck /></div>
      <label>Caption TikTok<textarea rows="5" maxLength="2200" value={form.title} onChange={(e) => setForm({ ...form, title: e.target.value })} /></label>
      <label>Quyền riêng tư<select required value={form.privacyLevel} onChange={(e) => setForm({ ...form, privacyLevel: e.target.value })}>{(creator.privacyLevelOptions || []).map((value) => <option value={value} key={value}>{privacyLabels[value] || value}</option>)}</select></label>
      <div className="tiktok-toggles">
        <label><input type="checkbox" checked={form.disableComment} disabled={creator.commentDisabled} onChange={(e) => setForm({ ...form, disableComment: e.target.checked })} /> Tắt bình luận</label>
        <label><input type="checkbox" checked={form.disableDuet} disabled={creator.duetDisabled} onChange={(e) => setForm({ ...form, disableDuet: e.target.checked })} /> Tắt Duet</label>
        <label><input type="checkbox" checked={form.disableStitch} disabled={creator.stitchDisabled} onChange={(e) => setForm({ ...form, disableStitch: e.target.checked })} /> Tắt Stitch</label>
      </div>
      <label className="consent-check"><input type="checkbox" checked={form.consent} onChange={(e) => setForm({ ...form, consent: e.target.checked })} /><span><b>Tôi đã xem video và đồng ý gửi video này tới TikTok.</b><small>App chưa audit có thể chỉ đăng ở chế độ “Chỉ mình tôi”. TikTok vẫn kiểm duyệt nội dung sau khi nhận.</small></span></label>
      <button className="button dark full" disabled={busy || !form.consent || !form.privacyLevel}>{busy ? 'Đang truyền video...' : 'Duyệt & gửi TikTok'} <ArrowRight /></button>
    </>}
  </form></div>;
}

function CampaignModal({ onClose, onCreated }) {
  const [idea, setIdea] = useState('');
  const [showAdvanced, setShowAdvanced] = useState(false);
  const [form, setForm] = useState(campaignDefaults);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const submit = async (event) => {
    event.preventDefault(); setBusy(true); setError('');
    try {
      const normalizedIdea = idea.trim();
      const created = await api('/api/campaigns', {
        method: 'POST',
        body: {
          ...form,
          name: form.name.trim() || normalizedIdea.slice(0, 160),
          theme: normalizedIdea,
          description: form.description.trim() || `Series do AI lập từ ý tưởng: ${normalizedIdea}`,
          episodeCount: Number(form.episodeCount),
          targetDurationSeconds: Number(form.targetDurationSeconds),
          nextRunAt: form.productionEnabled && form.nextRunAt ? form.nextRunAt : null,
        },
      });
      const episodes = await api(`/api/campaigns/${created.id}/ai-series`, { method: 'POST' });
      onCreated(created, episodes);
    } catch (reason) { setError(reason.message); }
    finally { setBusy(false); }
  };
  return <div className="modal-backdrop"><form className="modal campaign-modal" onSubmit={submit}>
    <div className="modal-head"><div><span>ONE-CLICK SERIES</span><h2>Tạo campaign bằng AI</h2></div><button type="button" onClick={onClose}><X /></button></div>
    {error && <div className="form-error">{error}</div>}
    <label>Ý tưởng series<textarea autoFocus required rows="5" maxLength="500" value={idea} onChange={(e) => setIdea(e.target.value)} placeholder="Ví dụ: Series chó cảnh sát Bobo giúp trẻ học kỹ năng an toàn, mỗi tập là một nhiệm vụ mới." /></label>
    <div className="one-click-note"><Sparkles /><div><b>AI tự làm phần còn lại</b><span>Tạo Campaign, Series Bible và {form.episodeCount} tập nháp nội dung. Bạn chỉ cần chọn tập và bấm Accept để dựng video.</span></div></div>
    <button type="button" className="advanced-toggle" onClick={() => setShowAdvanced(!showAdvanced)}><Settings2 /> {showAdvanced ? 'Ẩn tùy chỉnh' : 'Tùy chỉnh nâng cao (không bắt buộc)'}</button>
    {showAdvanced && <div className="advanced-campaign-fields">
      <label>Tên campaign<input maxLength="160" value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} placeholder="Để trống để AI dùng tên ý tưởng" /></label>
      <label>Khán giả mục tiêu<input maxLength="160" value={form.audience} onChange={(e) => setForm({ ...form, audience: e.target.value })} placeholder="Ví dụ: Trẻ em 7-11 tuổi và phụ huynh" /></label>
      <div className="form-grid"><label>Số tập<input type="number" min="1" max="30" value={form.episodeCount} onChange={(e) => setForm({ ...form, episodeCount: e.target.value })} /></label><label>Thời lượng mỗi tập<select value={form.targetDurationSeconds} onChange={(e) => setForm({ ...form, targetDurationSeconds: e.target.value })}><option value="60">60 giây / khoảng 6 cảnh</option><option value="90">90 giây / khoảng 9 cảnh</option><option value="180">3 phút / khoảng 18 cảnh</option></select></label></div>
      <div className="form-grid"><label>Phong cách hình ảnh<input maxLength="240" value={form.visualStyle} onChange={(e) => setForm({ ...form, visualStyle: e.target.value })} placeholder="Cinematic 3D animation..." /></label><label>Nhân vật xuyên suốt<input maxLength="240" value={form.characterDescription} onChange={(e) => setForm({ ...form, characterDescription: e.target.value })} placeholder="Chó cảnh sát Bobo..." /></label></div>
      <RenderProfileFields form={form} setForm={setForm} />
    </div>}
    <button className="button dark full" disabled={busy || !idea.trim()}>{busy ? 'AI đang lập campaign và các tập...' : 'Generate Campaign'} <WandSparkles /></button>
  </form></div>;
}

function CampaignsPage() {
  const navigate = useNavigate();
  const [data, setData] = useState(null);
  const [page, setPage] = useState(0);
  const [query, setQuery] = useState('');
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');
  const [showCreate, setShowCreate] = useState(false);
  const [busyCampaign, setBusyCampaign] = useState(null);
  const load = useCallback(() => {
    const params = new URLSearchParams({ page, size: 12, query });
    api(`/api/campaigns?${params}`).then(setData).catch((reason) => setError(reason.message));
  }, [page, query]);
  useEffect(() => { load(); }, [load]);
  const generateEpisodes = async (campaign) => {
    setError(''); setMessage(''); setBusyCampaign(campaign.id);
    try {
      const episodes = await api(`/api/campaigns/${campaign.id}/episodes`, { method: 'POST' });
      setMessage(`Đã chuẩn bị ${episodes.length} tập cho “${campaign.name}”.`);
      load();
    } catch (reason) { setError(reason.message); }
    finally { setBusyCampaign(null); }
  };
  const planSeries = async (campaign) => {
    setError(''); setMessage(''); setBusyCampaign(campaign.id);
    try { await api(`/api/campaigns/${campaign.id}/plan`, { method: 'POST' }); setMessage(`Series Bible cho “${campaign.name}” đã sẵn sàng.`); load(); }
    catch (reason) { setError(reason.message); }
    finally { setBusyCampaign(null); }
  };
  const generateAiSeries = async (campaign) => {
    setError(''); setMessage(''); setBusyCampaign(campaign.id);
    try {
      const episodes = await api(`/api/campaigns/${campaign.id}/ai-series`, { method: 'POST' });
      setMessage(`AI ?? l?p Series Bible v? chu?n b? ${episodes.length} t?p cho ?${campaign.name}?.`);
      load();
    } catch (reason) { setError(reason.message); }
    finally { setBusyCampaign(null); }
  };
  const produceNext = async (campaign) => {
    setError(''); setMessage(''); setBusyCampaign(campaign.id);
    try { const task = await api(`/api/campaigns/${campaign.id}/produce-next`, { method: 'POST' }); setMessage(`Đang dựng ${task.title}. Video sẽ về trạng thái chờ duyệt.`); load(); }
    catch (reason) { setError(reason.message); }
    finally { setBusyCampaign(null); }
  };
  const toggleAutomation = async (campaign) => {
    setError(''); setBusyCampaign(campaign.id);
    const enabled = !campaign.productionEnabled;
    try {
      await api(`/api/campaigns/${campaign.id}`, { method: 'PUT', body: {
        name: campaign.name, theme: campaign.theme, description: campaign.description,
        episodeCount: campaign.episodeCount, targetDurationSeconds: campaign.targetDurationSeconds,
        visualStyle: campaign.visualStyle, characterDescription: campaign.characterDescription,
        characterImageUrl: campaign.characterImageUrl, characterReferencePrompt: campaign.characterReferencePrompt,
        audioMode: campaign.audioMode, videoProvider: campaign.videoProvider,
        aspectRatio: campaign.aspectRatio, renderQuality: campaign.renderQuality,
        audience: campaign.audience, status: campaign.status,
        cadence: enabled && campaign.cadence === 'MANUAL' ? 'DAILY' : campaign.cadence,
        productionEnabled: enabled, nextRunAt: enabled ? new Date().toISOString().slice(0, 19) : null,
      } });
      setMessage(enabled ? 'Đã bật lịch sản xuất bản nháp.' : 'Đã tạm dừng lịch tự động.');
      load();
    } catch (reason) { setError(reason.message); }
    finally { setBusyCampaign(null); }
  };
  const remove = async (campaign) => {
    if (!window.confirm(`Xóa campaign “${campaign.name}”? Các video đã tạo vẫn được giữ lại.`)) return;
    try { await api(`/api/campaigns/${campaign.id}`, { method: 'DELETE' }); load(); }
    catch (reason) { setError(reason.message); }
  };
  return <div className="page campaigns-page">
    <section className="page-intro"><div><span>CONTENT ENGINE</span><h2>Một chủ đề, cả một series.</h2><p>Tạo chuỗi video nhất quán về nhân vật, hình ảnh và nhịp kể; mỗi tập vẫn được kiểm chứng và duyệt riêng.</p></div><button className="button dark" onClick={() => setShowCreate(true)}><Plus /> Tạo campaign</button></section>
    {error && <div className="alert error">{error}</div>}{message && <div className="alert success">{message}</div>}
    <section className="campaign-hero"><Layers3 /><div><b>Gemini + Research + Motion Pipeline</b><span>Script dài đến 10 phút, ảnh minh họa có nhân vật nhất quán, Ken Burns và chuyển cảnh xfade.</span></div><button className="button light" onClick={() => navigate('/videos')}>Mở thư viện <ArrowRight /></button></section>
    <section className="filter-bar"><div className="search-field"><Search /><input value={query} onChange={(e) => { setQuery(e.target.value); setPage(0); }} placeholder="Tìm campaign hoặc chủ đề..." /></div></section>
    {!data ? <Loader /> : data.content.length ? <><section className="campaign-grid">{data.content.map((campaign) => <article className="campaign-card" key={campaign.id}>
      <div className="campaign-card-head"><span className={`campaign-status ${campaign.status}`}>{campaign.status}</span><button onClick={() => remove(campaign)}><Trash2 /></button></div>
      <Link to={`/campaigns/${campaign.id}`}><h3>{campaign.name}</h3></Link><p>{campaign.description || campaign.theme}</p><div className="campaign-theme">{campaign.theme}</div>
      <div className="campaign-metrics"><span><Layers3 /><b>{campaign.episodeCount}</b><small>TẬP</small></span><span><Clock3 /><b>{campaign.targetDurationSeconds}s</b><small>MỖI TẬP</small></span><span><UserRound /><b>{campaign.ownerName}</b><small>CHỦ SỞ HỮU</small></span></div>
      <div className="campaign-plan-state"><span className={campaignProvider(campaign) === 'gemini' ? 'ready' : ''}><Bot /> Provider: {campaignProvider(campaign)}</span><span><CalendarDays /> {campaign.productionEnabled ? `${campaign.cadence} · ${campaign.nextRunAt?.replace('T', ' ').slice(0, 16)}` : 'Lịch đang tắt'}</span></div>
      <div className="campaign-action-grid"><Link className="primary" to={`/campaigns/${campaign.id}`}><Check /> Chọn campaign & duyệt tập</Link><button disabled={busyCampaign === campaign.id} onClick={() => generateAiSeries(campaign)}><RefreshCw /> Lập lại nội dung AI</button><button disabled={busyCampaign === campaign.id || campaign.status === 'COMPLETED'} onClick={() => toggleAutomation(campaign)}><CalendarDays /> {campaign.productionEnabled ? 'Tạm dừng lịch' : 'Bật lịch tạo draft'}</button></div>
    </article>)}</section><Pagination page={data.number} totalPages={data.totalPages} onChange={setPage} /></> : <EmptyState icon={Layers3} title="Chưa có campaign" description="Tạo series đầu tiên để sản xuất nội dung đều đặn." action={<button className="button dark" onClick={() => setShowCreate(true)}><Plus /> Tạo campaign</button>} />}
    {showCreate && <CampaignModal onClose={() => setShowCreate(false)} onCreated={(campaign) => { setShowCreate(false); navigate(`/campaigns/${campaign.id}`); }} />}
  </div>;
}

function CampaignDetailPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const [campaign, setCampaign] = useState(null);
  const [episodes, setEpisodes] = useState([]);
  const [description, setDescription] = useState('');
  const [file, setFile] = useState(null);
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');
  const [busy, setBusy] = useState('');
  const load = useCallback(() => {
    Promise.all([api(`/api/campaigns/${id}`), api(`/api/campaigns/${id}/episodes`)]).then(([row, plannedEpisodes]) => {
      setCampaign(row);
      setEpisodes(plannedEpisodes);
      setDescription(row.characterReferencePrompt || row.characterDescription || '');
    }).catch((reason) => setError(reason.message));
  }, [id]);
  useEffect(() => { load(); }, [load]);

  const generateCharacter = async (event) => {
    event.preventDefault(); setError(''); setMessage(''); setBusy('generate');
    try {
      const updated = await api(`/api/campaigns/${id}/generate-character`, {
        method: 'POST',
        body: { description },
      });
      setCampaign(updated);
      setMessage('Da tao lai anh nhan vat. Cac tap TODO/FAILED se dung reference moi.');
    } catch (reason) { setError(reason.message); }
    finally { setBusy(''); }
  };
  const acceptEpisode = async (episode) => {
    setError(''); setMessage(''); setBusy(`episode-${episode.id}`);
    try {
      await api(`/api/tasks/${episode.id}/generate`, { method: 'POST' });
      setMessage(`Đã accept “${episode.title}”. Worker đang dựng nhiều cảnh; video hoàn tất sẽ ở trạng thái Chờ duyệt.`);
      await load();
    } catch (reason) { setError(reason.message); }
    finally { setBusy(''); }
  };
  const uploadCharacter = async (event) => {
    event.preventDefault(); setError(''); setMessage(''); setBusy('upload');
    try {
      const form = new FormData();
      form.append('file', file);
      form.append('description', description);
      const updated = await apiForm(`/api/campaigns/${id}/character-image`, form);
      setCampaign(updated);
      setMessage('Da upload va luu anh nhan vat cho campaign.');
    } catch (reason) { setError(reason.message); }
    finally { setBusy(''); }
  };

  if (!campaign && !error) return <Loader />;
  return <div className="page campaign-detail-page">
    <button className="back-link" onClick={() => navigate('/campaigns')}><ArrowLeft /> Quay lai Campaign</button>
    {error && <div className="alert error">{error}</div>}{message && <div className="alert success">{message}</div>}
    {campaign && <><section className="page-intro"><div><span>CAMPAIGN REVIEW</span><h2>{campaign.name}</h2><p>{campaign.theme}</p></div><span className={`campaign-status ${campaign.status}`}>{campaign.status}</span></section>
      <section className="pipeline-truth">
        <article><Bot /><div><b>Nội dung · {campaignProvider(campaign)}</b><span>{campaignProvider(campaign) === 'gemini' ? 'Gemini đã lập Series Bible và nội dung từng tập.' : 'Campaign này chưa được Gemini xử lý; hãy lập lại nội dung AI.'} Research Agent kiểm tra nguồn khi dựng.</span></div></article>
        <article><Image /><div><b>Nhiều cảnh, không phải một ảnh</b><span>{campaign.targetDurationSeconds}s dự kiến khoảng {Math.max(4, Math.ceil(campaign.targetDurationSeconds / 10))} cảnh. AI ảnh thiếu cấu hình sẽ fallback sang visual offline cho từng cảnh.</span></div></article>
        <article><Film /><div><b>Google Flow / Veo</b><span>Google Login không phải Veo. Chỉ dùng Veo khi server có endpoint và API key riêng; nếu chưa có sẽ dùng Ken Burns + xfade.</span></div></article>
      </section>
      <section className="episode-review">
        <div className="section-heading"><div><span>AI EPISODE PLAN</span><h2>Chọn tập rồi Accept để dựng.</h2><p>Nội dung được tạo trước để bạn xem; không tự đăng và không bỏ qua bước review.</p></div></div>
        <div className="episode-list">{episodes.map((episode) => <article key={episode.id}>
          <span className="episode-number">{String(episode.episodeNumber).padStart(2, '0')}</span>
          <div><small>{statusLabels[episode.status] || episode.status}</small><h3>{episode.title}</h3><p>{episode.description || episode.topic}</p><em>{episode.targetDurationSeconds}s · {Math.max(4, Math.ceil(episode.targetDurationSeconds / 10))} cảnh dự kiến</em></div>
          {['TODO', 'FAILED'].includes(episode.status)
            ? <button className="button dark" disabled={busy === `episode-${episode.id}`} onClick={() => acceptEpisode(episode)}>{busy === `episode-${episode.id}` ? 'Đang gửi worker...' : 'Accept & tạo video'} <WandSparkles /></button>
            : <Link className="button outline" to={`/videos/${episode.id}`}>Mở Video Studio <ArrowRight /></Link>}
        </article>)}</div>
      </section>
      <section className="character-studio">
        <article className="character-preview">
          <div className="panel-label"><span>Nhân vật đại diện</span><Image /></div>
          {campaign.characterImageUrl ? <img src={campaign.characterImageUrl} alt="Character reference sheet" /> : <div className="character-empty"><Sparkles /><b>Chua co reference sheet</b><span>Tao bang AI hoac upload anh thu cong de giu nhan vat nhat quan.</span></div>}
          <div><b>{campaign.characterDescription || 'Chua co mo ta nhan vat'}</b><small>{campaign.characterReferencePrompt || 'Prompt se duoc luu sau khi tao hoac upload.'}</small></div>
        </article>
        <div className="character-tools">
          <form className="editor-panel" onSubmit={generateCharacter}>
            <div className="editor-head"><div><span>AI REFERENCE</span><h2>Tạo lại nhân vật bằng AI</h2></div><WandSparkles /></div>
            <label>Mo ta nhan vat<textarea rows="5" maxLength="700" required value={description} onChange={(e) => setDescription(e.target.value)} placeholder="Be banh bao trang Bobo, ma hong, deo tui xach mau xam tron" /></label>
            <button className="button dark full" disabled={busy === 'generate'}>{busy === 'generate' ? 'Dang tao...' : 'Tao lai nhan vat bang AI'} <WandSparkles /></button>
          </form>
          <form className="editor-panel" onSubmit={uploadCharacter}>
            <div className="editor-head"><div><span>MANUAL UPLOAD</span><h2>Upload ảnh reference</h2></div><Upload /></div>
            <label>Anh tu may tinh<input type="file" accept="image/*" required onChange={(e) => setFile(e.target.files?.[0] || null)} /></label>
            <button className="button outline full" disabled={busy === 'upload' || !file}>{busy === 'upload' ? 'Dang upload...' : 'Upload len Cloudinary'} <Upload /></button>
          </form>
        </div>
      </section></>}
  </div>;
}

function CalendarPage() {
  const [items, setItems] = useState(null);
  const [tasks, setTasks] = useState([]);
  const [page, setPage] = useState(0);
  const [showForm, setShowForm] = useState(false);
  const [form, setForm] = useState({ taskId: '', platform: 'TIKTOK', status: 'PENDING', scheduledAt: '', note: '' });
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');
  const load = useCallback(() => Promise.all([api(`/api/publications?page=${page}&size=20`), api('/api/tasks?size=50')]).then(([rows, taskRows]) => { setItems(rows); setTasks(taskRows.content); }).catch((reason) => setError(reason.message)), [page]);
  useEffect(() => { load(); }, [load]);
  const submit = async (event) => { event.preventDefault(); try { await api('/api/publications', { method: 'POST', body: { ...form, taskId: Number(form.taskId), scheduledAt: form.scheduledAt || null } }); setShowForm(false); load(); } catch (reason) { setError(reason.message); } };
  const approve = async (item) => {
    setError(''); setMessage('');
    try {
      await api(`/api/publications/${item.id}/approve`, { method: 'POST', body: { reviewed: true } });
      setMessage('Đã duyệt lịch. Mở Video Studio để xác nhận cài đặt và gửi lên nền tảng.');
      await load();
    } catch (reason) { setError(reason.message); }
  };
  return <div className="page">
    <section className="page-intro"><div><span>PUBLISHING PLAN</span><h2>Lịch nội dung rõ ràng.</h2><p>`PENDING` là lịch đang chờ bạn xem video; hệ thống không tự đăng khi chưa duyệt.</p></div><button className="button dark" onClick={() => setShowForm(true)}><Plus /> Thêm lịch</button></section>
    {error && <div className="alert error">{error}</div>}{message && <div className="alert success">{message}</div>}
    <div className="review-flow-note"><ShieldCheck /><div><b>Quy trình an toàn</b><span>Dựng video → kiểm tra nội dung → duyệt lịch → mở Studio và xác nhận gửi lên TikTok/YouTube.</span></div></div>
    {!items ? <Loader /> : items.content.length ? <><div className="schedule-list">{items.content.map((item) => <article className={item.status === 'PENDING' && item.scheduledAt && new Date(item.scheduledAt) < new Date() ? 'overdue' : ''} key={item.id}>
      <div className="schedule-date"><CalendarDays /><b>{item.scheduledAt?.slice(0, 10) || 'Chưa đặt ngày'}</b><span>{item.scheduledAt?.slice(11, 16) || '--:--'}</span></div>
      <div><span className={`platform ${item.platform}`}>{item.platform}</span><h3>{item.taskTitle}</h3><p>{item.note || 'Không có ghi chú'}</p><small>{item.hasVideo ? 'Video đã dựng xong' : 'Chưa có video để duyệt'}</small></div>
      <div className="schedule-actions"><span className={`status publication-${item.status}`}>{item.status}</span>
        {item.status === 'PENDING' && item.hasVideo && <button onClick={() => approve(item)}><Check /> Duyệt lịch</button>}
        {item.status === 'PENDING' && !item.hasVideo && <Link to={`/videos/${item.taskId}`}><WandSparkles /> Dựng video</Link>}
        {item.status === 'READY' && <Link to={`/videos/${item.taskId}`}><Send /> Mở Studio & đăng</Link>}
      </div>
    </article>)}</div><Pagination page={items.number} totalPages={items.totalPages} onChange={setPage} /></> : <EmptyState icon={CalendarDays} title="Chưa có lịch đăng" description="Thêm lịch sau khi video đã được duyệt." />}
    {showForm && <div className="modal-backdrop"><form className="modal" onSubmit={submit}><div className="modal-head"><div><span>NEW SCHEDULE</span><h2>Thêm lịch đăng</h2></div><button type="button" onClick={() => setShowForm(false)}><X /></button></div><label>Video<select required value={form.taskId} onChange={(e) => setForm({ ...form, taskId: e.target.value })}><option value="">Chọn video</option>{tasks.map((task) => <option value={task.id} key={task.id}>{task.title}</option>)}</select></label><div className="form-grid"><label>Nền tảng<select value={form.platform} onChange={(e) => setForm({ ...form, platform: e.target.value })}><option>TIKTOK</option><option>YOUTUBE</option><option>OTHER</option></select></label><label>Ngày giờ<input required type="datetime-local" value={form.scheduledAt} onChange={(e) => setForm({ ...form, scheduledAt: e.target.value })} /></label></div><label>Ghi chú<textarea rows="4" maxLength="1000" value={form.note} onChange={(e) => setForm({ ...form, note: e.target.value })} /></label><button className="button dark full">Lưu lịch <ArrowRight /></button></form></div>}
  </div>;
}

function AdminUsersPage() {
  const [data, setData] = useState(null);
  const [page, setPage] = useState(0);
  const [query, setQuery] = useState('');
  const [error, setError] = useState('');
  const load = useCallback(() => api(`/api/admin/users?page=${page}&size=20&query=${encodeURIComponent(query)}`).then(setData).catch((reason) => setError(reason.message)), [page, query]);
  useEffect(() => { load(); }, [load]);
  const update = async (user, changes) => { try { await api(`/api/admin/users/${user.id}`, { method: 'PUT', body: { displayName: user.displayName, role: user.role, enabled: user.enabled, ...changes } }); load(); } catch (reason) { setError(reason.message); } };
  return <div className="page"><section className="page-intro"><div><span>ADMIN CONTROL</span><h2>Người dùng & phân quyền.</h2><p>Quản lý quyền truy cập workspace và trạng thái tài khoản.</p></div></section>{error && <div className="alert error">{error}</div>}<section className="filter-bar"><div className="search-field"><Search /><input value={query} onChange={(e) => { setQuery(e.target.value); setPage(0); }} placeholder="Tìm email hoặc tên..." /></div></section>{!data ? <Loader /> : <><div className="user-table"><div className="user-row header"><span>Người dùng</span><span>Loại đăng nhập</span><span>Vai trò</span><span>Trạng thái</span></div>{data.content.map((user) => <div className="user-row" key={user.id}><div><span className="avatar">{user.displayName.slice(0, 1).toUpperCase()}</span><span><b>{user.displayName}</b><small>{user.email}</small></span></div><span>{user.provider}</span><select value={user.role} onChange={(e) => update(user, { role: e.target.value })}><option value="USER">USER</option><option value="ADMIN">ADMIN</option></select><button className={user.enabled ? 'enabled-pill' : 'disabled-pill'} onClick={() => update(user, { enabled: !user.enabled })}>{user.enabled ? 'Đang hoạt động' : 'Đã khóa'}</button></div>)}</div><Pagination page={data.number} totalPages={data.totalPages} onChange={setPage} /></>}</div>;
}

export default function App() {
  return <Routes>
    <Route path="/login" element={<LoginPage />} />
    <Route element={<Protected />}><Route element={<AppShell />}>
      <Route index element={<UserHomePage />} />
      <Route path="videos" element={<VideosPage />} />
      <Route path="videos/:id" element={<VideoStudioPage />} />
      <Route path="campaigns" element={<CampaignsPage />} />
      <Route path="campaigns/:id" element={<CampaignDetailPage />} />
      <Route path="research" element={<ResearchNotebookPage />} />
      <Route path="calendar" element={<CalendarPage />} />
      <Route path="profile" element={<ProfilePage />} />
    </Route></Route>
    <Route element={<Protected admin />}><Route element={<AdminShell />}><Route path="admin" element={<AdminDashboardPage />} /><Route path="admin/users" element={<AdminUsersPage />} /><Route path="admin/feedback" element={<AdminFeedbackPage />} /></Route></Route>
    <Route path="*" element={<Navigate to="/" replace />} />
  </Routes>;
}
