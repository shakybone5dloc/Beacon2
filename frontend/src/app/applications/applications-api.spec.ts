import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ApplicationsApi } from './applications-api';
import { noPostingDetails } from './testing';

describe('ApplicationsApi', () => {
  let service: ApplicationsApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(ApplicationsApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('changeStatus PATCHes the status and version', () => {
    service.changeStatus('abc', 'APPLIED', 3).subscribe();

    const req = http.expectOne('/api/applications/abc/status');
    expect(req.request.method).toBe('PATCH');
    expect(req.request.body).toEqual({ status: 'APPLIED', version: 3 });
    req.flush({});
  });

  it('create POSTs request to /api/applications', () => {
    let request = {
      company: 'Acme',
      role: 'Engineer',
      jobUrl: 'http://example.com',
      notes: 'something here',
      ...noPostingDetails,
      applyBy: '2026-11-15',
      followUpOn: null,
    };
    service.create(request).subscribe();

    const req = http.expectOne('/api/applications');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual(request);
    req.flush({});
  });

  it('history GETs /api/applications/:id/history', () => {
    service.history('abc').subscribe();

    const req = http.expectOne('/api/applications/abc/history');
    expect(req.request.method).toBe('GET');
    req.flush([]);
  });

  it('update PUTs the details and version to /api/applications/:id', () => {
    const request = {
      company: 'Acme',
      role: 'Engineer',
      jobUrl: null,
      notes: null,
      ...noPostingDetails,
      applyBy: null,
      followUpOn: '2026-11-07',
      version: 2,
    };
    service.update('abc', request).subscribe();

    const req = http.expectOne('/api/applications/abc');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual(request);
    req.flush({});
  });

  it('scheduleFollowUp PUTs the date and version', () => {
    service.scheduleFollowUp('abc', '2026-11-07', 2).subscribe();

    const req = http.expectOne('/api/applications/abc/follow-up');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ followUpOn: '2026-11-07', version: 2 });
    req.flush({});
  });

  it('delete DELETEs /api/applications/:id', () => {
    service.delete('abc').subscribe();

    const req = http.expectOne('/api/applications/abc');
    expect(req.request.method).toBe('DELETE');
    req.flush(null, { status: 204, statusText: 'No Content' });
  });
});
