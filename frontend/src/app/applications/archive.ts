import { ApplicationResponse, ApplicationStatus } from './application.model';

/** How long a closed application stays on the board before it moves to the archive. */
export const ARCHIVE_AFTER_DAYS = 14;

export const CLOSED_STATUSES: readonly ApplicationStatus[] = [
  'ACCEPTED',
  'DECLINED',
  'REJECTED',
  'WITHDRAWN',
];

const DAY_MS = 24 * 60 * 60 * 1000;

/**
 * Closed more than {@link ARCHIVE_AFTER_DAYS} days ago. Nothing is stored: the board just stops
 * showing it, and moving it back to an active stage brings it back.
 */
export function isArchived(app: ApplicationResponse, now: Date): boolean {
  return (
    CLOSED_STATUSES.includes(app.status) &&
    now.getTime() - new Date(app.statusChangedAt).getTime() > ARCHIVE_AFTER_DAYS * DAY_MS
  );
}
