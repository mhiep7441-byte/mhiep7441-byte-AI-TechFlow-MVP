let csrfToken = '';

export function setCsrfToken(token) {
  csrfToken = token || '';
}

function cookieToken() {
  const row = document.cookie.split('; ').find((item) => item.startsWith('XSRF-TOKEN='));
  return row ? decodeURIComponent(row.split('=').slice(1).join('=')) : '';
}

export async function api(path, { method = 'GET', body, headers = {}, signal } = {}) {
  const mutating = !['GET', 'HEAD', 'OPTIONS'].includes(method.toUpperCase());
  const requestHeaders = { ...headers };
  if (body !== undefined) requestHeaders['Content-Type'] = 'application/json';
  if (mutating) {
    const token = csrfToken || cookieToken();
    if (token) requestHeaders['X-XSRF-TOKEN'] = token;
  }
  const response = await fetch(path, {
    method,
    headers: requestHeaders,
    body: body === undefined ? undefined : JSON.stringify(body),
    credentials: 'same-origin',
    signal,
  });
  const contentType = response.headers.get('content-type') || '';
  const payload = response.status === 204
    ? null
    : contentType.includes('application/json') ? await response.json() : await response.text();
  if (!response.ok) {
    const error = new Error(payload?.message || payload || `HTTP ${response.status}`);
    error.status = response.status;
    error.validation = payload?.validation || {};
    if (response.status === 401 && !path.startsWith('/api/auth/')) {
      window.dispatchEvent(new Event('techflow:auth-expired'));
    }
    throw error;
  }
  return payload;
}

export async function apiForm(path, formData, { method = 'POST', headers = {}, signal } = {}) {
  const requestHeaders = { ...headers };
  const token = csrfToken || cookieToken();
  if (token) requestHeaders['X-XSRF-TOKEN'] = token;
  const response = await fetch(path, {
    method,
    headers: requestHeaders,
    body: formData,
    credentials: 'same-origin',
    signal,
  });
  const contentType = response.headers.get('content-type') || '';
  const payload = response.status === 204
    ? null
    : contentType.includes('application/json') ? await response.json() : await response.text();
  if (!response.ok) {
    const error = new Error(payload?.message || payload || `HTTP ${response.status}`);
    error.status = response.status;
    error.validation = payload?.validation || {};
    if (response.status === 401 && !path.startsWith('/api/auth/')) {
      window.dispatchEvent(new Event('techflow:auth-expired'));
    }
    throw error;
  }
  return payload;
}
