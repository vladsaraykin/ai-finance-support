import { categoryLabels, formatMoney } from '../finance-format.js';

export default function MonthlyReport({ report, monthLabel, onMoveMonth }) {
  return (
    <section className="monthly-report" aria-labelledby="report-title">
      <div className="section-heading report-heading">
        <div>
          <p className="section-kicker">Отчёт за месяц</p>
          <h2 id="report-title">{monthLabel}</h2>
        </div>
        <div className="month-controls" aria-label="Выбрать месяц">
          <button type="button" aria-label="Предыдущий месяц" onClick={() => onMoveMonth(-1)}>
            <svg viewBox="0 0 20 20" aria-hidden="true"><path d="m12 4-6 6 6 6" /></svg>
          </button>
          <button type="button" aria-label="Следующий месяц" onClick={() => onMoveMonth(1)}>
            <svg viewBox="0 0 20 20" aria-hidden="true"><path d="m8 4 6 6-6 6" /></svg>
          </button>
        </div>
      </div>
      {!report ? (
        <p className="muted-copy">Загружаю отчёт…</p>
      ) : (
        <>
          <div className="balance-line">
            <span>Остаток за месяц</span>
            <strong>{formatMoney(report.balance, report.currency)}</strong>
          </div>
          <dl className="report-totals">
            <div><dt>Доходы</dt><dd className="income-value">{formatMoney(report.incomeTotal, report.currency)}</dd></div>
            <div><dt>Расходы</dt><dd className="expense-value">{formatMoney(report.expenseTotal, report.currency)}</dd></div>
            <div><dt>Операции</dt><dd>{report.transactionCount}</dd></div>
          </dl>
          <div className="category-breakdown">
            <h3>Расходы по категориям</h3>
            {report.expensesByCategory.length ? (
              <ul>
                {report.expensesByCategory.map((item) => (
                  <li key={item.category}>
                    <span>{categoryLabels[item.category] ?? item.category}</span>
                    <strong>{formatMoney(item.total, report.currency)}</strong>
                  </li>
                ))}
              </ul>
            ) : <p className="empty-copy">В этом месяце расходов пока нет.</p>}
          </div>
        </>
      )}
    </section>
  );
}
