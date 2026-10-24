export interface JobSource {
  id: string;
  name: string;
  coverage: string;
  /** Credit link shown with every result; most free feeds require it. */
  attributionUrl: string;
  configured: boolean;
  /** How to switch the source on; null once configured. */
  setupHint: string | null;
}

export interface JobPosting {
  source: string;
  id: string;
  title: string;
  company: string | null;
  location: string | null;
  remote: boolean;
  /** The source's page for the job; their terms require linking here. */
  url: string;
  postedAt: string | null;
  /** Last day to apply or the listing's end (YYYY-MM-DD). */
  closesOn: string | null;
  salaryMin: number | null;
  salaryMax: number | null;
  salaryCurrency: string | null;
  salaryPeriod: string | null;
  salaryText: string | null;
  tags: string[];
  snippet: string | null;
  /** The full ad as plain text, when the source provides it. */
  description: string | null;
}

export interface SourceResult {
  id: string;
  count: number;
  error: string | null;
}

export interface PostingSearchResponse {
  postings: JobPosting[];
  sources: SourceResult[];
}

export interface PostingQuery {
  keywords: string;
  location: string;
  remoteOnly: boolean;
  /** Empty means every configured source. */
  sources: string[];
}
