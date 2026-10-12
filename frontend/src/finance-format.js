export const categoryLabels = {
  GROCERIES: 'Продукты',
  RESTAURANTS: 'Кафе и рестораны',
  TRANSPORT: 'Транспорт',
  HOUSING: 'Жильё',
  HEALTH: 'Здоровье',
  ENTERTAINMENT: 'Развлечения',
  UTILITIES: 'Коммунальные услуги',
  SALARY: 'Зарплата и доходы',
  OTHER: 'Другое',
};

export const transactionTypes = {
  INCOME: 'Доход',
  EXPENSE: 'Расход',
  TRANSFER: 'Перевод',
};

export function formatMoney(value, currency = 'RUB') {
  return new Intl.NumberFormat('ru-RU', {
    style: 'currency',
    currency,
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(String(value));
}

export function formatMonth(month) {
  const [year, numericMonth] = month.split('-').map(Number);
  const date = new Date(year, numericMonth - 1, 1);
  return new Intl.DateTimeFormat('ru-RU', { month: 'long', year: 'numeric' }).format(date);
}

export function moveMonth(month, amount) {
  const [year, numericMonth] = month.split('-').map(Number);
  const date = new Date(year, numericMonth - 1 + amount, 1);
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}`;
}

export function currentMoscowMonth() {
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Europe/Moscow', year: 'numeric', month: '2-digit',
  }).formatToParts(new Date());
  const values = Object.fromEntries(parts.map(({ type, value }) => [type, value]));
  return `${values.year}-${values.month}`;
}

export function dateInputValue(dateTime) {
  return dateTime?.slice(0, 10) ?? '';
}

export function dateAtNoon(date) {
  return `${date}T12:00:00+03:00`;
}

export function formatDate(dateTime) {
  return new Intl.DateTimeFormat('ru-RU', {
    day: 'numeric', month: 'short', year: 'numeric', timeZone: 'Europe/Moscow',
  }).format(new Date(dateTime));
}
