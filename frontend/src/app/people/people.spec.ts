import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { ApplicationPeople } from './application-people';
import { PeopleList } from './people-list';
import { PersonDetail } from './person-detail';
import { PersonForm } from './person-form';
import { ApplicationPerson, Person } from './person.model';

function aPerson(overrides: Partial<Person> = {}): Person {
  return {
    id: 'p1',
    name: 'Pat Lee',
    companyId: 'c1',
    company: 'Robert Half',
    title: 'Senior Recruiter',
    role: 'RECRUITER',
    recruiterType: 'AGENCY',
    email: 'pat@roberthalf.example',
    phone: null,
    linkedinUrl: null,
    notes: null,
    version: 0,
    createdAt: '2026-10-23T12:00:00Z',
    updatedAt: '2026-10-23T12:00:00Z',
    ...overrides,
  };
}

function onApp(person: Person, role = person.role): ApplicationPerson {
  return { person, role, addedAt: '2026-10-24T12:00:00Z' };
}

function type(input: HTMLInputElement | HTMLSelectElement, value: string): void {
  input.value = value;
  input.dispatchEvent(new Event(input instanceof HTMLSelectElement ? 'change' : 'input'));
}

const wait = (ms: number) => new Promise((resolve) => setTimeout(resolve, ms));

