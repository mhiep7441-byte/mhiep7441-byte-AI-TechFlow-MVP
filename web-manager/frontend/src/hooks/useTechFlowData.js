import { useCallback, useEffect, useMemo, useState } from 'react';

async function api(url, options) {
  const response = await fetch(url, options);
  if (!response.ok) throw new Error((await response.text()) || `HTTP ${response.status}`);
  return response.status === 204 ? null : response.json();
}

const json = (method, body) => ({
  method,
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify(body),
});

export function useTechFlowData(refreshInterval = 5000) {
  const [tasks, setTasks] = useState([]);
  const [publications, setPublications] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const load = useCallback(async () => {
    try {
      const [taskRows, publicationRows] = await Promise.all([
        api('/api/tasks'),
        api('/api/publications'),
      ]);
      setTasks(taskRows);
      setPublications(publicationRows);
      setError('');
    } catch (reason) {
      setError(`Không thể tải dữ liệu: ${reason.message}`);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load();
    const timer = window.setInterval(load, refreshInterval);
    return () => window.clearInterval(timer);
  }, [load, refreshInterval]);

  const mutate = useCallback(async (url, options) => {
    try {
      const result = await api(url, options);
      await load();
      return result;
    } catch (reason) {
      setError(reason.message);
      throw reason;
    }
  }, [load]);

  const actions = useMemo(() => ({
    createTask: (task) => mutate('/api/tasks', json('POST', task)),
    updateTask: (task, status) => {
      // A generated video must be explicitly reviewed before it can be marked done.
      // Keep this guard in the shared action as well as in the card UI so a future
      // screen cannot accidentally bypass the review step.
      if (task.status === 'DRAFT_REQUIRES_REVIEW' && status !== 'DRAFT_REQUIRES_REVIEW') {
        const reason = new Error('Video đang chờ duyệt. Hãy mở bản xem trước và xác nhận đã duyệt.');
        setError(reason.message);
        return Promise.reject(reason);
      }
      return mutate(`/api/tasks/${task.id}`, json('PUT', { ...task, status }));
    },
    confirmReview: (task) => mutate(`/api/tasks/${task.id}/review`, { method: 'POST' }),
    deleteTask: (id) => mutate(`/api/tasks/${id}`, { method: 'DELETE' }),
    generateDraft: (id) => mutate(`/api/tasks/${id}/generate`, { method: 'POST' }),
    createPublication: (publication) => mutate('/api/publications', json('POST', publication)),
    deletePublication: (id) => mutate(`/api/publications/${id}`, { method: 'DELETE' }),
  }), [mutate]);

  const stats = useMemo(() => ({
    total: tasks.length,
    processing: tasks.filter((task) => ['IN_PROGRESS', 'GENERATING'].includes(task.status)).length,
    reviewing: tasks.filter((task) => task.status === 'DRAFT_REQUIRES_REVIEW').length,
    completed: tasks.filter((task) => task.status === 'DONE').length,
  }), [tasks]);

  return { tasks, publications, loading, error, stats, actions, reload: load };
}
