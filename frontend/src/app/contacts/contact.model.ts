import * as api from '../api/models';

/** Generated from the API; the names here are the ones the app uses. */
export type Contact = api.ContactResponse;
export type CreateContactRequest = api.CreateContactRequest;
export type UpdateContactRequest = api.UpdateContactRequest;
/** Another application that looks like the same job. */
export type PossibleDuplicate = api.PossibleDuplicateResponse;

export type ContactChannel = Contact['channel'];
export type ContactDirection = Contact['direction'];

export const CHANNEL_LABELS: Record<ContactChannel, string> = {
  LINKEDIN: 'LinkedIn',
  EMAIL: 'Email',
  SMS: 'Text',
  PHONE: 'Phone call',
  OTHER: 'Other',
};
export const CHANNELS = Object.keys(CHANNEL_LABELS) as ContactChannel[];

/** Material Symbols names. */
export const CHANNEL_ICONS: Record<ContactChannel, string> = {
  LINKEDIN: 'forum',
  EMAIL: 'mail',
  SMS: 'sms',
  PHONE: 'call',
  OTHER: 'chat_bubble',
};

/** "2026-10-28T14:05" in local time, for a datetime-local input. */
export function toLocalInput(date: Date): string {
  const pad = (n: number) => String(n).padStart(2, '0');
  return (
    `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}` +
    `T${pad(date.getHours())}:${pad(date.getMinutes())}`
  );
}
