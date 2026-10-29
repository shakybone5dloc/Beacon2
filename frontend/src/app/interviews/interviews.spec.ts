import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { ApplicationInterviews } from './application-interviews';
import { durationLabel, formatInZone, Interview, toInputValue } from './interview.model';

function anInterview(overrides: Partial<Interview> = {}): Interview {
  return {
    id: 'i1',
    applicationId: 'a1',
    startsAt: '2099-03-10T19:00:00Z',
    endsAt: '2099-03-10T19:45:00Z',
    localStart: '2099-03-10T14:00:00',
    timeZone: 'America/Chicago',
    durationMinutes: 45,
    type: 'TECHNICAL',
    location: 'https://meet.example/abc',
    interviewers: [{ id: 'p1', name: 'Sam Ortiz', company: 'Acme' }],
    prepNotes: 'Review the payments design.',
    debriefNotes: null,
    outcome: null,
    version: 0,
    createdAt: '2026-10-28T12:00:00Z',
    updatedAt: '2026-10-28T12:00:00Z',
    ...overrides,
  };
}

function set(
  input: HTMLInputElement | HTMLSelectElement | HTMLTextAreaElement,
  value: string,
): void {
  input.value = value;
  input.dispatchEvent(new Event(input instanceof HTMLSelectElement ? 'change' : 'input'));
}

/** Buttons with an icon also carry the icon's name in their text, so match the end. */
function button(el: HTMLElement, label: string): HTMLButtonElement {
  return Array.from(el.querySelectorAll('button')).find((b) =>
    b.textContent?.trim().endsWith(label),
  )!;
}

