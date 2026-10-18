import test from 'node:test';
import assert from 'node:assert/strict';

let sequence = 0;
async function client(respond) {
  const calls = [];
  globalThis.window = new EventTarget();
  globalThis.fetch = async (path, options = {}) => {
    calls.push({ path, options });
    const [status, body] = respond(path, options, calls);
    return new Response(status === 204 ? null : JSON.stringify(body), {
      status, headers: { 'Content-Type': 'application/json' },
    });
  };
  const api = await import(`../src/api.js?test=${++sequence}`);
  return { ...api, calls };
}

test('login sends CSRF and form credentials; mutations use a fresh token after login', async () => {
  let tokens = 0;
  const { authApi, financeApi, calls } = await client((path) => {
    if (path === '/api/auth/csrf') return [200, { headerName: 'X-CSRF-TOKEN', token: `token-${++tokens}` }];
    if (path === '/api/auth/login') return [200, { id: 'owner', login: 'alice' }];
    return [204];
  });
  await authApi.login('alice', 'test password');
  await financeApi.deleteTransaction('transaction-id');
  const login = calls.find((call) => call.path.endsWith('/login'));
  const deletion = calls.find((call) => call.options.method === 'DELETE');
  assert.equal(login.options.headers['X-CSRF-TOKEN'], 'token-1');
  assert.equal(login.options.body.get('password'), 'test password');
  assert.equal(deletion.options.headers['X-CSRF-TOKEN'], 'token-2');
  assert.equal(deletion.options.credentials, 'same-origin');
});

test('expired session emits an event and preserves HTTP status for the interface', async () => {
  const { authApi } = await client(() => [401, { message: 'Войдите' }]);
  let expired = 0;
  window.addEventListener('auth-expired', () => expired++);
  await assert.rejects(authApi.me(), (error) => error.status === 401 && error.message === 'Войдите');
  assert.equal(expired, 1);
});

test('a rejected mutation is not automatically repeated; retry obtains a new CSRF token', async () => {
  let tokens = 0, deletes = 0;
  const { financeApi } = await client((path) => {
    if (path.endsWith('/csrf')) return [200, { headerName: 'X-CSRF-TOKEN', token: `${++tokens}` }];
    return ++deletes === 1 ? [403, { message: 'Обновите страницу' }] : [204];
  });
  await assert.rejects(financeApi.deleteTransaction('id'));
  assert.equal(deletes, 1);
  assert.equal(await financeApi.deleteTransaction('id'), null);
  assert.equal(tokens, 2);
});

test('retries retain the caller supplied idempotency key', async () => {
  const { financeApi, calls } = await client((path) => path.endsWith('/csrf')
    ? [200, { headerName: 'X-CSRF-TOKEN', token: 'token' }] : [200, []]);
  for (let i = 0; i < 2; i++) await financeApi.createTransactions([], 'stable-key');
  assert.deepEqual(calls.filter((call) => call.options.method === 'POST')
    .map((call) => call.options.headers['Idempotency-Key']), ['stable-key', 'stable-key']);
});
