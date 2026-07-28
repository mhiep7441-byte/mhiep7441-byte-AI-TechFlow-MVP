import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import {
  ArrowRight, BarChart3, BookOpen, Bot, CalendarClock, CheckCircle2, CirclePlay, Copy,
  ExternalLink, Film, Gauge, Layers3, Search, Send, ShieldCheck, Sparkles, Star,
  UserRound, UsersRound,
} from 'lucide-react';
import { api } from './api';
import { useAuth } from './AuthContext';

function Loading() {
  return <div className="loader-state"><span className="spinner" /><p>Đang tải dữ liệu</p></div>;
}

function Metric({ icon: Icon, label, value, hint, tone = '' }) {
  return <article className={`ops-metric ${tone}`}><div><Icon /><span>{label}</span></div><strong>{value ?? 0}</strong><small>{hint}</small></article>;
}

export function UserHomePage() {
  const { user } = useAuth();
  const [data, setData] = useState(null);
  const [error, setError] = useState('');
  useEffect(() => {
    Promise.all([
      api('/api/dashboard'),
      api('/api/tasks?size=5'),
      api('/api/campaigns?size=3'),
    ]).then(([stats, tasks, campaigns]) => setData({ stats, tasks: tasks.content, campaigns: campaigns.content }))
      .catch((reason) => setError(reason.message));
  }, []);
  if (!data && !error) return <Loading />;
  return <div className="page workspace-page">
    {error && <div className="alert error">{error}</div>}
    <section className="workspace-hero">
      <div><span>CREATOR WORKSPACE</span><h2>Chào {user.displayName}.<br />Hôm nay mình sản xuất gì?</h2><p>Từ ý tưởng, nghiên cứu, nhiều cảnh đến bản nháp cần duyệt — tất cả trong một quy trình.</p><div><Link className="button light" to="/videos?new=1"><Sparkles /> Tạo video</Link><Link className="button ghost-light" to="/campaigns"><Layers3 /> Xây series</Link></div></div>
      <div className="workspace-orbit"><div><Bot /><b>AI Studio</b><small>Research → Script → Scenes → Review</small></div></div>
    </section>
    <section className="ops-grid personal">
      <Metric icon={Film} label="Video của tôi" value={data?.stats.total} hint="Toàn bộ thư viện cá nhân" />
      <Metric icon={Bot} label="Đang dựng" value={data?.stats.processing} hint="Worker đang xử lý" tone="violet" />
      <Metric icon={ShieldCheck} label="Cần tôi duyệt" value={data?.stats.review} hint="Không tự xuất bản" tone="amber" />
      <Metric icon={CheckCircle2} label="Hoàn tất" value={data?.stats.done} hint="Đã sẵn sàng sử dụng" tone="green" />
    </section>
    <section className="workspace-columns">
      <article className="workspace-panel">
        <header><div><span>RECENT DRAFTS</span><h3>Video gần đây</h3></div><Link to="/videos">Xem tất cả <ArrowRight /></Link></header>
        <div className="workspace-rows">{data?.tasks.map((task) => <Link to={`/videos/${task.id}`} key={task.id}><div className="mini-poster">{task.outputPath ? <video muted src={task.outputPath} /> : <Film />}</div><span><b>{task.title}</b><small>{task.status.replaceAll('_', ' ')}</small></span><ArrowRight /></Link>)}{!data?.tasks.length && <p className="workspace-empty">Chưa có video. Bắt đầu bằng một chủ đề bạn hiểu rõ.</p>}</div>
      </article>
      <article className="workspace-panel">
        <header><div><span>SERIES ENGINE</span><h3>Campaign của tôi</h3></div><Link to="/campaigns">Quản lý <ArrowRight /></Link></header>
        <div className="campaign-mini-grid">{data?.campaigns.map((campaign) => <div key={campaign.id}><span>{campaign.status}</span><h4>{campaign.name}</h4><p>{campaign.episodeCount} tập · {campaign.cadence === 'MANUAL' ? 'Thủ công' : campaign.cadence}</p></div>)}{!data?.campaigns.length && <p className="workspace-empty">Chưa có series. Tạo series để sản xuất đều theo lịch.</p>}</div>
      </article>
    </section>
  </div>;
}

