export default function TransactionComposer({ value, onChange, onSubmit, busy, error, hint, aiEnabled }) {
  return (
    <section className="composer section-block" aria-labelledby="composer-title">
      <div className="section-heading">
        <div>
          <p className="section-kicker">Новая запись</p>
          <h2 id="composer-title">Записать операцию</h2>
        </div>
        <span className="step-marker">01</span>
      </div>
      <form onSubmit={onSubmit}>
        <label className="visually-hidden" htmlFor="transaction-text">Описание дохода или расхода</label>
        <textarea
          id="transaction-text"
          value={value}
          onChange={(event) => onChange(event.target.value)}
          placeholder="Например: вчера потратил 800 рублей на такси и 450 на обед"
          rows={3}
          maxLength={2000}
          required
        />
        <div className="composer-footer">
          <p>{aiEnabled
            ? 'Текст будет отправлен в OpenAI для распознавания. Проверьте результат перед сохранением.'
            : 'Локальный разбор текста. Проверьте распознанные суммы перед сохранением.'}</p>
          <button className="button button-primary" type="submit" disabled={busy || !value.trim()}>
            {busy ? 'Разбираю…' : 'Распознать'}
            <svg viewBox="0 0 20 20" aria-hidden="true"><path d="M4 10h11M10 5l5 5-5 5" /></svg>
          </button>
        </div>
      </form>
      {error && <p className="form-message form-message-error" role="alert">{error}</p>}
      {hint && <p className="form-message form-message-hint" role="status">{hint}</p>}
    </section>
  );
}
