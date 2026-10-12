import { useState } from 'react';
import { financeApi } from '../api.js';

export default function AssistantPanel({ enabled }) {
  const [message, setMessage] = useState('');
  const [answer, setAnswer] = useState(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  async function submit(event) {
    event.preventDefault();
    setBusy(true);
    setError('');
    try {
      setAnswer(await financeApi.askAssistant(message.trim()));
    } catch (requestError) {
      setError(requestError.message);
    } finally {
      setBusy(false);
    }
  }

  return (
    <section className="assistant-panel section-block" aria-labelledby="assistant-title">
      <div className="section-heading">
        <div><p className="section-kicker">Ответы по вашим материалам</p><h2 id="assistant-title">Спросить помощника</h2></div>
        <span className="step-marker">AI</span>
      </div>
      <form onSubmit={submit}>
        <label className="visually-hidden" htmlFor="assistant-message">Вопрос по документам и правилам</label>
        <textarea id="assistant-message" value={message} onChange={(event) => setMessage(event.target.value)}
          placeholder="Например: чем отличаются условия кешбэка в этих магазинах?" rows={3} maxLength={2000} required />
        <div className="assistant-actions">
          <p>{enabled
            ? 'Вопрос и найденные выдержки отправляются в OpenAI для ответа.'
            : 'Для ответов включите OpenAI и RAG в конфигурации backend.'}</p>
          <button className="button button-primary" type="submit" disabled={!enabled || busy || !message.trim()}>
            {busy ? 'Ищу…' : 'Задать вопрос'}
          </button>
        </div>
      </form>
      {error && <p className="form-message form-message-error" role="alert">{error}</p>}
      {answer && <div className="assistant-answer" aria-live="polite">
        <p>{answer.answer}</p>
        {!!answer.sources?.length && <div className="assistant-sources"><strong>Источники</strong>
          <ul>{answer.sources.map((source, index) => <li key={`${source.title}-${index}`}>
            <span>{source.title}</span><small>{source.excerpt}</small>
          </li>)}</ul>
        </div>}
      </div>}
    </section>
  );
}
