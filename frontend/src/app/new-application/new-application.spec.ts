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

  // TODO 9 (stretch): Fill in company and role, submit, answer the POST with
  //   .flush({ errors: { company: ['already tracked'] } }, { status: 400, statusText: 'Bad Request' })
  //   and assert 'already tracked' appears on the page.
  //   Filling an input in a test:
  //     const input = el.querySelector<HTMLInputElement>('input[formControlName=company]')!;
  //     input.value = 'Acme';
  //     input.dispatchEvent(new Event('input'));   // tells the form the value changed
});
