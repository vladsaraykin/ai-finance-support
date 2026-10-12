import { useEffect, useState } from 'react';
import { financeApi } from './api.js';
import { currentMoscowMonth, formatMonth, moveMonth } from './finance-format.js';
import DraftReview from './components/DraftReview.jsx';
import AssistantPanel from './components/AssistantPanel.jsx';
import KnowledgeDocumentForm from './components/KnowledgeDocumentForm.jsx';
import MonthlyReport from './components/MonthlyReport.jsx';
import TransactionComposer from './components/TransactionComposer.jsx';
import TransactionsList from './components/TransactionsList.jsx';

export default function App() {
  const [month, setMonth] = useState(currentMoscowMonth);
  const [transactions, setTransactions] = useState([]);
  const [report, setReport] = useState(null);
  const [aiEnabled, setAiEnabled] = useState(false);
  const [assistantEnabled, setAssistantEnabled] = useState(false);
  const [drafts, setDrafts] = useState([]);
  const [entryText, setEntryText] = useState('');
  const [loading, setLoading] = useState(true);
  const [parsing, setParsing] = useState(false);
  const [saving, setSaving] = useState(false);
  const [savingId, setSavingId] = useState(null);
  const [pageError, setPageError] = useState('');
  const [formError, setFormError] = useState('');
  const [formHint, setFormHint] = useState('');
  const [notice, setNotice] = useState('');
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    let active = true;
    setLoading(true);
    setPageError('');
    Promise.all([financeApi.listTransactions(month), financeApi.monthlyReport(month), financeApi.getConfig()])
      .then(([entries, monthlyReport, config]) => {
        if (!active) return;
        setTransactions(entries);
        setReport(monthlyReport);
        setAiEnabled(config.aiEnabled);
        setAssistantEnabled(config.assistantEnabled);
      })
      .catch((error) => {
        if (active) setPageError(error.message);
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => { active = false; };
  }, [month, reloadKey]);

  async function parseText(event) {
    event.preventDefault();
    setParsing(true);
    setFormError('');
    setFormHint('');
    setNotice('');
    try {
      const parsed = await financeApi.parseText(entryText.trim());
      setDrafts(parsed.transactions.map((draft) => ({ ...draft, amount: String(draft.amount) })));
      setFormHint(parsed.clarificationQuestion ?? '');
    } catch (error) {
      setFormError(error.message);
    } finally {
      setParsing(false);
    }
  }

  async function confirmDrafts() {
    setSaving(true);
    setFormError('');
    try {
      await financeApi.createTransactions(drafts);
      setDrafts([]);
      setEntryText('');
      setNotice('Операции сохранены. Отчёт пересчитан по данным финансовой базы.');
      setReloadKey((key) => key + 1);
    } catch (error) {
      setFormError(error.message);
    } finally {
      setSaving(false);
    }
  }

  async function saveTransaction(id, draft) {
    setSavingId(id);
    setNotice('');
    try {
      await financeApi.updateTransaction(id, draft);
      setNotice('Изменения сохранены.');
      setReloadKey((key) => key + 1);
      return true;
    } catch (error) {
      setNotice(error.message);
      return false;
    } finally {
      setSavingId(null);
    }
  }

  return (
    <div className="app-shell">
      <header className="topbar">
        <a className="brand" href="#overview" aria-label="Финансы — главная">
          <span className="brand-mark" aria-hidden="true">ф</span>
          <span>финансы</span>
        </a>
        <nav aria-label="Основная навигация"><a className="nav-current" href="#overview">Обзор</a></nav>
      </header>

      <main id="overview" className="page-content">
        <div className="page-title-row">
          <div>
            <h1>Финансовый обзор</h1>
            <p className="page-subtitle">Записывайте операции обычным текстом и следите за расходами.</p>
          </div>
          <div className="current-period"><span>Выбранный период</span><strong>{formatMonth(month)}</strong></div>
        </div>

        {pageError && (
          <div className="connection-error" role="alert">
            <div><strong>Не удалось загрузить финансовые данные.</strong><span>{pageError}</span></div>
            <button className="button button-secondary" type="button" onClick={() => setReloadKey((key) => key + 1)}>Повторить</button>
          </div>
        )}
        {notice && <p className="notice" role="status">{notice}</p>}

        <div className="workspace-grid">
          <div className="primary-column">
            <TransactionComposer value={entryText} onChange={setEntryText} onSubmit={parseText}
              busy={parsing} error={formError} hint={formHint} aiEnabled={aiEnabled} />
            <DraftReview drafts={drafts} onChange={setDrafts} onConfirm={confirmDrafts}
              onCancel={() => { setDrafts([]); setFormError(''); setFormHint(''); }} busy={saving} />
          </div>
          <aside className="report-column">
            <MonthlyReport report={report} monthLabel={formatMonth(month)}
              onMoveMonth={(amount) => setMonth((current) => moveMonth(current, amount))} />
            {loading && <p className="sync-status" role="status">Обновляю данные…</p>}
          </aside>
        </div>

        <div className="assistant-grid">
          <AssistantPanel enabled={assistantEnabled} />
          <KnowledgeDocumentForm enabled={assistantEnabled} />
        </div>

        <TransactionsList transactions={transactions} onSave={saveTransaction} savingId={savingId} />

        <footer className="page-footer">
          <span>Расчёты основаны на сохранённых операциях.</span>
          <span>Переводы между своими счетами не входят в доходы и расходы.</span>
        </footer>
      </main>
    </div>
  );
}
