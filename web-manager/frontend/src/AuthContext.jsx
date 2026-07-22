import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { api, setCsrfToken } from './api';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [user, setUser] = useState(null);
  const [config, setConfig] = useState({ googleEnabled: false });
  const [loading, setLoading] = useState(true);

  const refresh = useCallback(async () => {
    try {
      const current = await api('/api/auth/me');
      setCsrfToken(current.csrfToken);
      setUser(current);
      return current;
    } catch (error) {
      if (error.status !== 401) throw error;
      setUser(null);
      return null;
    }
  }, []);

  useEffect(() => {
    let active = true;
    Promise.all([api('/api/auth/config'), refresh()])
      .then(([settings]) => active && setConfig(settings))
      .finally(() => active && setLoading(false));
    const expired = () => setUser(null);
    window.addEventListener('techflow:auth-expired', expired);
    return () => { active = false; window.removeEventListener('techflow:auth-expired', expired); };
  }, [refresh]);

  const login = useCallback(async (credentials) => {
    const current = await api('/api/auth/login', { method: 'POST', body: credentials });
    setCsrfToken(current.csrfToken);
    setUser(current);
    return current;
  }, []);

  const register = useCallback(async (details) => {
    const current = await api('/api/auth/register', { method: 'POST', body: details });
    setCsrfToken(current.csrfToken);
    setUser(current);
    return current;
  }, []);

  const logout = useCallback(async () => {
    await api('/api/auth/logout', { method: 'POST' });
    setCsrfToken('');
    setUser(null);
  }, []);

  const value = useMemo(() => ({ user, config, loading, login, register, logout, refresh }),
    [user, config, loading, login, register, logout, refresh]);
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const value = useContext(AuthContext);
  if (!value) throw new Error('useAuth must be used inside AuthProvider');
  return value;
}
