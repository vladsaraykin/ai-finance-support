import { useState } from 'react';
import { categoryLabels, dateAtNoon, dateInputValue, formatDate, formatMoney, transactionTypes } from '../finance-format.js';

function TransactionRow({ transaction, onSave, busy }) {
  const [editing, setEditing] = useState(false);
  const toDraft = (value) => ({
    type: value.type,
    amount: value.amount,
    currency: value.currency,
    occurredAt: value.occurredAt,
    category: value.category,
    merchant: value.merchant ?? '',
    description: value.description ?? '',
  });
  const [draft, setDraft] = useState(() => toDraft(transaction));

  function set(field, value) {
    setDraft((current) => ({ ...current, [field]: value }));
  }

  async function submit(event) {
    event.preventDefault();
    const saved = await onSave(transaction.id, {
      ...draft,
      amount: String(draft.amount),
      merchant: draft.merchant || null,
      occurredAt: draft.occurredAt,
    });
    if (saved) setEditing(false);
  }

  if (editing) {
    return (
      <tr className="editing-row">
        <td colSpan="5">
          <form className="inline-edit" onSubmit={submit}>
            <label><span>Тип</span><select value={draft.type} onChange={(event) => set('type', event.target.value)}>
              {Object.entries(transactionTypes).map(([value, label]) => <option value={value} key={value}>{label}</option>)}
            </select></label>
            <label><span>Сумма</span><input type="number" min="0.01" step="0.01" value={draft.amount}
              onChange={(event) => set('amount', event.target.value)} required /></label>
            <label><span>Категория</span><select value={draft.category} onChange={(event) => set('category', event.target.value)}>
              {Object.entries(categoryLabels).map(([value, label]) => <option value={value} key={value}>{label}</option>)}
            </select></label>
            <label><span>Описание</span><input value={draft.merchant} maxLength={160}
              onChange={(event) => set('merchant', event.target.value)} /></label>
            <label><span>Дата</span><input type="date" value={dateInputValue(draft.occurredAt)}
              onChange={(event) => set('occurredAt', dateAtNoon(event.target.value))} required /></label>
            <div className="inline-edit-actions">
              <button className="button button-primary button-small" type="submit" disabled={busy}>Сохранить</button>
              <button className="text-button" type="button" onClick={() => setEditing(false)}>Отмена</button>
            </div>
          </form>
        </td>
      </tr>
    );
  }

  const sign = transaction.type === 'INCOME' ? '+' : transaction.type === 'EXPENSE' ? '−' : '';
  return (
    <tr>
      <td>{formatDate(transaction.occurredAt)}</td>
      <td><span className="category-name">{categoryLabels[transaction.category] ?? transaction.category}</span></td>
      <td>{transaction.merchant || transaction.description || '—'}</td>
      <td className={`amount-cell ${transaction.type === 'INCOME' ? 'income-value' : transaction.type === 'EXPENSE' ? 'expense-value' : ''}`}>
        {sign}{formatMoney(transaction.amount, transaction.currency)}
      </td>
      <td className="action-cell">
        <button className="text-button edit-button" type="button" onClick={() => { setDraft(toDraft(transaction)); setEditing(true); }} aria-label={`Изменить: ${transaction.merchant || categoryLabels[transaction.category]}`}>
          Изменить
        </button>
      </td>
    </tr>
  );
}

export default function TransactionsList({ transactions, onSave, savingId }) {
  return (
    <section className="transaction-list section-block" aria-labelledby="transactions-title">
      <div className="section-heading">
        <div>
          <p className="section-kicker">История</p>
          <h2 id="transactions-title">Операции</h2>
        </div>
        <span className="row-count">{transactions.length} записей</span>
      </div>
      {transactions.length ? (
        <div className="table-wrap">
          <table>
            <thead><tr><th>Дата</th><th>Категория</th><th>Описание</th><th>Сумма</th><th><span className="visually-hidden">Действие</span></th></tr></thead>
            <tbody>
              {transactions.map((transaction) => <TransactionRow key={transaction.id} transaction={transaction}
                onSave={onSave} busy={savingId === transaction.id} />)}
            </tbody>
          </table>
        </div>
      ) : (
        <div className="empty-state">
          <span className="empty-state-mark" aria-hidden="true">₽</span>
          <h3>Записей за этот месяц пока нет</h3>
          <p>Опишите операцию выше, чтобы она появилась здесь.</p>
        </div>
      )}
    </section>
  );
}
