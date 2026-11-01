import * as api from '../api/models';

/** Generated from the API; the names here are the ones the app uses. */
export type Company = api.CompanyResponse;
export type UpdateCompanyRequest = api.UpdateCompanyRequest;
export type CompanyType = Company['type'];
export type CareersPlatform = NonNullable<Company['careersPlatform']>;
/** A role open on a watched company's careers board. */
export type Opening = api.JobPostingResponse;

export const COMPANY_TYPE_LABELS: Record<CompanyType, string> = {
  EMPLOYER: 'Employer',
  AGENCY: 'Recruiting agency',
  BOTH: 'Agency that also hires',
};

export const COMPANY_TYPES = Object.keys(COMPANY_TYPE_LABELS) as CompanyType[];

export const CAREERS_PLATFORM_LABELS: Record<CareersPlatform, string> = {
  GREENHOUSE: 'Greenhouse',
  LEVER: 'Lever',
  ASHBY: 'Ashby',
};

/** The public careers page for a watched board, to link to. */
export function careersPageUrl(platform: CareersPlatform, boardId: string): string {
  switch (platform) {
    case 'GREENHOUSE':
      return `https://boards.greenhouse.io/${boardId}`;
    case 'LEVER':
      return `https://jobs.lever.co/${boardId}`;
    case 'ASHBY':
      return `https://jobs.ashbyhq.com/${boardId}`;
  }
}
