import { useEffect, useState } from 'react';
import { financeApi } from '../api.js';

export default function KnowledgeDocumentForm({ enabled }) {
  const [title, setTitle] = useState('');
  const [source, setSource] = useState('');
  const [documentType, setDocumentType] = useState('FINANCIAL_GUIDE');
  const [text, setText] = useState('');
  const [busy, setBusy] = useState(false);
  const [document, setDocument] = useState(null);
  const [pollVersion, setPollVersion] = useState(0);
  const [error, setError] = useState('');
  const pending = document?.status === 'in_progress';

  useEffect(() => {
    if (!pending) return;
    const controller = new AbortController();
    let timer;
    let attempts = 0;
    async function poll() {
      try {
        const result = await financeApi.documentStatus(document.fileId, controller.signal);
        if (controller.signal.aborted) return;
        if (result.status !== 'in_progress') {
          setDocument(result);
        } else if (++attempts < 40) {
          timer = setTimeout(poll, 3000);
        } else {
          setError('Индексация ещё выполняется. Можно проверить статус позже без повторной загрузки.');
        }
      } catch (requestError) {
        if (!controller.signal.aborted) setError(requestError.message);
      }
    }
    timer = setTimeout(poll, 1500);
    return () => { controller.abort(); clearTimeout(timer); };
  }, [document?.fileId, pending, pollVersion]);

  async function submit(event) {
    event.preventDefault();
    if (busy || pending) return;
    setBusy(true);
    setDocument(null);
    setError('');
    try {
      const result = await financeApi.ingestDocument({ title, source, documentType, text });
      setDocument(result);
    } catch (requestError) {
      setError(requestError.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="knowledge-form section-block" aria-labelledby="knowledge-title">
      <div className="section-heading">
        <div><p className="section-kicker">База знаний RAG</p><h2 id="knowledge-title">Добавить материал</h2></div>
      </div>
      <form onSubmit={submit}>
        <label>Название<input value={title} onChange={(event) => setTitle(event.target.value)} maxLength={200} required /></label>
        <label>Источник или магазин<input value={source} onChange={(event) => setSource(event.target.value)} maxLength={300} /></label>
        <label>Тип материала<select value={documentType} onChange={(event) => setDocumentType(event.target.value)}>
          <option value="RULE">Моё правило</option>
          <option value="FINANCIAL_GUIDE">Финансовый документ</option>
          <option value="STORE_CATALOG">Магазин</option>
          <option value="PRODUCT_CATALOG">Товары</option>
          <option value="OTHER">Другое</option>
        </select></label>
        <label>Текст<textarea value={text} onChange={(event) => setText(event.target.value)} rows={5} maxLength={100000} required /></label>
        <div className="assistant-actions">
          <p>{enabled ? 'Документ передаётся и сохраняется в OpenAI для поиска и ответов.' : 'Поиск по документам отключён.'}</p>
          <button className="button button-secondary" type="submit" disabled={!enabled || busy || pending || !title.trim() || !text.trim()}>
            {busy ? 'Загружаю…' : pending ? 'Индексирую…' : 'Добавить'}
          </button>
        </div>
      </form>
      {document && <p className="form-message" role="status">
        {pending ? 'Документ загружен. OpenAI готовит его к поиску.'
          : document.status === 'completed' ? 'Документ проиндексирован и доступен для поиска.' : document.error}
      </p>}
      {error && <p className="form-message form-message-error" role="alert">{error}</p>}
      {pending && error && <button type="button" className="text-button" onClick={() => {
        setError('');
        setPollVersion((version) => version + 1);
      }}>Проверить статус</button>}
    </section>
  );
}
