async function request(path, options = {}) {
  const response = await fetch(path, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...options.headers,
    },
  });

  if (!response.ok) {
    const problem = await response.json().catch(() => null);
    throw new Error(problem?.message ?? 'Не удалось выполнить запрос. Попробуйте ещё раз.');
  }
  return response.json();
}

export const financeApi = {
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
  createTransactions: (transactions) => request('/api/transactions', {
    method: 'POST',
    headers: { 'Idempotency-Key': crypto.randomUUID() },
    body: JSON.stringify({ transactions }),
  }),
  listTransactions: (month) => request(`/api/transactions?month=${encodeURIComponent(month)}`),
  updateTransaction: (id, transaction) => request(`/api/transactions/${id}`, {
    method: 'PUT',
    body: JSON.stringify(transaction),
  }),
  monthlyReport: (month) => request(`/api/reports/monthly?month=${encodeURIComponent(month)}`),
};
