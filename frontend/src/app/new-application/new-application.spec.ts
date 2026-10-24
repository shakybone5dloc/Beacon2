import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { NewApplication } from './new-application';

describe('NewApplication', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [NewApplication],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('does not submit an empty form and shows Required errors', async () => {
    const fixture = TestBed.createComponent(NewApplication);
    await fixture.whenStable();
    const el: HTMLElement = fixture.nativeElement;

    el.querySelector<HTMLButtonElement>('button[type=submit]')!.click();
    await fixture.whenStable();

    http.expectNone('/api/applications'); // nothing was sent
    expect(el.querySelectorAll('.error').length).toBeGreaterThanOrEqual(2); // company + role
  });

  it('shows server validation errors next to the matching field', async () => {
    const fixture = TestBed.createComponent(NewApplication);
    await fixture.whenStable();
    const el: HTMLElement = fixture.nativeElement;

    fill(el, 'company', 'Acme');
    fill(el, 'role', 'Engineer');
    el.querySelector<HTMLButtonElement>('button[type=submit]')!.click();

    const req = http.expectOne('/api/applications');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({
      company: 'Acme',
      role: 'Engineer',
      jobUrl: null,
      notes: null,
    });
    req.flush(
      { errors: { company: ['already tracked'] } },
      { status: 400, statusText: 'Bad Request' },
    );
    await fixture.whenStable();

    const companyLabel = el.querySelector('input[formControlName=company]')!.closest('label')!;
    expect(companyLabel.querySelector('.error')?.textContent).toContain('already tracked');
    expect(el.querySelectorAll('.error').length).toBe(1);
  });
});

function fill(el: HTMLElement, name: string, value: string): void {
  const input = el.querySelector<HTMLInputElement>(`input[formControlName=${name}]`)!;
  input.value = value;
  input.dispatchEvent(new Event('input'));
}
