const HOUR = 60 * 60 * 1000;
const DAY = 24 * HOUR;

/** Rough, human-sized gap between two instants, e.g. "3 days" or "5 hours". */
export function durationBetween(from: string | Date, to: string | Date): string {
  const ms = new Date(to).getTime() - new Date(from).getTime();

  if (ms < HOUR) return 'less than an hour';
  if (ms < DAY) return plural(Math.floor(ms / HOUR), 'hour');
  return plural(Math.floor(ms / DAY), 'day');
}

function plural(n: number, unit: string): string {
  return `${n} ${unit}${n === 1 ? '' : 's'}`;
}
