import { categoryLabels, dateAtNoon, dateInputValue, formatMoney, transactionTypes } from '../finance-format.js';

const categories = Object.keys(categoryLabels);

export default function DraftReview({ drafts, onChange, onConfirm, onCancel, busy }) {
  if (!drafts.length) return null;

  function update(index, field, value) {
    onChange(drafts.map((draft, itemIndex) => itemIndex === index
      ? { ...draft, [field]: value }
      : draft));
  }

  return (
    <section className="draft-review" aria-labelledby="draft-title">
      <div className="review-heading">
        <div>
          <p className="section-kicker">Проверьте детали</p>
          <h2 id="draft-title">Найдено операций: {drafts.length}</h2>
        </div>
        <button type="button" className="text-button" onClick={onCancel}>Отменить</button>
      </div>
      <div className="draft-rows">
        {drafts.map((draft, index) => (
          <div className="draft-row" key={`${index}-${draft.occurredAt}`}>
            <label>
              <span>Тип</span>
              <select value={draft.type} onChange={(event) => update(index, 'type', event.target.value)}>
                {Object.entries(transactionTypes).map(([value, label]) => <option value={value} key={value}>{label}</option>)}
              </select>
            </label>
            <label>
              <span>Сумма, ₽</span>
              <input type="number" min="0.01" step="0.01" value={draft.amount}
                onChange={(event) => update(index, 'amount', event.target.value)} required />
            </label>
            <label>
              <span>Категория</span>
              <select value={draft.category} onChange={(event) => update(index, 'category', event.target.value)}>
                {categories.map((value) => <option value={value} key={value}>{categoryLabels[value]}</option>)}
              </select>
            </label>
            <label>
              <span>Место или описание</span>
              <input value={draft.merchant ?? ''} maxLength={160} placeholder="Например, Такси"
                onChange={(event) => update(index, 'merchant', event.target.value)} />
            </label>
            <label>
              <span>Дата</span>
              <input type="date" value={dateInputValue(draft.occurredAt)}
                onChange={(event) => update(index, 'occurredAt', dateAtNoon(event.target.value))} required />
            </label>
            <div className="draft-amount-preview" aria-label={`Сумма ${formatMoney(draft.amount)}`}>
              {formatMoney(draft.amount)}
            </div>
          </div>
        ))}
      </div>
      <div className="review-footer">
        <p>Записи появятся в журнале после подтверждения.</p>
        <button className="button button-primary" type="button" onClick={onConfirm} disabled={busy}>
          {busy ? 'Сохраняю…' : 'Подтвердить'}
        </button>
      </div>
    </section>
  );
}
