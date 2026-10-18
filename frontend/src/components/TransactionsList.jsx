import { useState } from 'react';
import { categoryLabels, dateAtNoon, dateInputValue, formatDate, formatMoney, transactionTypes } from '../finance-format.js';

function TransactionRow({ transaction, categories, onSave, onDelete, busy }) {
  const [confirmingDelete, setConfirmingDelete] = useState(false);
  const [editing, setEditing] = useState(false);
  const toDraft = (value) => ({
    type: value.type,
    amount: value.amount,
    currency: value.currency,
    occurredAt: value.occurredAt,
    category: categoryLabels[value.category] ?? value.category,
    merchant: value.merchant ?? '',
    description: value.description ?? '',
    paymentMethod: value.paymentMethod ?? null,
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
      category: Object.entries(categoryLabels).find(([, label]) => label === draft.category.trim())?.[0] ?? draft.category.trim(),
      merchant: draft.merchant || null,
      occurredAt: draft.occurredAt,
    });
    if (saved) setEditing(false);
  }

  if (editing) {
    return (
      <tr className="editing-row">
        <td colSpan="6">
          <form className="inline-edit" onSubmit={submit}>
            <label><span>Тип</span><select value={draft.type} onChange={(event) => set('type', event.target.value)}>
              {Object.entries(transactionTypes).map(([value, label]) => <option value={value} key={value}>{label}</option>)}
            </select></label>
            <label><span>Сумма</span><input type="number" min="0.01" step="0.01" value={draft.amount}
              onChange={(event) => set('amount', event.target.value)} required /></label>
            <label><span>Категория</span><input list={`categories-${transaction.id}`} value={draft.category}
              maxLength={160} required onChange={(event) => set('category', event.target.value)} />
              <datalist id={`categories-${transaction.id}`}>
                {categories.map((value) => <option value={categoryLabels[value] ?? value} key={value} />)}
              </datalist>
            </label>
            <label><span>Магазин или получатель</span><input value={draft.merchant} maxLength={160}
              onChange={(event) => set('merchant', event.target.value)} /></label>
            <label><span>Описание</span><input value={draft.description} maxLength={500}
              onChange={(event) => set('description', event.target.value)} /></label>
            <label><span>Способ оплаты</span><select value={draft.paymentMethod ?? ''} onChange={(event) => set('paymentMethod', event.target.value || null)}>
              <option value="">Не указан</option>
              {['Карта', 'Наличные', 'Перевод', 'Другое'].map((value) => <option key={value}>{value}</option>)}
            </select></label>
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

  if (confirmingDelete) return <tr><td colSpan="6">
    <div className="delete-confirm" role="group" aria-label="Подтверждение удаления">
      <p>Удалить {transactionTypes[transaction.type]?.toLowerCase()} от {formatDate(transaction.occurredAt)} на {formatMoney(transaction.amount, transaction.currency)} — {transaction.description || transaction.merchant || 'без описания'}?</p>
      <button type="button" className="button button-danger" disabled={busy} onClick={async () => {
        if (await onDelete(transaction.id)) setConfirmingDelete(false);
      }}>{busy ? 'Удаляю…' : 'Удалить операцию'}</button>
      <button type="button" className="text-button" disabled={busy} onClick={() => setConfirmingDelete(false)}>Отмена</button>
    </div>
  </td></tr>;

  const sign = transaction.type === 'INCOME' ? '+' : transaction.type === 'EXPENSE' ? '−' : '';
  return (
    <tr>
      <td data-label="Дата">{formatDate(transaction.occurredAt)}</td>
      <td data-label="Категория"><span className="category-name">{categoryLabels[transaction.category] ?? transaction.category}</span></td>
      <td data-label="Описание">{transaction.description || transaction.merchant || '—'}
        {transaction.merchant && transaction.description && transaction.merchant !== transaction.description && <small className="merchant-detail">{transaction.merchant}</small>}
      </td>
      <td data-label="Оплата">{transaction.paymentMethod || 'Не указан'}</td>
      <td data-label="Сумма" className={`amount-cell ${transaction.type === 'INCOME' ? 'income-value' : transaction.type === 'EXPENSE' ? 'expense-value' : ''}`}>
        {sign}{formatMoney(transaction.amount, transaction.currency)}
      </td>
      <td className="action-cell">
        <button className="text-button edit-button" type="button" disabled={busy} onClick={() => { setDraft(toDraft(transaction)); setEditing(true); }} aria-label={`Изменить: ${transaction.merchant || categoryLabels[transaction.category] || transaction.category}`}>
          Изменить
        </button>
        <button className="text-button danger-text" type="button" disabled={busy} onClick={() => setConfirmingDelete(true)}>Удалить</button>
      </td>
    </tr>
  );
}

export default function TransactionsList({ transactions, categories, onSave, onDelete, savingId }) {
  return (
    <section className="transaction-list section-block" aria-labelledby="transactions-title">
      <div className="section-heading">
        <div>
          <p className="section-kicker">История</p>
          <h2 id="transactions-title">Операции</h2>
          <p className="history-order">Сначала недавно добавленные</p>
        </div>
        <span className="row-count">{transactions.length} записей</span>
      </div>
      {transactions.length ? (
        <div className="table-wrap">
          <table>
            <thead><tr><th>Дата</th><th>Категория</th><th>Описание</th><th>Оплата</th><th>Сумма</th><th><span className="visually-hidden">Действие</span></th></tr></thead>
            <tbody>
              {transactions.map((transaction) => <TransactionRow key={transaction.id} transaction={transaction}
                categories={categories} onSave={onSave} onDelete={onDelete} busy={savingId === transaction.id} />)}
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
