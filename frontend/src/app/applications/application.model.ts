export const STATUSES = [
  'SAVED',
  'APPLIED',
  'INTERVIEWING',
  'OFFER',
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
  status: ApplicationStatus;
  createdAt: string;
  updatedAt: string;
  version: number;
  allowedTransitions: ApplicationStatus[];
}
