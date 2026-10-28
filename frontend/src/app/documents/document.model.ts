import * as api from '../api/models';

/** Generated from the API; the names here are the ones the app uses. */
export type DocumentResponse = api.DocumentResponse;
export type CaptureResponse = api.CaptureResponse;

export type DocumentKind = DocumentResponse['kind'];

export const DOCUMENT_KINDS = [
  'POSTING',
  'RESUME',
  'COVER_LETTER',
  'OTHER',
] as const satisfies readonly DocumentKind[];

export const KIND_LABELS: Record<DocumentKind, string> = {
  POSTING: 'Job posting',
  RESUME: 'Resume',
  COVER_LETTER: 'Cover letter',
  OTHER: 'Other',
};

/** File types the server accepts, for the file picker. */
export const ACCEPTED_FILES = '.pdf,.doc,.docx,.odt,.rtf,.txt,.md,.png,.jpg,.jpeg';
