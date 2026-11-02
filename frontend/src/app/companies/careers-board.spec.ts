import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { CareersBoard } from './careers-board';
import { boardLabel, careersPageUrl, Company, Opening } from './company.model';

function aCompany(overrides: Partial<Company> = {}): Company {
  return {
    id: 'c1',
    name: 'Acme',
    type: 'EMPLOYER',
    website: null,
    emailDomains: [],
    notes: null,
    applicationCount: 1,
    careersPlatform: null,
    careersBoardId: null,
    version: 0,
    createdAt: '2026-10-23T12:00:00Z',
    updatedAt: '2026-10-23T12:00:00Z',
    ...overrides,
  };
}

const watched = aCompany({ careersPlatform: 'GREENHOUSE', careersBoardId: 'acme', version: 1 });

const opening: Opening = {
  source: 'watchlist',
  id: 'greenhouse:1',
  title: 'Senior Java Engineer',
  company: 'Acme',
  location: 'Indianapolis, IN',
  remote: true,
  url: 'https://boards.greenhouse.io/acme/jobs/1',
  postedAt: '2026-10-28T12:00:00Z',
  closesOn: null,
  salaryMin: null,
  salaryMax: null,
  salaryCurrency: null,
  salaryPeriod: null,
  salaryText: null,
  tags: [],
  snippet: null,
  description: null,
};

describe('CareersBoard', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  async function open(company: Company) {
    const fixture = TestBed.createComponent(CareersBoard);
    fixture.componentRef.setInput('company', company);
    const changed = vi.fn();
    fixture.componentInstance.changed.subscribe(changed);
    await fixture.whenStable();
    return { fixture, changed, el: fixture.nativeElement as HTMLElement };
  }

  function type(input: HTMLInputElement, value: string): void {
    input.value = value;
    input.dispatchEvent(new Event('input'));
  }

  it('watches a company from its careers page link', async () => {
    const { fixture, changed, el } = await open(aCompany());

    type(
      el.querySelector('input[aria-label="Careers page link"]')!,
      ' https://boards.greenhouse.io/acme ',
    );
    el.querySelector('form')!.dispatchEvent(new Event('submit'));

    const req = http.expectOne(
      (r) => r.method === 'PUT' && r.url === '/api/companies/c1/careers-board',
    );
    expect(req.request.body).toEqual({ url: 'https://boards.greenhouse.io/acme' });
    req.flush(watched);
    await fixture.whenStable();

    expect(changed).toHaveBeenCalledWith(watched);
  });

  it('shows why a link was turned down', async () => {
    const { fixture, el } = await open(aCompany());

    type(el.querySelector('input')!, 'https://acme.example/careers');
    el.querySelector('form')!.dispatchEvent(new Event('submit'));
    http
      .expectOne('/api/companies/c1/careers-board')
      .flush(
        { errors: { url: ['Paste a Greenhouse, Lever or Ashby careers page link.'] } },
        { status: 400, statusText: 'Bad Request' },
      );
    await fixture.whenStable();

    expect(el.querySelector('.error')?.textContent).toContain('Greenhouse, Lever or Ashby');
  });

  it('lists the open roles of a watched company and can stop watching', async () => {
    const { fixture, changed, el } = await open(watched);
    http.expectOne('/api/companies/c1/openings').flush([opening]);
    await fixture.whenStable();

    expect(el.querySelector('.watching')?.textContent).toContain('on Greenhouse');
    expect(el.querySelector('.watching a')?.getAttribute('href')).toBe(
      'https://boards.greenhouse.io/acme',
    );
    const role = el.querySelector('.openings li')!;
    expect(role.querySelector('a')?.getAttribute('href')).toBe(opening.url);
    expect(role.textContent!.replace(/\s+/g, ' ')).toContain('Indianapolis, IN · Remote');

    Array.from(el.querySelectorAll('button'))
      .find((b) => b.textContent?.includes('Stop watching'))!
      .click();
    http
      .expectOne((r) => r.method === 'DELETE' && r.url === '/api/companies/c1/careers-board')
      .flush(aCompany({ version: 2 }));

    expect(changed).toHaveBeenCalledWith(aCompany({ version: 2 }));
  });

  it('shows the newest ten roles until asked for all', async () => {
    const many = Array.from({ length: 12 }, (_, i) => ({ ...opening, id: `greenhouse:${i}` }));
    const { fixture, el } = await open(watched);
    http.expectOne('/api/companies/c1/openings').flush(many);
    await fixture.whenStable();

    expect(el.querySelectorAll('.openings li').length).toBe(10);
    Array.from(el.querySelectorAll('button'))
      .find((b) => b.textContent?.includes('Show all 12 roles'))!
      .click();
    await fixture.whenStable();

    expect(el.querySelectorAll('.openings li').length).toBe(12);
  });

  it('says so when the board cannot be read', async () => {
    const { fixture, el } = await open(watched);
    http
      .expectOne('/api/companies/c1/openings')
      .flush(
        { detail: "Couldn't read Acme's careers board right now." },
        { status: 502, statusText: 'Bad Gateway' },
      );
    await fixture.whenStable();

    expect(el.querySelector('.notice')?.textContent).toContain(
      "Couldn't read Acme's careers board",
    );
  });

  it('links each platform to its public careers page', () => {
    expect(careersPageUrl('LEVER', 'acme')).toBe('https://jobs.lever.co/acme');
    expect(careersPageUrl('ASHBY', 'acme')).toBe('https://jobs.ashbyhq.com/acme');
    expect(careersPageUrl('WORKDAY', 'elevancehealth.wd1/ANT')).toBe(
      'https://elevancehealth.wd1.myworkdayjobs.com/ANT',
    );
    expect(careersPageUrl('ORACLE', 'eeho.fa.us2/CX_45001')).toBe(
      'https://eeho.fa.us2.oraclecloud.com/hcmUI/CandidateExperience/en/sites/CX_45001',
    );
    expect(boardLabel('WORKDAY', 'elevancehealth.wd1/ANT')).toBe('ANT');
    expect(boardLabel('LEVER', 'acme')).toBe('acme');
  });

  it('says a large board lists only its newest roles here', async () => {
    const { el } = await open(
      aCompany({
        careersPlatform: 'WORKDAY',
        careersBoardId: 'elevancehealth.wd1/ANT',
        version: 1,
      }),
    );
    http.expectOne('/api/companies/c1/openings').flush([opening]);

    expect(el.querySelector('.watching a')?.textContent?.trim()).toBe('ANT');
    expect(el.textContent).toContain('only the newest roles are listed here');
  });
});
