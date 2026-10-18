let csrf = null;
let csrfRequest = null;

async function getCsrf() {
  if (csrf) return csrf;
  if (!csrfRequest) {
    csrfRequest = fetch('/api/auth/csrf', { credentials: 'same-origin', cache: 'no-store' })
      .then(async (response) => {
        if (!response.ok) throw new Error('Не удалось установить защищённое соединение.');
        csrf = await response.json();
        return csrf;
      }).finally(() => { csrfRequest = null; });
  }
  return csrfRequest;
}

async function request(path, options = {}) {
  const headers = { 'Content-Type': 'application/json', ...options.headers };
  if (!['GET', 'HEAD', 'OPTIONS'].includes(options.method ?? 'GET')) {
    const token = await getCsrf();
    headers[token.headerName] = token.token;
  }
  const response = await fetch(path, { ...options, credentials: 'same-origin', cache: 'no-store', headers });
  if (!response.ok) {
    if (response.status === 403) csrf = null;
    if (response.status === 401 && path !== '/api/auth/login') {
      csrf = null;
      window.dispatchEvent(new Event('auth-expired'));
    }
    const problem = await response.json().catch(() => null);
    const error = new Error(problem?.message ?? 'Не удалось выполнить запрос. Попробуйте ещё раз.');
    error.status = response.status;
    throw error;
  }
  return response.status === 204 ? null : response.json();
}

export const authApi = {
  me: () => request('/api/auth/me'),
  login: async (login, password) => {
    const user = await request('/api/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: new URLSearchParams({ username: login, password }),
    });
    csrf = null;
    return user;
  },
  logout: async () => {
    await request('/api/auth/logout', { method: 'POST' });
    csrf = null;
  },
};

export const financeApi = {
  categories: () => request('/api/categories'),
  getConfig: () => request('/api/config'),
  parseText: (text) => request('/api/transactions/parse', {
    method: 'POST',
    body: JSON.stringify({ text }),
  }),
  askAssistant: (message) => request('/api/assistant/chat', {
    method: 'POST',
    body: JSON.stringify({ message }),
  }),
  ingestDocument: (document) => request('/api/knowledge/documents', {
    method: 'POST',
    body: JSON.stringify(document),
  }),
  documentStatus: (fileId, signal) => request(`/api/knowledge/documents/${encodeURIComponent(fileId)}`, { signal }),
  createTransactions: (transactions, idempotencyKey) => request('/api/transactions', {
    method: 'POST',
    headers: { 'Idempotency-Key': idempotencyKey },
    body: JSON.stringify({ transactions }),
  }),
  listTransactions: (month) => request(`/api/transactions?month=${encodeURIComponent(month)}`),
  updateTransaction: (id, transaction) => request(`/api/transactions/${id}`, {
    method: 'PUT',
    body: JSON.stringify(transaction),
  }),
  deleteTransaction: (id) => request(`/api/transactions/${id}`, { method: 'DELETE' }),
  monthlyReport: (month) => request(`/api/reports/monthly?month=${encodeURIComponent(month)}`),
};