describe('interviews', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    vi.restoreAllMocks();
  });

  describe('interview.model', () => {
    it('shows the start in the zone it was agreed in', () => {
      expect(formatInZone('2099-03-10T19:00:00Z', 'America/Chicago')).toContain('2:00');
      expect(formatInZone('2099-03-10T19:00:00Z', 'Europe/London')).toContain('7:00');
    });

    it('labels lengths', () => {
      expect(durationLabel(45)).toBe('45 min');
      expect(durationLabel(60)).toBe('1 h');
      expect(durationLabel(90)).toBe('1 h 30 min');
    });

    it('trims the seconds off a local start for the input', () => {
      expect(toInputValue('2099-03-10T14:00:00')).toBe('2099-03-10T14:00');
    });
  });

  describe('ApplicationInterviews', () => {
    async function open(interviews: Interview[]) {
      const fixture = TestBed.createComponent(ApplicationInterviews);
      fixture.componentRef.setInput('applicationId', 'a1');
      const changed = vi.fn();
      fixture.componentInstance.changed.subscribe(changed);
      fixture.detectChanges();
      http.expectOne('/api/applications/a1/interviews').flush(interviews);
      await fixture.whenStable();
      return { fixture, changed, el: fixture.nativeElement as HTMLElement };
    }

    it('lists an interview with its time, link, interviewers and calendar file', async () => {
      const { el } = await open([anInterview()]);

      expect(el.querySelector('.type')?.textContent).toContain('Technical');
      expect(el.querySelector('.pill-blue')?.textContent).toContain('Next');
      expect(el.querySelector('.when time')?.getAttribute('datetime')).toBe('2099-03-10T19:00:00Z');
      expect(el.querySelector('.when')?.textContent).toContain('45 min');
      expect(el.querySelector('.where a')?.getAttribute('href')).toBe('https://meet.example/abc');
      expect(el.querySelector('.with a')?.getAttribute('href')).toBe('/people/p1');
      expect(el.querySelector('a.calendar')?.getAttribute('href')).toBe(
        '/api/interviews/i1/calendar.ics',
      );
    });

    it('marks a cancelled interview and does not call it next', async () => {
      const { el } = await open([anInterview({ outcome: 'CANCELLED' })]);

      expect(el.querySelector('.outcome')?.textContent).toContain('Cancelled');
      expect(el.querySelector('.pill-blue')).toBeNull();
    });

    it('schedules an interview with picked interviewers, then reloads', async () => {
      const { fixture, changed, el } = await open([]);
      expect(el.querySelector('.empty')?.textContent).toContain('None scheduled');

      button(el, 'Schedule interview').click();
      http.expectOne('/api/people').flush([
        { id: 'p1', name: 'Sam Ortiz', company: 'Acme' },
        { id: 'p2', name: 'Pat Lee', company: null },
      ]);
      await fixture.whenStable();

      const form = el.querySelector<HTMLFormElement>('form')!;
      set(form.querySelector('select[formControlName=type]')!, 'PANEL');
      set(form.querySelector('input[formControlName=localStart]')!, '2099-03-10T14:00');
      set(form.querySelector('select[formControlName=timeZone]')!, 'America/Chicago');
      set(form.querySelector('input[formControlName=location]')!, ' https://meet.example/abc ');
      form.querySelectorAll<HTMLInputElement>('.interviewers input[type=checkbox]')[0].click();
      form.dispatchEvent(new Event('submit'));

      const req = http.expectOne(
        (r) => r.method === 'POST' && r.url === '/api/applications/a1/interviews',
      );
      expect(req.request.body).toEqual({
        type: 'PANEL',
        localStart: '2099-03-10T14:00:00',
        timeZone: 'America/Chicago',
        durationMinutes: 60,
        location: 'https://meet.example/abc',
        interviewerIds: ['p1'],
        prepNotes: null,
        debriefNotes: null,
        outcome: null,
      });
      req.flush(anInterview({ type: 'PANEL' }));
      http.expectOne('/api/applications/a1/interviews').flush([anInterview({ type: 'PANEL' })]);
      await fixture.whenStable();

      expect(changed).toHaveBeenCalled();
      expect(el.querySelector('form')).toBeNull();
      expect(el.querySelector('.type')?.textContent).toContain('Panel');
    });

    it('asks for the date before saving', async () => {
      const { fixture, el } = await open([]);
      button(el, 'Schedule interview').click();
      http.expectOne('/api/people').flush([]);
      await fixture.whenStable();

      el.querySelector('form')!.dispatchEvent(new Event('submit'));
      await fixture.whenStable();

      expect(el.textContent).toContain('Pick the date and time.');
    });

    it('records the outcome with the version it was loaded at', async () => {
      const { fixture, el } = await open([anInterview({ version: 2 })]);

      button(el, 'Edit').click();
      http.expectOne('/api/people').flush([{ id: 'p1', name: 'Sam Ortiz', company: 'Acme' }]);
      await fixture.whenStable();
      const form = el.querySelector<HTMLFormElement>('form')!;
      expect(form.querySelector<HTMLInputElement>('input[formControlName=localStart]')!.value).toBe(
        '2099-03-10T14:00',
      );
      expect(
        form.querySelector<HTMLInputElement>('.interviewers input[type=checkbox]')!.checked,
      ).toBe(true);
      set(form.querySelector('select[formControlName=outcome]')!, 'PASSED');
      form.dispatchEvent(new Event('submit'));

      const req = http.expectOne((r) => r.method === 'PUT' && r.url === '/api/interviews/i1');
      expect(req.request.body).toMatchObject({
        outcome: 'PASSED',
        interviewerIds: ['p1'],
        durationMinutes: 45,
        version: 2,
      });
      req.flush(anInterview({ outcome: 'PASSED', version: 3 }));
      http.expectOne('/api/applications/a1/interviews').flush([anInterview({ outcome: 'PASSED' })]);
    });

    it('deletes after confirming', async () => {
      vi.spyOn(window, 'confirm').mockReturnValue(true);
      const { fixture, changed, el } = await open([anInterview()]);

      button(el, 'Delete').click();
      http.expectOne((r) => r.method === 'DELETE' && r.url === '/api/interviews/i1').flush(null);
      await fixture.whenStable();

      expect(el.querySelector('.interviews')).toBeNull();
      expect(changed).toHaveBeenCalled();
    });
  });
});
