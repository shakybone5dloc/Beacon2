import * as api from '../api/models';

/** Generated from the API; the names here are the ones the app uses. */
export type Company = api.CompanyResponse;
export type UpdateCompanyRequest = api.UpdateCompanyRequest;
export type CompanyType = Company['type'];

export const COMPANY_TYPE_LABELS: Record<CompanyType, string> = {
  EMPLOYER: 'Employer',
  AGENCY: 'Recruiting agency',
  BOTH: 'Agency that also hires',
};

export const COMPANY_TYPES = Object.keys(COMPANY_TYPE_LABELS) as CompanyType[];
