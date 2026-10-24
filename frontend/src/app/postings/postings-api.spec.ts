import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { PostingsApi } from './postings-api';

describe('PostingsApi', () => {
  let api: PostingsApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    api = TestBed.inject(PostingsApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('sources GETs /api/postings/sources', () => {
    api.sources().subscribe();

    http.expectOne('/api/postings/sources').flush([]);
  });

  it('search sends every filter that is set', () => {
    api
      .search({
        keywords: 'java spring',
        location: 'USA',
        remoteOnly: true,
        sources: ['remotive', 'jobicy'],
      })
      .subscribe();

    const req = http.expectOne((r) => r.url === '/api/postings/search');
    expect(req.request.params.get('keywords')).toBe('java spring');
    expect(req.request.params.get('location')).toBe('USA');
    expect(req.request.params.get('remoteOnly')).toBe('true');
    expect(req.request.params.get('sources')).toBe('remotive,jobicy');
    req.flush({ postings: [], sources: [] });
  });

  it('search leaves out filters that are not set', () => {
    api.search({ keywords: 'java', location: '', remoteOnly: false, sources: [] }).subscribe();

    const req = http.expectOne((r) => r.url === '/api/postings/search');
    expect(req.request.params.keys()).toEqual(['keywords']);
    req.flush({ postings: [], sources: [] });
  });
});
