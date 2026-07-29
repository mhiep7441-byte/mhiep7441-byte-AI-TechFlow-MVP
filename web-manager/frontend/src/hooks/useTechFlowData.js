import { useCallback, useEffect, useMemo, useState } from 'react';
import { api } from '../api';

const json = (method, body) => ({ method, body });

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
    updateTask: (task, status) => mutate(`/api/tasks/${task.id}`, json('PUT', { ...task, status })),
    deleteTask: (id) => mutate(`/api/tasks/${id}`, { method: 'DELETE' }),
    generateDraft: (id) => mutate(`/api/tasks/${id}/generate`, { method: 'POST' }),
    renderVideo: (id) => mutate(`/api/tasks/${id}/render`, { method: 'POST' }),
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