describe('people', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  describe('PersonForm', () => {
    it('creates a person, naming their company and keeping the recruiter type', async () => {
      const fixture = TestBed.createComponent(PersonForm);
      const saved = vi.fn();
      fixture.componentInstance.saved.subscribe(saved);
      await fixture.whenStable();
      const el: HTMLElement = fixture.nativeElement;

      type(el.querySelector('input[formControlName=name]')!, 'Pat Lee');
      type(el.querySelector('input[formControlName=company]')!, 'Robert Half');
      type(el.querySelector('select[formControlName=recruiterType]')!, 'AGENCY');
      el.querySelector<HTMLFormElement>('form')!.dispatchEvent(new Event('submit'));

      const req = http.expectOne((r) => r.method === 'POST' && r.url === '/api/people');
      expect(req.request.body).toEqual({
        name: 'Pat Lee',
        companyName: 'Robert Half',
        title: null,
        role: 'RECRUITER',
        recruiterType: 'AGENCY',
        email: null,
        phone: null,
        linkedinUrl: null,
        notes: null,
      });
      req.flush(aPerson());
      // The company input's type-ahead may have asked for suggestions meanwhile.
      await wait(250);
      http.match((r) => r.url === '/api/companies').forEach((r) => r.flush([]));

      expect(saved).toHaveBeenCalledWith(aPerson());
    });

    it('only asks for a recruiter type for recruiters', async () => {
      const fixture = TestBed.createComponent(PersonForm);
      await fixture.whenStable();
      const el: HTMLElement = fixture.nativeElement;
      expect(el.querySelector('select[formControlName=recruiterType]')).not.toBeNull();

      type(el.querySelector('select[formControlName=role]')!, 'HIRING_MANAGER');
      await fixture.whenStable();

      expect(el.querySelector('select[formControlName=recruiterType]')).toBeNull();
    });
  });

  it('PeopleList shows each person with their role and where they work', async () => {
    const fixture = TestBed.createComponent(PeopleList);
    fixture.detectChanges();
    http
      .expectOne('/api/people')
      .flush([
        aPerson(),
        aPerson({ id: 'p2', name: 'Sam', role: 'OTHER', title: null, company: null }),
      ]);
    await fixture.whenStable();
    const el: HTMLElement = fixture.nativeElement;

    const rows = Array.from(el.querySelectorAll('.people-table tbody tr')).map((tr) => [
      tr.querySelector('.name')!.textContent!.trim(),
      tr.querySelector('.role-pill')!.textContent!.trim(),
      tr.querySelector('.where')!.textContent!.trim(),
    ]);
    expect(rows).toEqual([
      ['Pat Lee', 'Recruiter', 'Senior Recruiter, Robert Half'],
      ['Sam', 'Other', ''],
    ]);
  });

  it('PersonDetail lists their applications and deletes after confirming', async () => {
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    const fixture = TestBed.createComponent(PersonDetail);
    fixture.componentRef.setInput('id', 'p1');
    fixture.detectChanges();
    http.expectOne('/api/people/p1').flush(aPerson());
    http.expectOne('/api/people/p1/applications').flush([
      {
        applicationId: 'a1',
        company: null,
        jobRole: 'Java Contractor',
        status: 'APPLIED',
        role: 'RECRUITER',
        addedAt: '2026-10-24T12:00:00Z',
      },
    ]);
    await fixture.whenStable();
    const el: HTMLElement = fixture.nativeElement;

    expect(el.querySelector('.applications a')?.textContent).toContain(
      'Undisclosed client – Java Contractor',
    );

    Array.from(el.querySelectorAll('button'))
      .find((b) => b.textContent?.trim() === 'Delete')!
      .click();
    http.expectOne((r) => r.method === 'DELETE' && r.url === '/api/people/p1').flush(null);

    expect(navigate).toHaveBeenCalledWith(['/people']);
  });

  describe('ApplicationPeople', () => {
    async function open(people: ApplicationPerson[]) {
      const fixture = TestBed.createComponent(ApplicationPeople);
      fixture.componentRef.setInput('applicationId', 'a1');
      const changed = vi.fn();
      fixture.componentInstance.changed.subscribe(changed);
      fixture.detectChanges();
      http.expectOne('/api/applications/a1/people').flush(people);
      await fixture.whenStable();
      return { fixture, changed, el: fixture.nativeElement as HTMLElement };
    }

    it('adds someone picked from the suggestions with the chosen role', async () => {
      const { fixture, changed, el } = await open([]);
      const sam = aPerson({ id: 'p2', name: 'Sam Ortiz', company: 'Acme', role: 'OTHER' });

      type(el.querySelector('input[aria-label="Person to add"]')!, 'sam');
      await wait(250);
      http.expectOne((r) => r.url === '/api/people' && r.params.get('q') === 'sam').flush([sam]);
      await fixture.whenStable();
      expect(
        Array.from(el.querySelectorAll('datalist option')).map((o) => o.getAttribute('value')),
      ).toEqual(['Sam Ortiz (Acme)']);

      type(el.querySelector('input[aria-label="Person to add"]')!, 'Sam Ortiz (Acme)');
      type(el.querySelector('select[aria-label="Their role on this application"]')!, 'REFERRAL');
      await wait(250);
      // Picking "Sam Ortiz (Acme)" searches by the name, so Sam stays pickable.
      http
        .expectOne((r) => r.url === '/api/people' && r.params.get('q') === 'Sam Ortiz')
        .flush([sam]);
      await fixture.whenStable();
      Array.from(el.querySelectorAll('button'))
        .find((b) => b.textContent?.trim() === 'Add')!
        .click();

      const req = http.expectOne('/api/applications/a1/people/p2');
      expect(req.request.method).toBe('PUT');
      expect(req.request.body).toEqual({ role: 'REFERRAL' });
      req.flush(onApp(sam, 'REFERRAL'));
      await fixture.whenStable();

      expect(el.querySelector('li .name')?.textContent).toContain('Sam Ortiz');
      expect(changed).toHaveBeenCalled();
    });

    it('changes a role and removes someone', async () => {
      const { fixture, el } = await open([onApp(aPerson())]);

      type(el.querySelector('select[aria-label="Role of Pat Lee"]')!, 'INTERVIEWER');
      const relink = http.expectOne('/api/applications/a1/people/p1');
      expect(relink.request.body).toEqual({ role: 'INTERVIEWER' });
      relink.flush(onApp(aPerson(), 'INTERVIEWER'));
      await fixture.whenStable();

      el.querySelector<HTMLButtonElement>('button.remove')!.click();
      http
        .expectOne((r) => r.method === 'DELETE' && r.url === '/api/applications/a1/people/p1')
        .flush(null);
      await fixture.whenStable();

      expect(el.querySelectorAll('li').length).toBe(0);
    });
  });
});
