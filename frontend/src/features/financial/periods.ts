export type PeriodPreset = 'today' | 'week' | 'month' | 'year' | 'custom';

type DateParts = { year: number; month: number; day: number };

const formatter = new Intl.DateTimeFormat('en-CA', {
  timeZone: 'America/Bahia',
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
});

function dateParts(year: number | undefined, month: number | undefined, day: number | undefined) {
  if (year === undefined || month === undefined || day === undefined) {
    throw new Error('Data inválida.');
  }
  return { year, month, day };
}

function parts(value: Date): DateParts {
  const values = Object.fromEntries(
    formatter
      .formatToParts(value)
      .filter((item) => item.type !== 'literal')
      .map((item) => [item.type, Number(item.value)]),
  );
  return dateParts(values.year, values.month, values.day);
}

function instant(value: DateParts) {
  const y = String(value.year).padStart(4, '0');
  const m = String(value.month).padStart(2, '0');
  const d = String(value.day).padStart(2, '0');
  return new Date(`${y}-${m}-${d}T00:00:00-03:00`).toISOString();
}

function shift(value: DateParts, days: number): DateParts {
  const date = new Date(Date.UTC(value.year, value.month - 1, value.day + days));
  return { year: date.getUTCFullYear(), month: date.getUTCMonth() + 1, day: date.getUTCDate() };
}

export function periodFor(preset: Exclude<PeriodPreset, 'custom'>, now = new Date()) {
  const current = parts(now);
  if (preset === 'today') return { from: instant(current), to: instant(shift(current, 1)) };
  if (preset === 'week') {
    const weekday = new Date(Date.UTC(current.year, current.month - 1, current.day)).getUTCDay();
    const monday = shift(current, -(weekday === 0 ? 6 : weekday - 1));
    return { from: instant(monday), to: instant(shift(monday, 7)) };
  }
  if (preset === 'year') {
    return {
      from: instant({ year: current.year, month: 1, day: 1 }),
      to: instant({ year: current.year + 1, month: 1, day: 1 }),
    };
  }
  const nextMonth =
    current.month === 12
      ? { year: current.year + 1, month: 1, day: 1 }
      : { year: current.year, month: current.month + 1, day: 1 };
  return {
    from: instant({ year: current.year, month: current.month, day: 1 }),
    to: instant(nextMonth),
  };
}

export function customPeriod(from: string, toInclusive: string) {
  const fromParts = dateParts(...from.split('-').map(Number));
  const toParts = dateParts(...toInclusive.split('-').map(Number));
  return {
    from: instant(fromParts),
    to: instant(shift(toParts, 1)),
  };
}
