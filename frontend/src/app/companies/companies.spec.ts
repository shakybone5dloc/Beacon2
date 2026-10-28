import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { anApplication } from '../applications/testing';
import { CompanyDetail } from './company-detail';
import { CompanyList } from './company-list';
import { Company } from './company.model';

function aCompany(overrides: Partial<Company> = {}): Company {
  return {
    id: 'c1',
    name: 'Acme',
    type: 'EMPLOYER',
    website: null,
    emailDomains: [],
    notes: null,
    applicationCount: 1,
    version: 0,
    createdAt: '2026-10-23T12:00:00Z',
    updatedAt: '2026-10-23T12:00:00Z',
    ...overrides,
  };
}

describe('companies', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('CompanyList links each company and counts its applications', async () => {
    const fixture = TestBed.createComponent(CompanyList);
    fixture.detectChanges();
    http
      .expectOne('/api/companies')
      .flush([
        aCompany(),
        aCompany({ id: 'c2', name: 'Globex', type: 'AGENCY', applicationCount: 3 }),
      ]);
    await fixture.whenStable();
    const el: HTMLElement = fixture.nativeElement;

    const rows = Array.from(el.querySelectorAll('.company-table tbody tr')).map((tr) => [
      tr.querySelector('.name')!.textContent!.trim(),
      tr.querySelector('.type')!.textContent!.trim(),
      tr.querySelector('.count')!.textContent!.replace(/\s+/g, ' ').trim(),
    ]);
    expect(rows).toEqual([
      ['Acme', 'Employer', '1 application'],
      ['Globex', 'Recruiting agency', '3 applications'],
    ]);
    expect(el.querySelector('.company-table a')?.getAttribute('href')).toBe('/companies/c1');
  });

  describe('CompanyDetail', () => {
    async function open() {
      const fixture = TestBed.createComponent(CompanyDetail);
      fixture.componentRef.setInput('id', 'c1');
      fixture.detectChanges();
      http.expectOne('/api/companies/c1').flush(aCompany({ emailDomains: ['acme.com'] }));
      http
        .expectOne('/api/companies/c1/applications')
        .flush([anApplication({ id: 'a1', role: 'Java Engineer', status: 'APPLIED' })]);
      http.expectOne('/api/companies/c1/people').flush([]);
      await fixture.whenStable();
      return { fixture, el: fixture.nativeElement as HTMLElement };
    }

    it('shows the company and its applications', async () => {
      const { el } = await open();

      expect(el.querySelector('h1')?.textContent).toContain('Acme');
      expect(el.textContent).toContain('acme.com');
      const app = el.querySelector('.applications a')!;
      expect(app.textContent).toContain('Java Engineer');
      expect(app.getAttribute('href')).toBe('/applications/a1');
    });

    it('saves edits with the version it loaded, splitting the email domains', async () => {
      const { fixture, el } = await open();
      Array.from(el.querySelectorAll('button'))
        .find((b) => b.textContent?.trim() === 'Edit')!
        .click();
      await fixture.whenStable();

      const domains = el.querySelector<HTMLInputElement>('input[formControlName=emailDomains]')!;
      domains.value = 'acme.com, acmejobs.com';
      domains.dispatchEvent(new Event('input'));
      el.querySelector<HTMLFormElement>('form')!.dispatchEvent(new Event('submit'));

      const req = http.expectOne('/api/companies/c1');
      expect(req.request.method).toBe('PUT');
      expect(req.request.body).toEqual({
        name: 'Acme',
        type: 'EMPLOYER',
        website: null,
        emailDomains: ['acme.com', 'acmejobs.com'],
        notes: null,
        version: 0,
      });
      req.flush(aCompany({ emailDomains: ['acme.com', 'acmejobs.com'], version: 1 }));
      http.expectOne('/api/companies/c1/applications').flush([]);
      await fixture.whenStable();

      expect(el.textContent).toContain('acme.com, acmejobs.com');
    });

    it('shows a taken name next to the name field', async () => {
      const { fixture, el } = await open();
      Array.from(el.querySelectorAll('button'))
        .find((b) => b.textContent?.trim() === 'Edit')!
        .click();
      await fixture.whenStable();

      el.querySelector<HTMLFormElement>('form')!.dispatchEvent(new Event('submit'));
      http
        .expectOne('/api/companies/c1')
        .flush(
          { errors: { name: ['You already have a company called Globex.'] } },
          { status: 400, statusText: 'Bad Request' },
        );
      await fixture.whenStable();

      expect(el.textContent).toContain('You already have a company called Globex.');
    });
  });
});
