import * as api from '../api/models';

/** Generated from the API; the names here are the ones the app uses. */
export type JobSource = api.JobSourceResponse;
export type JobPosting = api.JobPostingResponse;
export type SourceResult = api.SourceResultResponse;
export type PostingSearchResponse = api.PostingSearchResponse;

/** What the Find jobs form asks for. */
export interface PostingQuery {
  keywords: string;
  location: string;
  remoteOnly: boolean;
  /** Empty means every usable source. */
  sources: string[];
}
