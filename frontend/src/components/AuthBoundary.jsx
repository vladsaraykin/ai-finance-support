import { useEffect, useState } from 'react';
import { authApi } from '../api.js';

export default function AuthBoundary({ children }) {
  const [user, setUser] = useState(null);
  const [checking, setChecking] = useState(true);
  const [login, setLogin] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const [retry, setRetry] = useState(0);

  useEffect(() => {
    let active = true;
    const expired = () => { setUser(null); setPassword(''); setError('Войдите, чтобы продолжить.'); };
    window.addEventListener('auth-expired', expired);
    setChecking(true);
    authApi.me().then((value) => { if (active) { setUser(value); setError(''); } })
      .catch((failure) => { if (active) setError(failure.status === 401 ? '' : failure.message); })
      .finally(() => { if (active) setChecking(false); });
    return () => { active = false; window.removeEventListener('auth-expired', expired); };
  }, [retry]);

  async function submit(event) {
    event.preventDefault();
    setBusy(true);
    setError('');
    try {
      setUser(await authApi.login(login.trim(), password));
      setPassword('');
    } catch (failure) { setError(failure.message); }
    finally { setBusy(false); }
  }

  async function logout() {
    setBusy(true);
    setError('');
    try { await authApi.logout(); setUser(null); setPassword(''); }
    catch (failure) { setError(failure.message); }
    finally { setBusy(false); }
  }

  if (checking) return <main className="auth-page"><p role="status">Проверяю вход…</p></main>;
  if (user) return <>{error && <p className="notice" role="alert">{error}</p>}{children(user, logout, busy)}</>;
  return (
    <main className="auth-page">
      <form className="auth-card" onSubmit={submit}>
        <span className="brand-mark" aria-hidden="true">ф</span>
        <h1>Вход в финансы</h1>
        <p>Войдите в свой аккаунт, чтобы открыть операции.</p>
        <label>Логин<input name="username" autoComplete="username" autoCapitalize="none" spellCheck="false"
          value={login} onChange={(event) => setLogin(event.target.value)} required maxLength={64} disabled={busy} /></label>
        <label>Пароль<input name="password" type="password" autoComplete="current-password"
          value={password} onChange={(event) => setPassword(event.target.value)} required disabled={busy} /></label>
        {error && <p className="auth-error" role="alert">{error}</p>}
        <button className="button button-primary" disabled={busy}>{busy ? 'Вхожу…' : 'Войти'}</button>
        <button className="text-button" type="button" disabled={busy} onClick={() => setRetry((value) => value + 1)}>Проверить соединение</button>
      </form>
    </main>
  );
}
