import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { ApplicationForm } from './application-form';
import { anApplication, noPostingDetails } from '../applications/testing';

describe('ApplicationForm', () => {
  let http: HttpTestingController;
  let navigate: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [ApplicationForm],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    http = TestBed.inject(HttpTestingController);
    navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
  });

  afterEach(() => http.verify());

  describe('creating', () => {
    it('does not submit an empty form and shows Required errors', async () => {
      const fixture = TestBed.createComponent(ApplicationForm);
      await fixture.whenStable();
      const el: HTMLElement = fixture.nativeElement;

      el.querySelector<HTMLButtonElement>('button[type=submit]')!.click();
      await fixture.whenStable();

      http.expectNone('/api/applications'); // nothing was sent
      expect(el.querySelectorAll('.error').length).toBeGreaterThanOrEqual(2); // company + role
    });

    it('shows server validation errors next to the matching field', async () => {
      const fixture = TestBed.createComponent(ApplicationForm);
      await fixture.whenStable();
      const el: HTMLElement = fixture.nativeElement;

      fill(el, 'company', 'Acme');
      fill(el, 'role', 'Engineer');
      el.querySelector<HTMLButtonElement>('button[type=submit]')!.click();

      const req = http.expectOne('/api/applications');
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({
        companyName: 'Acme',
        role: 'Engineer',
        jobUrl: null,
        notes: null,
        ...noPostingDetails,
        applyBy: null,
        followUpOn: null,
      });
      req.flush(
        { errors: { companyName: ['already tracked'] } },
        { status: 400, statusText: 'Bad Request' },
      );
      await fixture.whenStable();

      const companyLabel = el.querySelector('input[formControlName=company]')!.closest('label')!;
      expect(companyLabel.querySelector('.error')?.textContent).toContain('already tracked');
      expect(el.querySelectorAll('.error').length).toBe(1);
    });
  });

  describe('company', () => {
    it('sends no company when the employer is not disclosed', async () => {
      const fixture = TestBed.createComponent(ApplicationForm);
      await fixture.whenStable();
      const el: HTMLElement = fixture.nativeElement;

      input(el, 'undisclosed').click();
      await fixture.whenStable();
      expect(input(el, 'company').disabled).toBe(true);

      fill(el, 'role', 'Java Contractor');
      el.querySelector<HTMLButtonElement>('button[type=submit]')!.click();

      const req = http.expectOne('/api/applications');
      expect(req.request.body.companyName).toBeNull();
      req.flush(anApplication({ company: null, companyId: null }));
    });

    it("suggests the user's companies matching what is typed", async () => {
      const fixture = TestBed.createComponent(ApplicationForm);
      await fixture.whenStable();
      const el: HTMLElement = fixture.nativeElement;

      fill(el, 'company', 'ac');
      await new Promise((resolve) => setTimeout(resolve, 250));
      http
        .expectOne((r) => r.url === '/api/companies' && r.params.get('q') === 'ac')
        .flush([
          {
            id: 'c1',
            name: 'Acme, Inc.',
            type: 'EMPLOYER',
            website: null,
            emailDomains: [],
            notes: null,
            applicationCount: 2,
            version: 0,
            createdAt: '2026-10-23T12:00:00Z',
            updatedAt: '2026-10-23T12:00:00Z',
          },
        ]);
      await fixture.whenStable();

      const options = Array.from(el.querySelectorAll('#company-suggestions option'));
      expect(options.map((o) => o.getAttribute('value'))).toEqual(['Acme, Inc.']);
    });
  });

  it('sends the apply-by and follow-up dates', async () => {
    const fixture = TestBed.createComponent(ApplicationForm);
    await fixture.whenStable();
    const el: HTMLElement = fixture.nativeElement;

    fill(el, 'company', 'Acme');
    fill(el, 'role', 'Engineer');
    fill(el, 'applyBy', '2026-11-15');
    fill(el, 'followUpOn', '2026-11-01');
    el.querySelector<HTMLButtonElement>('button[type=submit]')!.click();

    const req = http.expectOne('/api/applications');
    expect(req.request.body).toMatchObject({ applyBy: '2026-11-15', followUpOn: '2026-11-01' });
    req.flush(anApplication());
  });

  it('sends posting details converted to the API types', async () => {
    const fixture = TestBed.createComponent(ApplicationForm);
    await fixture.whenStable();
    const el: HTMLElement = fixture.nativeElement;

    fill(el, 'company', 'Acme');
    fill(el, 'role', 'Engineer');
    fill(el, 'salaryMin', '140000');
    fill(el, 'salaryMax', '170000');
    fill(el, 'salaryCurrency', 'usd');
    const remote = el.querySelector<HTMLSelectElement>('select[formControlName=remote]')!;
    remote.value = 'yes';
    remote.dispatchEvent(new Event('change'));
    const description = el.querySelector<HTMLTextAreaElement>(
      'textarea[formControlName=jobDescription]',
    )!;
    description.value = '  Build services.  ';
    description.dispatchEvent(new Event('input'));
    el.querySelector<HTMLButtonElement>('button[type=submit]')!.click();

    const req = http.expectOne('/api/applications');
    expect(req.request.body).toMatchObject({
      salaryMin: 140000,
      salaryMax: 170000,
      salaryCurrency: 'USD',
      remote: true,
      jobDescription: 'Build services.',
      location: null,
    });
    req.flush(anApplication());
  });

  describe('pay', () => {
    async function create() {
      const fixture = TestBed.createComponent(ApplicationForm);
      await fixture.whenStable();
      const el: HTMLElement = fixture.nativeElement;
      fill(el, 'company', 'Acme');
      fill(el, 'role', 'Engineer');
      return { fixture, el };
    }

    async function choose(
      fixture: { whenStable(): Promise<unknown> },
      el: HTMLElement,
      type: string,
    ) {
      el.querySelector<HTMLInputElement>(`input[type=radio][value=${type}]`)!.click();
      await fixture.whenStable();
    }

    it('sends a salary range per year by default', async () => {
      const { el } = await create();
      fill(el, 'salaryMin', '120000');
      fill(el, 'salaryMax', '150000');
      el.querySelector<HTMLButtonElement>('button[type=submit]')!.click();

      const req = http.expectOne('/api/applications');
      expect(req.request.body).toMatchObject({
        salaryMin: 120000,
        salaryMax: 150000,
        salaryPeriod: 'year',
      });
      req.flush(anApplication());
    });

    it('sends an hourly rate as one amount per hour', async () => {
      const { fixture, el } = await create();
      await choose(fixture, el, 'hourly');

      expect(el.querySelector('input[formControlName=salaryMin]')).toBeNull();
      fill(el, 'hourlyRate', '45');
      fill(el, 'salaryCurrency', 'usd');
      el.querySelector<HTMLButtonElement>('button[type=submit]')!.click();

      const req = http.expectOne('/api/applications');
      expect(req.request.body).toMatchObject({
        salaryMin: 45,
        salaryMax: null,
        salaryPeriod: 'hour',
        salaryCurrency: 'USD',
      });
      req.flush(anApplication());
    });

    it('ignores a leftover invalid salary once hourly is chosen', async () => {
      const { fixture, el } = await create();
      fill(el, 'salaryMin', '140,000');
      await choose(fixture, el, 'hourly');
      fill(el, 'hourlyRate', '50');
      el.querySelector<HTMLButtonElement>('button[type=submit]')!.click();

      const req = http.expectOne('/api/applications');
      expect(req.request.body).toMatchObject({ salaryMin: 50, salaryPeriod: 'hour' });
      req.flush(anApplication());
    });

    it('sends no period when no amount was entered', async () => {
      const { el } = await create();
      el.querySelector<HTMLButtonElement>('button[type=submit]')!.click();

      const req = http.expectOne('/api/applications');
      expect(req.request.body).toMatchObject({
        salaryMin: null,
        salaryMax: null,
        salaryPeriod: null,
      });
      req.flush(anApplication());
    });

    it('opens an hourly application on the hourly switch with its rate', async () => {
      const fixture = TestBed.createComponent(ApplicationForm);
      fixture.componentRef.setInput('id', 'a1');
      await fixture.whenStable();
      http
        .expectOne('/api/applications/a1')
        .flush(
          anApplication({ id: 'a1', salaryMin: 45, salaryPeriod: 'hour', salaryCurrency: 'USD' }),
        );
      await fixture.whenStable();
      const el: HTMLElement = fixture.nativeElement;

      expect(el.querySelector<HTMLInputElement>('input[type=radio][value=hourly]')!.checked).toBe(
        true,
      );
      expect(el.querySelector<HTMLInputElement>('input[formControlName=hourlyRate]')!.value).toBe(
        '45',
      );
    });
  });

  it('rejects a salary with commas before sending', async () => {
    const fixture = TestBed.createComponent(ApplicationForm);
    await fixture.whenStable();
    const el: HTMLElement = fixture.nativeElement;

    fill(el, 'company', 'Acme');
    fill(el, 'role', 'Engineer');
    fill(el, 'salaryMin', '140,000');
    el.querySelector<HTMLButtonElement>('button[type=submit]')!.click();
    await fixture.whenStable();

    http.expectNone('/api/applications');
    expect(el.textContent).toContain('Whole number, no commas.');
  });

  describe('editing', () => {
    async function openEditor(app = anApplication({ id: 'a1', version: 4 })) {
      const fixture = TestBed.createComponent(ApplicationForm);
      fixture.componentRef.setInput('id', app.id);
      await fixture.whenStable();

      http.expectOne(`/api/applications/${app.id}`).flush(app);
      await fixture.whenStable();
      return { fixture, el: fixture.nativeElement as HTMLElement };
    }

    it('shows an undisclosed employer as such', async () => {
      const { el } = await openEditor(anApplication({ id: 'a1', company: null, companyId: null }));

      expect(input(el, 'undisclosed').checked).toBe(true);
      expect(input(el, 'company').disabled).toBe(true);
    });

    it('loads the application into the form', async () => {
      const { el } = await openEditor(
        anApplication({
          id: 'a1',
          company: 'Globex',
          role: 'Staff Engineer',
          jobUrl: 'https://globex.com/jobs/1',
          notes: 'Referred by Sam',
          applyBy: '2026-11-15',
          followUpOn: '2026-11-01',
        }),
      );

      expect(el.querySelector('h1')?.textContent).toContain('Edit application');
      expect(input(el, 'company').value).toBe('Globex');
      expect(input(el, 'role').value).toBe('Staff Engineer');
      expect(input(el, 'jobUrl').value).toBe('https://globex.com/jobs/1');
      expect(el.querySelector<HTMLTextAreaElement>('textarea[formControlName=notes]')!.value).toBe(
        'Referred by Sam',
      );
      expect(input(el, 'applyBy').value).toBe('2026-11-15');
      expect(input(el, 'followUpOn').value).toBe('2026-11-01');
    });

    it('PUTs the changes with the loaded version and opens the detail page', async () => {
      const { fixture, el } = await openEditor();

      fill(el, 'role', 'Principal Engineer');
      el.querySelector<HTMLButtonElement>('button[type=submit]')!.click();

      const req = http.expectOne('/api/applications/a1');
      expect(req.request.method).toBe('PUT');
      expect(req.request.body).toEqual({
        companyName: 'Acme',
        role: 'Principal Engineer',
        jobUrl: null,
        notes: null,
        ...noPostingDetails,
        applyBy: null,
        followUpOn: null,
        version: 4,
      });
      req.flush(anApplication({ id: 'a1', role: 'Principal Engineer', version: 5 }));
      await fixture.whenStable();

      expect(navigate).toHaveBeenCalledWith(['/applications', 'a1']);
    });

    it('keeps the edits on a 409 and can load the latest version', async () => {
      const { fixture, el } = await openEditor();

      fill(el, 'role', 'My unsaved edit');
      el.querySelector<HTMLButtonElement>('button[type=submit]')!.click();
      http
        .expectOne('/api/applications/a1')
        .flush({ title: 'Conflict' }, { status: 409, statusText: 'Conflict' });
      await fixture.whenStable();

      expect(el.querySelector('.notice')?.textContent).toContain('changed somewhere else');
      expect(input(el, 'role').value).toBe('My unsaved edit');
      expect(navigate).not.toHaveBeenCalled();

      el.querySelector<HTMLButtonElement>('.notice button')!.click();
      http
        .expectOne('/api/applications/a1')
        .flush(anApplication({ id: 'a1', role: 'Their edit', version: 5 }));
      await fixture.whenStable();

      expect(el.querySelector('.notice')).toBeNull();
      expect(input(el, 'role').value).toBe('Their edit');
    });

    it('shows a notice instead of the form when the application does not exist', async () => {
      const fixture = TestBed.createComponent(ApplicationForm);
      fixture.componentRef.setInput('id', 'gone');
      await fixture.whenStable();

      http
        .expectOne('/api/applications/gone')
        .flush(null, { status: 404, statusText: 'Not Found' });
      await fixture.whenStable();

      const el: HTMLElement = fixture.nativeElement;
      expect(el.textContent).toContain('no longer exists');
      expect(el.querySelector('form')).toBeNull();
    });
  });
});

function input(el: HTMLElement, name: string): HTMLInputElement {
  return el.querySelector<HTMLInputElement>(`input[formControlName=${name}]`)!;
}

function fill(el: HTMLElement, name: string, value: string): void {
  const field = input(el, name);
  field.value = value;
  field.dispatchEvent(new Event('input'));
}
