import * as api from '../api/models';

/** Generated from the API; the names here are the ones the app uses. */
export type CheckIn = api.CheckInResponse;
export type CheckInRecipient = api.CheckInRecipient;
export type CheckInDraft = api.CheckInDraftResponse;
export type MailClient = CheckIn['mailClient'];

export const MAIL_CLIENT_LABELS: Record<MailClient, string> = {
  GMAIL: 'Gmail',
  MAIL_APP: 'my mail app',
};

/** Teams offered when a contact has no one person to name; any other text works too. */
export const TEAM_SUGGESTIONS = ['Hiring team', 'Recruiting', 'HR', 'Talent acquisition'];

/**
 * The link that opens a new message, filled in, for the user to send themselves: a Gmail
 * compose window, or a mailto link for the device's own mail program.
 */
export function composeUrl(client: MailClient, to: string, subject: string, body: string): string {
  const enc = encodeURIComponent;
  if (client === 'GMAIL') {
    return `https://mail.google.com/mail/?view=cm&fs=1&to=${enc(to)}&su=${enc(subject)}&body=${enc(body)}`;
  }
  // Mail programs expect CRLF line breaks in a mailto body.
  return `mailto:${enc(to)}?subject=${enc(subject)}&body=${enc(body.replace(/\r?\n/g, '\r\n'))}`;
}

/**
 * Re-addresses a message whose first line is still a plain greeting ("Hello," or
 * "Hello Pat,"): to the person's first name, or to nobody for a team. A greeting the user
 * rewrote is left alone.
 */
export function greet(body: string, name: string | null): string {
  const first = name?.trim().split(/\s+/)[0];
  return body.replace(/^Hello(?: [^,\n]*)?,/, first ? `Hello ${first},` : 'Hello,');
}

/** YYYY-MM-DD, this many days from now, in the viewer's time zone. */
export function daysFromNow(days: number, now = new Date()): string {
  const date = new Date(now.getFullYear(), now.getMonth(), now.getDate() + days);
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}