export function AdminDashboardPage() {
  const [stats, setStats] = useState(null);
  const [error, setError] = useState('');
  useEffect(() => { api('/api/admin/dashboard').then(setStats).catch((reason) => setError(reason.message)); }, []);
  if (!stats && !error) return <Loading />;
  const max = Math.max(stats?.videos || 0, 1);
  const pipeline = [
    ['Đang dựng', stats?.generating || 0, '#7c5ce5'],
    ['Chờ duyệt', stats?.awaitingReview || 0, '#e5a93f'],
    ['Hoàn tất', stats?.completed || 0, '#2e9b68'],
    ['Thất bại', stats?.failed || 0, '#d75a69'],
  ];
  return <div className="page admin-dashboard">
    {error && <div className="alert error">{error}</div>}
    <section className="admin-heading"><div><span>ADMIN COMMAND CENTER</span><h2>Toàn cảnh vận hành.</h2><p>Số liệu toàn hệ thống chỉ xuất hiện trong khu vực quản trị.</p></div><div className="admin-live"><i /> SYSTEM LIVE</div></section>
    <section className="ops-grid">
      <Metric icon={UsersRound} label="Người dùng" value={stats?.users} hint={`${stats?.activeUsers || 0} tài khoản hoạt động`} />
      <Metric icon={Layers3} label="Campaign" value={stats?.campaigns} hint={`${stats?.automatedCampaigns || 0} đang chạy tự động`} tone="violet" />
      <Metric icon={Film} label="Video" value={stats?.videos} hint={`${stats?.awaitingReview || 0} bản nháp cần duyệt`} tone="amber" />
      <Metric icon={Send} label="Đã xuất bản" value={stats?.published} hint={`${stats?.publications || 0} lượt trong lịch`} tone="green" />
    </section>
    <section className="admin-grid">
      <article className="pipeline-health"><header><div><span>PIPELINE HEALTH</span><h3>Trạng thái sản xuất</h3></div><Gauge /></header><div className="health-bars">{pipeline.map(([label, value, color]) => <div key={label}><span><b>{label}</b><strong>{value}</strong></span><i><em style={{ width: `${Math.max(3, (value / max) * 100)}%`, background: color }} /></i></div>)}</div></article>
      <article className="admin-actions"><span>QUICK CONTROL</span><h3>Quản trị workspace</h3><Link to="/admin/users"><UsersRound /><div><b>Người dùng & phân quyền</b><small>Khóa tài khoản, đổi vai trò và kiểm soát truy cập.</small></div><ArrowRight /></Link><Link to="/campaigns"><CalendarClock /><div><b>Campaign automation</b><small>Theo dõi series theo giờ/ngày và bản nháp.</small></div><ArrowRight /></Link><Link to="/videos"><ShieldCheck /><div><b>Hàng đợi review</b><small>Kiểm tra video, nguồn và quality score.</small></div><ArrowRight /></Link></article>
    </section>
  </div>;
}

