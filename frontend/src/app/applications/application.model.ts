import * as api from '../api/models';

/** Generated from the API; the names here are the ones the app uses. */
export type ApplicationResponse = api.ApplicationResponse;
export type StatusChange = api.StatusChangeResponse;
export type CreateApplicationRequest = api.CreateApplicationRequest;
export type UpdateApplicationRequest = api.UpdateApplicationRequest;
export type FollowUpRequest = api.FollowUpRequest;
export type ChangeStatusRequest = api.ChangeStatusRequest;

export type ApplicationStatus = ApplicationResponse['status'];

/** Board column order. */
export const STATUSES = [
  'SAVED',
  'APPLIED',
  'INTERVIEWING',
  'OFFER',
  'ACCEPTED',
  'DECLINED',
  'REJECTED',
  'WITHDRAWN',
] as const satisfies readonly ApplicationStatus[];

// Fails to compile if the API gains a status the board doesn't list.
const everyStatusListed: Exclude<ApplicationStatus, (typeof STATUSES)[number]> extends never
  ? true
  : never = true;
void everyStatusListed;

export const SALARY_PERIODS = ['year', 'month', 'week', 'day', 'hour'] as const;
export type SalaryPeriod = (typeof SALARY_PERIODS)[number];

/** Facts copied from the job ad; shared by responses and requests. */
export type PostingDetails = Required<
  Pick<
    CreateApplicationRequest,
    | 'location'
    | 'remote'
    | 'salaryMin'
    | 'salaryMax'
    | 'salaryCurrency'
    | 'salaryPeriod'
    | 'salaryText'
    | 'source'
    | 'postedOn'
    | 'jobDescription'
  >
>;

/**
 * Error body for 4xx/5xx answers. Hand-written because the field errors live in
 * ProblemDetail's extra properties, which the spec doesn't describe.
 */
export interface ProblemDetail {
  title?: string;
  detail?: string;
  status?: number;
  errors?: Record<string, string[]>;
}
