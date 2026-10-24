import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { DocumentsApi } from './documents-api';
import { CoverLettersApi } from './cover-letters-api';
import { aDocument } from './testing';

describe('DocumentsApi', () => {
  let api: DocumentsApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(DocumentsApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lists an application or the library', () => {
    api.list('a1').subscribe();
    api.list(null).subscribe();

    http.expectOne('/api/applications/a1/documents').flush([]);
    http.expectOne('/api/documents').flush([]);
  });

  it('uploads the file and kind as form data', () => {
    const file = new File(['hello'], 'resume.pdf', { type: 'application/pdf' });

    api.upload('a1', file, 'RESUME').subscribe();

    const req = http.expectOne('/api/applications/a1/documents');
    expect(req.request.method).toBe('POST');
    const body = req.request.body as FormData;
    expect((body.get('file') as File).name).toBe('resume.pdf');
    expect(body.get('kind')).toBe('RESUME');
    req.flush(aDocument());
  });

  it('deletes and builds content links for both places', () => {
    api.delete(null, 'd1').subscribe();
    http.expectOne((r) => r.method === 'DELETE' && r.url === '/api/documents/d1').flush(null);

    expect(api.contentUrl(aDocument({ id: 'd2', applicationId: 'a1' }))).toBe(
      '/api/applications/a1/documents/d2/content',
    );
    expect(api.contentUrl(aDocument({ id: 'd3', applicationId: null }))).toBe(
      '/api/documents/d3/content',
    );
  });

  it('asks the server to capture the posting', () => {
    api.capturePosting('a1').subscribe();

    const req = http.expectOne('/api/applications/a1/documents/posting-capture');
    expect(req.request.method).toBe('POST');
    req.flush({ document: aDocument(), descriptionSaved: true });
  });
});

describe('CoverLettersApi', () => {
  let api: CoverLettersApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(CoverLettersApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('reports whether drafting is configured', () => {
    let configured: boolean | undefined;
    api.isConfigured().subscribe((c) => (configured = c));

    http.expectOne('/api/cover-letters/status').flush({ configured: true });

    expect(configured).toBe(true);
  });

  it('drafts and saves', () => {
    let draft: string | undefined;
    api.draft('a1', 'r1', 'Mention Denver').subscribe((t) => (draft = t));
    const draftReq = http.expectOne('/api/applications/a1/cover-letter/draft');
    expect(draftReq.request.body).toEqual({
      resumeDocumentId: 'r1',
      instructions: 'Mention Denver',
    });
    draftReq.flush({ text: 'Dear Hiring Team' });
    expect(draft).toBe('Dear Hiring Team');

    api.save('a1', 'Dear Hiring Team').subscribe();
    const saveReq = http.expectOne('/api/applications/a1/cover-letter');
    expect(saveReq.request.body).toEqual({ text: 'Dear Hiring Team' });
    saveReq.flush(aDocument({ kind: 'COVER_LETTER' }));
  });
});