export function ProfilePage() {
  const { user, refresh } = useAuth();
  const [name, setName] = useState(user.displayName);
  const [connections, setConnections] = useState({ tiktok: null, youtube: null });
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const loadConnections = () => Promise.all([
    api('/api/tiktok/status').catch(() => ({ configured: false, connected: false })),
    api('/api/youtube/status').catch(() => ({ configured: false, connected: false })),
  ]).then(([tiktok, youtube]) => setConnections({ tiktok, youtube }));
  useEffect(() => { loadConnections(); }, []);
  const save = async (event) => {
    event.preventDefault(); setError('');
    try { await api('/api/auth/profile', { method: 'PUT', body: { displayName: name } }); await refresh(); setMessage('Đã cập nhật hồ sơ.'); }
    catch (reason) { setError(reason.message); }
  };
  const disconnectYoutube = async () => {
    try { await api('/api/youtube/connection', { method: 'DELETE' }); await loadConnections(); }
    catch (reason) { setError(reason.message); }
  };
  return <div className="page profile-page">
    <section className="page-intro"><div><span>ACCOUNT & CHANNELS</span><h2>Hồ sơ người dùng.</h2><p>Thông tin cá nhân và các kênh được kết nối bằng OAuth; secret không bao giờ hiển thị ở trình duyệt.</p></div></section>
    {error && <div className="alert error">{error}</div>}{message && <div className="alert success">{message}</div>}
    <section className="profile-grid">
      <form className="profile-card identity-card" onSubmit={save}><div className="large-avatar">{user.avatarUrl ? <img src={user.avatarUrl} alt="" /> : user.displayName.slice(0, 1).toUpperCase()}</div><span>IDENTITY</span><h3>{user.displayName}</h3><p>{user.email}</p><label>Tên hiển thị<input required maxLength="120" value={name} onChange={(event) => setName(event.target.value)} /></label><div className="identity-meta"><span><UserRound /> {user.role}</span><span><ShieldCheck /> {user.provider}</span></div><button className="button dark full">Lưu hồ sơ <ArrowRight /></button></form>
      <div className="channel-stack">
        <article className="profile-card channel-card tiktok"><header><div className="channel-logo">♪</div><div><span>TIKTOK</span><h3>{connections.tiktok?.connected ? connections.tiktok.displayName || 'Đã kết nối' : 'Chưa kết nối'}</h3></div></header><p>{connections.tiktok?.message || 'Đang đọc trạng thái...'}</p>{connections.tiktok?.configured && !connections.tiktok.connected && <a className="button dark" href="/api/tiktok/connect">Kết nối TikTok <ArrowRight /></a>}</article>
        <article className="profile-card channel-card youtube"><header><div className="channel-logo"><CirclePlay /></div><div><span>YOUTUBE</span><h3>{connections.youtube?.connected ? connections.youtube.channelTitle || 'Đã kết nối' : 'Chưa kết nối'}</h3></div></header><p>{connections.youtube?.message || 'Đang đọc trạng thái...'}</p>{connections.youtube?.configured && !connections.youtube.connected && <a className="button dark" href="/api/youtube/connect">Kết nối YouTube <ArrowRight /></a>}{connections.youtube?.connected && <button className="button outline" onClick={disconnectYoutube}>Ngắt kết nối</button>}</article>
      </div>
    </section>
  </div>;
}

export function YouTubePublishModal({ task, onClose, onPublished }) {
  const [form, setForm] = useState({
    title: task.title.slice(0, 100),
    description: `${task.caption || ''}\n\n${task.hashtags || ''}`.trim(),
    privacyStatus: 'private',
    madeForKids: false,
    consent: false,
  });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const submit = async (event) => {
    event.preventDefault(); setBusy(true); setError('');
    try { onPublished(await api(`/api/tasks/${task.id}/publish/youtube`, { method: 'POST', body: form })); }
    catch (reason) { setError(reason.message); }
    finally { setBusy(false); }
  };
  return <div className="modal-backdrop"><form className="modal youtube-modal" onSubmit={submit}><div className="modal-head"><div><span>YOUTUBE UPLOAD</span><h2>Duyệt & upload video</h2></div><button type="button" onClick={onClose}>×</button></div>{error && <div className="form-error">{error}</div>}<div className="creator-card"><div><b>Review-first upload</b><small>Project chưa audit có thể bị YouTube giới hạn video ở chế độ private.</small></div><CirclePlay /></div><label>Tiêu đề<input required maxLength="100" value={form.title} onChange={(event) => setForm({ ...form, title: event.target.value })} /></label><label>Mô tả<textarea rows="6" maxLength="5000" value={form.description} onChange={(event) => setForm({ ...form, description: event.target.value })} /></label><div className="form-grid"><label>Quyền riêng tư<select value={form.privacyStatus} onChange={(event) => setForm({ ...form, privacyStatus: event.target.value })}><option value="private">Riêng tư</option><option value="unlisted">Không công khai</option><option value="public">Công khai</option></select></label><label className="inline-check"><input type="checkbox" checked={form.madeForKids} onChange={(event) => setForm({ ...form, madeForKids: event.target.checked })} /> Nội dung dành cho trẻ em</label></div><label className="consent-check"><input type="checkbox" checked={form.consent} onChange={(event) => setForm({ ...form, consent: event.target.checked })} /><span><b>Tôi đã xem video, kiểm tra nguồn và đồng ý upload lên YouTube.</b><small>Đây là hành động chủ động; hệ thống không tự xuất bản video.</small></span></label><button className="button dark full" disabled={busy || !form.consent}>{busy ? 'Đang upload...' : 'Xác nhận upload'} <ArrowRight /></button></form></div>;
}

