function pad(value: number) {
  return String(value).padStart(2, '0');
}
export function toLocalDateTimeValue(date: Date) {
  return [
    date.getFullYear(),
    '-',
    pad(date.getMonth() + 1),
    '-',
    pad(date.getDate()),
    'T',
    pad(date.getHours()),
    ':',
    pad(date.getMinutes()),
  ].join('');
}
export function nowAsLocalDateTimeValue() {
  return toLocalDateTimeValue(new Date());
}
