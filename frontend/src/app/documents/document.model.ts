export const DOCUMENT_KINDS = ['POSTING', 'RESUME', 'COVER_LETTER', 'OTHER'] as const;
export type DocumentKind = (typeof DOCUMENT_KINDS)[number];

export const KIND_LABELS: Record<DocumentKind, string> = {
  POSTING: 'Job posting',
  RESUME: 'Resume',
  COVER_LETTER: 'Cover letter',
  OTHER: 'Other',
};

export interface DocumentResponse {
  id: string;
  /** Null for library documents. */
  applicationId: string | null;
  kind: DocumentKind;
  filename: string;
  contentType: string;
  sizeBytes: number;
  createdAt: string;
}

export interface CaptureResponse {
  document: DocumentResponse;
  /** True when the page text became the job description. */
  descriptionSaved: boolean;
}

/** File types the server accepts, for the file picker. */
export const ACCEPTED_FILES = '.pdf,.doc,.docx,.odt,.rtf,.txt,.md,.png,.jpg,.jpeg';
