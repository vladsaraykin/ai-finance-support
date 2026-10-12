import { useState } from 'react';
import { financeApi } from '../api.js';

export default function KnowledgeDocumentForm({ enabled }) {
  const [title, setTitle] = useState('');
  const [source, setSource] = useState('');
  const [documentType, setDocumentType] = useState('FINANCIAL_GUIDE');
  const [text, setText] = useState('');
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState('');
  const [error, setError] = useState('');

  async function submit(event) {
    event.preventDefault();
    setBusy(true);
    setNotice('');
    setError('');
    try {
      const result = await financeApi.ingestDocument({ title, source, documentType, text });
      setNotice(`Документ добавлен: ${result.chunksAdded} фрагм.`);
      setTitle('');
      setSource('');
      setText('');
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
          <p>{enabled ? 'Текст индексируется локальным embedding-сервисом и сохраняется в Qdrant.' : 'Сначала включите и запустите RAG-сервис.'}</p>
          <button className="button button-secondary" type="submit" disabled={!enabled || busy || !title.trim() || !text.trim()}>
            {busy ? 'Индексирую…' : 'Добавить'}
          </button>
        </div>
      </form>
      {notice && <p className="notice" role="status">{notice}</p>}
      {error && <p className="form-message form-message-error" role="alert">{error}</p>}
    </section>
  );
}