export function VideoFeedbackWidget({ task }) {
  const aspectOptions = ['Nội dung đúng', 'Hình ảnh đẹp', 'Giọng đọc tốt', 'Chuyển cảnh mượt', 'Dễ sử dụng'];
  const [form, setForm] = useState({ rating: 0, aspects: [], comment: '' });
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  useEffect(() => {
    api(`/api/tasks/${task.id}/feedback`).then((value) => {
      if (value) setForm({ rating: value.rating, aspects: value.aspects ? value.aspects.split(',') : [], comment: value.comment || '' });
    }).catch(() => {});
  }, [task.id]);
  const toggle = (aspect) => setForm((current) => ({
    ...current,
    aspects: current.aspects.includes(aspect)
      ? current.aspects.filter((value) => value !== aspect)
      : [...current.aspects, aspect],
  }));
  const submit = async (event) => {
    event.preventDefault(); setError(''); setMessage('');
    try {
      await api(`/api/tasks/${task.id}/feedback`, {
        method: 'PUT',
        body: { rating: form.rating, aspects: form.aspects.join(','), comment: form.comment },
      });
      setMessage('Cảm ơn bạn. Đánh giá này sẽ xuất hiện trong Admin Feedback.');
    } catch (reason) { setError(reason.message); }
  };
  return <section className="feedback-widget"><div><span>YOUR FEEDBACK</span><h3>Video này đạt kỳ vọng chưa?</h3><p>AI TechFlow dùng đánh giá này để tìm phần cần cải thiện.</p></div><form onSubmit={submit}>{error && <div className="form-error">{error}</div>}{message && <div className="alert success">{message}</div>}<div className="star-picker" aria-label="Mức độ hài lòng">{[1,2,3,4,5].map((star) => <button type="button" aria-label={`${star} sao`} className={form.rating >= star ? 'active' : ''} onClick={() => setForm({ ...form, rating: star })} key={star}><Star /></button>)}</div><div className="feedback-aspects">{aspectOptions.map((aspect) => <button type="button" className={form.aspects.includes(aspect) ? 'active' : ''} onClick={() => toggle(aspect)} key={aspect}>{aspect}</button>)}</div><textarea rows="3" maxLength="2000" placeholder="Điều gì cần sửa ở nội dung, hình ảnh hoặc trải nghiệm?" value={form.comment} onChange={(event) => setForm({ ...form, comment: event.target.value })} /><button className="button dark" disabled={!form.rating}>Gửi đánh giá <ArrowRight /></button></form></section>;
}

