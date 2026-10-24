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

export const SALARY_PERIODS = ['year', 'month', 'week', 'day', 'hour'] as const;
export type SalaryPeriod = (typeof SALARY_PERIODS)[number];

/** Facts copied from the job ad; shared by responses and requests. */
export interface PostingDetails {
  location: string | null;
  remote: boolean | null;
  salaryMin: number | null;
  salaryMax: number | null;
  /** 3-letter code, e.g. USD. */
  salaryCurrency: string | null;
  salaryPeriod: string | null;
  /** Free-text pay for ads without numbers. */
  salaryText: string | null;
  /** Where it was found: a job board, "Referral", "Company site"... */
  source: string | null;
  /** YYYY-MM-DD */
  postedOn: string | null;
  /** Full text of the ad. Null in list responses. */
  jobDescription: string | null;
}

export interface ApplicationResponse extends PostingDetails {
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
  /** When it first moved to APPLIED; null if never, and in list responses. */
  appliedAt: string | null;
  version: number;
  allowedTransitions: ApplicationStatus[];
}

export interface StatusChange {
  /** Null for the entry recorded when the application was created. */
  fromStatus: ApplicationStatus | null;
  toStatus: ApplicationStatus;
  changedAt: string;
}

export interface CreateApplicationRequest extends PostingDetails {
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
