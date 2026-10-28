import * as api from '../api/models';

/** Generated from the API; the names here are the ones the app uses. */
export type Person = api.PersonResponse;
export type CreatePersonRequest = api.CreatePersonRequest;
export type UpdatePersonRequest = api.UpdatePersonRequest;
/** Someone on an application, with their role there. */
export type ApplicationPerson = api.ApplicationPersonResponse;
/** An application someone is on, with their role there. */
export type PersonApplication = api.PersonApplicationResponse;

export type PersonRole = Person['role'];
export type RecruiterType = NonNullable<Person['recruiterType']>;

export const PERSON_ROLE_LABELS: Record<PersonRole, string> = {
  RECRUITER: 'Recruiter',
  HIRING_MANAGER: 'Hiring manager',
  INTERVIEWER: 'Interviewer',
  REFERRAL: 'Referral',
  OTHER: 'Other',
};
export const PERSON_ROLES = Object.keys(PERSON_ROLE_LABELS) as PersonRole[];

export const RECRUITER_TYPE_LABELS: Record<RecruiterType, string> = {
  IN_HOUSE: 'In-house',
  AGENCY: 'Agency',
  RPO: 'RPO (outsourced hiring)',
  INDEPENDENT: 'Independent',
};
export const RECRUITER_TYPES = Object.keys(RECRUITER_TYPE_LABELS) as RecruiterType[];
