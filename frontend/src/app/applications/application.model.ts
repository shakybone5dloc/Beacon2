export const STATUSES = [
  'SAVED',
  'APPLIED',
  'INTERVIEWING',
  'OFFER',
  'ACCEPTED',
  'DECLINED',
  'REJECTED',
  'WITHDRAWN',
] as const;

export type ApplicationStatus = (typeof STATUSES)[number];

export interface ApplicationResponse {
  id: string;
  company: string;
  role: string;
  jobUrl: string | null;
  notes: string | null;
  /** Last day the posting accepts applications (YYYY-MM-DD). */
  applyBy: string | null;
  /** When to chase this application next (YYYY-MM-DD). */
  followUpOn: string | null;
  status: ApplicationStatus;
  createdAt: string;
  updatedAt: string;
  /** When the application entered its current status. */
  statusChangedAt: string;
  version: number;
  allowedTransitions: ApplicationStatus[];
}

export interface StatusChange {
  /** Null for the entry recorded when the application was created. */
  fromStatus: ApplicationStatus | null;
  toStatus: ApplicationStatus;
  changedAt: string;
}

export interface CreateApplicationRequest {
  company: string;
  role: string;
  jobUrl: string | null;
  notes: string | null;
  applyBy: string | null;
  followUpOn: string | null;
}

export interface UpdateApplicationRequest extends CreateApplicationRequest {
  version: number;
}

export interface FollowUpRequest {
  /** Null clears the follow-up. */
  followUpOn: string | null;
  version: number;
}

export interface ChangeStatusRequest {
  status: ApplicationStatus;
  version: number;
}

export interface ProblemDetail {
  title?: string;
  detail?: string;
  status?: number;
  errors?: Record<string, string[]>;
}
