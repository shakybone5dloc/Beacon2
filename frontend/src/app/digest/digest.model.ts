import * as api from '../api/models';

/** Generated from the API; the names here are the ones the app uses. */
export type DigestSettings = api.DigestSettingsResponse;
export type DigestPreview = api.DigestPreviewResponse;
export type SaveDigestSettings = api.SaveDigestSettingsRequest;

export const DAYS: { value: SaveDigestSettings['dayOfWeek']; label: string }[] = [
  { value: 'MONDAY', label: 'Monday' },
  { value: 'TUESDAY', label: 'Tuesday' },
  { value: 'WEDNESDAY', label: 'Wednesday' },
  { value: 'THURSDAY', label: 'Thursday' },
  { value: 'FRIDAY', label: 'Friday' },
  { value: 'SATURDAY', label: 'Saturday' },
  { value: 'SUNDAY', label: 'Sunday' },
];

/** "8 AM", "12 PM", "5 PM" for each hour of the day. */
export function hourLabel(hour: number): string {
  const h = hour % 12 === 0 ? 12 : hour % 12;
  return `${h} ${hour < 12 ? 'AM' : 'PM'}`;
}

export const HOURS = Array.from({ length: 24 }, (_, hour) => ({
  value: hour,
  label: hourLabel(hour),
}));