export function ResearchNotebookPage() {
  const [entries, setEntries] = useState(null);
  const [query, setQuery] = useState('');
  const [error, setError] = useState('');
  useEffect(() => { api('/api/research/notebook?limit=100').then(setEntries).catch((reason) => setError(reason.message)); }, []);
  if (!entries && !error) return <Loading />;
  const rows = (entries || []).filter((entry) =>
    `${entry.title} ${entry.topic} ${entry.summary}`.toLowerCase().includes(query.toLowerCase()));
  const copy = async (entry) => navigator.clipboard.writeText(
    `${entry.title}\n${entry.summary}\n\n${(entry.sources || []).map((source) => source.url).join('\n')}`);
  return <div className="page research-notebook"><section className="page-intro"><div><span>RESEARCH LIBRARY</span><h2>Notebook nguồn & kiến thức.</h2><p>Tập hợp research, claim và nguồn từ các video đã dựng để kiểm tra hoặc tái sử dụng ý tưởng.</p></div><Link className="button dark" to="/videos?new=1"><Sparkles /> Research video mới</Link></section>{error && <div className="alert error">{error}</div>}<section className="notebook-toolbar"><Search /><input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Tìm chủ đề, video hoặc dữ kiện..." /><span>{rows.length} ghi chú</span></section><section className="notebook-grid">{rows.map((entry) => <article className="notebook-card" key={entry.taskId}><header><span className={entry.mode === 'offline' ? 'offline' : ''}>{entry.mode}</span><small>{entry.updatedAt?.slice(0, 10)}</small></header><h3>{entry.title}</h3><p>{entry.summary || entry.topic}</p><div className="notebook-score"><span><ShieldCheck /> {entry.factCheckStatus}</span><span><Gauge /> {entry.qualityScore ?? '—'}/100</span><span><ExternalLink /> {(entry.sources || []).length} nguồn</span></div><div className="notebook-sources">{(entry.sources || []).slice(0, 4).map((source, index) => <a href={source.url} target="_blank" rel="noreferrer" key={`${source.id}-${index}`}><b>{source.title || source.publisher}</b><small>{source.publisher}</small><ExternalLink /></a>)}</div><footer><button onClick={() => copy(entry)}><Copy /> Copy research</button><Link to={`/videos/${entry.taskId}`}>Mở Video Studio <ArrowRight /></Link></footer></article>)}</section>{!rows.length && <div className="workspace-empty notebook-empty"><BookOpen /><b>Chưa có research phù hợp</b><span>Hãy dựng video có Research Agent để tạo ghi chú đầu tiên.</span></div>}</div>;
}

export function AdminFeedbackPage() {
  const [summary, setSummary] = useState(null);
  const [data, setData] = useState(null);
  const [rating, setRating] = useState('');
  const [page, setPage] = useState(0);
  const [error, setError] = useState('');
  const load = () => Promise.all([
    api('/api/admin/feedback/summary'),
    api(`/api/admin/feedback?page=${page}&size=20${rating ? `&rating=${rating}` : ''}`),
  ]).then(([nextSummary, nextData]) => { setSummary(nextSummary); setData(nextData); })
    .catch((reason) => setError(reason.message));
  useEffect(() => { load(); }, [page, rating]);
  if ((!summary || !data) && !error) return <Loading />;
  return <div className="page admin-feedback"><section className="admin-heading"><div><span>VOICE OF CUSTOMER</span><h2>Feedback Analytics.</h2><p>Đọc đánh giá thật để ưu tiên sửa nội dung, hình ảnh và trải nghiệm sản xuất.</p></div><div className="feedback-average"><Star /><strong>{summary?.average || 0}</strong><span>/ 5<br />{summary?.total || 0} đánh giá</span></div></section>{error && <div className="alert error">{error}</div>}<section className="rating-distribution">{[5,4,3,2,1].map((star) => { const count = summary?.distribution?.[star] || 0; const percent = summary?.total ? count / summary.total * 100 : 0; return <button className={String(star) === rating ? 'active' : ''} onClick={() => { setRating(String(star) === rating ? '' : String(star)); setPage(0); }} key={star}><span>{star} <Star /></span><i><em style={{ width: `${percent}%` }} /></i><b>{count}</b></button>; })}</section><section className="feedback-list">{data?.content.map((item) => <article key={item.id}><div className="feedback-user"><span className="avatar">{item.ownerName.slice(0,1).toUpperCase()}</span><div><b>{item.ownerName}</b><small>{item.ownerEmail}</small></div></div><div><Link to={`/videos/${item.taskId}`}>{item.taskTitle} <ArrowRight /></Link><div className="feedback-stars">{[1,2,3,4,5].map((star) => <Star className={item.rating >= star ? 'active' : ''} key={star} />)}</div><p>{item.comment || 'Không có nhận xét chi tiết.'}</p><small>{item.aspects || 'Chưa chọn tiêu chí'} · {item.updatedAt?.replace('T', ' ').slice(0,16)}</small></div></article>)}</section>{data?.totalPages > 1 && <div className="pagination"><button disabled={page === 0} onClick={() => setPage(page - 1)}>Trước</button><span>Trang {page + 1}/{data.totalPages}</span><button disabled={page + 1 >= data.totalPages} onClick={() => setPage(page + 1)}>Sau</button></div>}</div>;
}
