import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { ApplicationDetail } from './application-detail';
import { ApplicationPeople } from '../people/application-people';
import { anApplication } from '../applications/testing';
import { ApplicationResponse, StatusChange } from '../applications/application.model';
import { ApplicationDocuments } from '../documents/application-documents';
import { CoverLetterDrafter } from '../documents/cover-letter-drafter';
import { Component, input, output } from '@angular/core';
import { By } from '@angular/platform-browser';
import { DocumentResponse } from '../documents/document.model';
import { aDocument } from '../documents/testing';
import { PostingPanel } from '../posting-view/posting-panel';
import { ContactLog } from '../contacts/contact-log';
import { PossibleDuplicates } from '../contacts/possible-duplicates';
import { ApplicationInterviews } from '../interviews/application-interviews';

/** Stand-ins for the child panels, which have their own specs and HTTP calls. */
@Component({
  selector: 'app-application-documents',
  template: '<div class="documents-stub"></div>',
})
class DocumentsStub {
  readonly applicationId = input<string | null>(null);
  readonly jobUrl = input<string | null>(null);
  readonly captured = output<boolean>();
  readonly changed = output<DocumentResponse[]>();
  reload(): void {}
}

@Component({ selector: 'app-application-people', template: '' })
class PeopleStub {
  readonly applicationId = input<string>();
  readonly changed = output<void>();
  reload(): void {}
}

@Component({ selector: 'app-contact-log', template: '' })
class ContactLogStub {
  readonly applicationId = input<string>();
  readonly logged = output<void>();
}

@Component({ selector: 'app-application-interviews', template: '' })
class InterviewsStub {
  readonly applicationId = input<string>();
  readonly changed = output<void>();
}

@Component({ selector: 'app-possible-duplicates', template: '' })
class DuplicatesStub {
  readonly applicationId = input<string>();
}

@Component({ selector: 'app-cover-letter-drafter', template: '' })
class DrafterStub {
  readonly applicationId = input<string>();
  readonly hasJobDescription = input(false);
  readonly saved = output<unknown>();
}

describe('ApplicationDetail', () => {
  let http: HttpTestingController;
  let navigate: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [ApplicationDetail],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    TestBed.overrideComponent(ApplicationDetail, {
      remove: {
        imports: [
          ApplicationDocuments,
          CoverLetterDrafter,
          ApplicationPeople,
          ContactLog,
          PossibleDuplicates,
          ApplicationInterviews,
        ],
      },
      add: {
        imports: [
          DocumentsStub,
          DrafterStub,
          PeopleStub,
          ContactLogStub,
          DuplicatesStub,
          InterviewsStub,
        ],
      },
    });
    http = TestBed.inject(HttpTestingController);
    navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
  });

  afterEach(() => {
    http.verify();
    vi.restoreAllMocks();
    localStorage.clear();
  });

  const created: StatusChange = {
    fromStatus: null,
    toStatus: 'SAVED',
    changedAt: '2026-10-01T12:00:00Z',
  };

  async function open(app: ApplicationResponse, history: StatusChange[] = [created]) {
    const fixture = TestBed.createComponent(ApplicationDetail);
    fixture.componentRef.setInput('id', app.id);
    await fixture.whenStable();

    flushLoad(app, history);
    await fixture.whenStable();
    return { fixture, el: fixture.nativeElement as HTMLElement };
  }

  function flushLoad(app: ApplicationResponse, history: StatusChange[]) {
    http.expectOne(`/api/applications/${app.id}`).flush(app);
    http.expectOne(`/api/applications/${app.id}/history`).flush(history);
  }

  function buttonLabelled(el: HTMLElement, label: string): HTMLButtonElement {
    return Array.from(el.querySelectorAll('button')).find((b) => b.textContent?.trim() === label)!;
  }

  function deleteButton(el: HTMLElement): HTMLButtonElement {
    return el.querySelector<HTMLButtonElement>('button.delete')!;
  }

  it('shows the recruiter agency of an undisclosed employer, refreshed when people change', async () => {
    const app = anApplication({ id: 'a1', company: null, companyId: null, employmentType: 'C2C' });
    const { fixture, el } = await open(app);
    expect(el.querySelector('h1')?.textContent).toContain('Undisclosed client');
    expect(el.textContent).toContain('Contract (C2C)');

    fixture.debugElement.query(By.directive(PeopleStub)).componentInstance.changed.emit();
    http.expectOne('/api/applications/a1').flush({ ...app, viaAgency: 'Robert Half' });
    await fixture.whenStable();

    expect(el.querySelector('h1')?.textContent).toContain('Undisclosed client · via Robert Half');
  });

  it('shows the application details with an edit link', async () => {
    const { el } = await open(
      anApplication({
        id: 'a1',
        company: 'Globex',
        role: 'Staff Engineer',
        status: 'INTERVIEWING',
        jobUrl: 'https://globex.com/jobs/1',
        notes: 'Referred by Sam',
      }),
    );

    expect(el.querySelector('h1')?.textContent).toContain('Globex');
    expect(el.querySelector('.role')?.textContent).toContain('Staff Engineer');
    expect(el.querySelector('.status')?.textContent).toContain('Interviewing');
    expect(el.querySelector('a[target=_blank]')?.getAttribute('href')).toBe(
      'https://globex.com/jobs/1',
    );
    expect(el.textContent).toContain('Referred by Sam');
    expect(el.querySelector('a.edit')?.getAttribute('href')).toBe('/applications/a1/edit');
  });

  it('shows the apply-by date with its countdown', async () => {
    const { el } = await open(anApplication({ id: 'a1', status: 'SAVED', applyBy: '2099-12-31' }));

    expect(el.textContent).toContain('Apply by');
    expect(el.textContent).toContain('Dec 31, 2099');
  });

  it('saves a follow-up date', async () => {
    const app = anApplication({ id: 'a1', status: 'APPLIED', version: 2 });
    const { fixture, el } = await open(app);

    const input = el.querySelector<HTMLInputElement>('#follow-up')!;
    input.value = '2099-11-07';
    buttonLabelled(el, 'Save').click();

    const req = http.expectOne('/api/applications/a1/follow-up');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ followUpOn: '2099-11-07', version: 2 });
    req.flush({ ...app, followUpOn: '2099-11-07', version: 3 });
    await fixture.whenStable();

    expect(el.querySelector('.follow-up .chip')?.textContent).toContain('Follow up in');
    expect(buttonLabelled(el, 'Clear')).toBeTruthy();
  });

  it('clears the follow-up date', async () => {
    const app = anApplication({ id: 'a1', status: 'APPLIED', followUpOn: '2099-11-07' });
    const { fixture, el } = await open(app);

    buttonLabelled(el, 'Clear').click();

    const req = http.expectOne('/api/applications/a1/follow-up');
    expect(req.request.body).toEqual({ followUpOn: null, version: 0 });
    req.flush({ ...app, followUpOn: null, version: 1 });
    await fixture.whenStable();

    expect(buttonLabelled(el, 'Clear')).toBeUndefined();
    expect(el.querySelector('.follow-up .chip')).toBeNull();
  });

  it('shows the posting details, applied date and job description', async () => {
    const { el } = await open(
      anApplication({
        id: 'a1',
        status: 'APPLIED',
        location: 'Denver, CO',
        remote: true,
        salaryMin: 140000,
        salaryMax: 170000,
        salaryCurrency: 'USD',
        salaryPeriod: 'year',
        source: 'Himalayas',
        postedOn: '2026-10-20',
        appliedAt: '2026-10-22T15:00:00Z',
        jobDescription: 'Build services with Spring Boot.',
      }),
    );

    const facts = new Map(
      Array.from(el.querySelectorAll('dt')).map((dt) => [
        dt.textContent!.trim(),
        dt.nextElementSibling!.textContent!.replace(/\s+/g, ' ').trim(),
      ]),
    );
    expect(facts.get('Salary')).toBe('$140k–$170k/yr');
    expect(facts.get('Location')).toBe('Denver, CO Remote');
    expect(facts.get('Found on')).toBe('Himalayas');
    expect(facts.get('Posted')).toBe('Oct 20, 2026');
    expect(facts.get('Applied')).toMatch(/^Oct 22, 2026 · \d+ days? ago$/);
    expect(el.querySelector('.description-text')?.textContent).toBe(
      'Build services with Spring Boot.',
    );
  });

  it('points to capturing the posting when there is no description', async () => {
    const { el } = await open(anApplication({ id: 'a1', jobUrl: 'https://example.com/job' }));

    expect(el.textContent).toContain('Save posting as PDF');
    expect(el.querySelector('.description-text')).toBeNull();
  });

  it('reloads after a posting capture so the new description shows', async () => {
    const app = anApplication({ id: 'a1', jobUrl: 'https://example.com/job' });
    const { fixture, el } = await open(app);

    fixture.debugElement.query(By.directive(DocumentsStub)).componentInstance.captured.emit(true);
    flushLoad({ ...app, version: 1, jobDescription: 'Captured text' }, [created]);
    await fixture.whenStable();

    expect(el.querySelector('.notice')?.textContent).toContain(
      'its text is now the job description',
    );
    expect(el.querySelector('.description-text')?.textContent).toBe('Captured text');
  });

  describe('posting panel', () => {
    function toggle(el: HTMLElement): HTMLButtonElement {
      return el.querySelector<HTMLButtonElement>('button.panel-toggle')!;
    }

    it('opens beside the details and takes over showing the description', async () => {
      const { fixture, el } = await open(
        anApplication({ id: 'a1', jobDescription: 'Build services.' }),
      );
      expect(el.querySelector('aside app-posting-panel')).toBeNull();
      expect(el.querySelector('.description-text')).not.toBeNull();

      toggle(el).click();
      await fixture.whenStable();

      expect(toggle(el).textContent?.trim()).toBe('Hide posting');
      expect(toggle(el).getAttribute('aria-expanded')).toBe('true');
      expect(el.querySelector('aside app-posting-panel')).not.toBeNull();
      expect(el.querySelector('main .description-text, .main .description-text')).toBeNull();
    });

    it('remembers whether it was open', async () => {
      const { fixture, el } = await open(anApplication({ id: 'a1' }));

      toggle(el).click();
      await fixture.whenStable();

      expect(localStorage.getItem('beacon.detail.postingPanel')).toBe('open');
    });

    it('gets the newest saved posting PDF from the documents list', async () => {
      localStorage.setItem('beacon.detail.postingPanel', 'open');
      const { fixture } = await open(anApplication({ id: 'a1' }));

      fixture.debugElement
        .query(By.directive(DocumentsStub))
        .componentInstance.changed.emit([
          aDocument({ id: 'r1', kind: 'RESUME' }),
          aDocument({ id: 'p2', kind: 'POSTING' }),
          aDocument({ id: 'p1', kind: 'POSTING' }),
        ]);
      await fixture.whenStable();

      const panel = fixture.debugElement.query(By.directive(PostingPanel))
        .componentInstance as PostingPanel;
      expect(panel.postingPdfUrl()).toBe('/api/applications/a1/documents/p2/content');
    });
  });

  it('shows a notice when the application does not exist', async () => {
    const fixture = TestBed.createComponent(ApplicationDetail);
    fixture.componentRef.setInput('id', 'gone');
    await fixture.whenStable();

    http.expectOne('/api/applications/gone').flush(null, { status: 404, statusText: 'Not Found' });
    http.expectOne('/api/applications/gone/history'); // cancelled once the first request fails
    await fixture.whenStable();

    expect(fixture.nativeElement.textContent).toContain('no longer exists');
  });

  it('shows the status history oldest first with the gap between steps', async () => {
    const { el } = await open(anApplication({ id: 'a1', status: 'APPLIED' }), [
      created,
      { fromStatus: 'SAVED', toStatus: 'APPLIED', changedAt: '2026-10-04T12:00:00Z' },
    ]);

    const entries = Array.from(el.querySelectorAll('.timeline li')).map((li) =>
      li.textContent!.replace(/\s+/g, ' ').trim(),
    );
    expect(entries.length).toBe(2);
    expect(entries[0]).toContain('Saved');
    expect(entries[0]).not.toContain('later');
    expect(entries[1]).toContain('Applied');
    expect(entries[1]).toContain('3 days later');
  });

  it('shows how long the application has been in its current status', async () => {
    const twoDaysAgo = new Date(Date.now() - 2 * 24 * 60 * 60 * 1000 - 60_000).toISOString();
    const { el } = await open(
      anApplication({ id: 'a1', status: 'APPLIED', statusChangedAt: twoDaysAgo }),
    );

    expect(el.querySelector('.status')?.textContent).toContain('for 2 days');
  });

  it('moves the application and refreshes the history', async () => {
    const app = anApplication({
      id: 'a1',
      status: 'INTERVIEWING',
      version: 3,
      allowedTransitions: ['OFFER', 'REJECTED', 'WITHDRAWN'],
    });
    const { fixture, el } = await open(app);

    buttonLabelled(el, 'Offer').click();

    const req = http.expectOne('/api/applications/a1/status');
    expect(req.request.body).toEqual({ status: 'OFFER', version: 3 });
    req.flush({
      ...app,
      status: 'OFFER',
      version: 4,
      allowedTransitions: ['ACCEPTED', 'DECLINED', 'REJECTED'],
    });
    http
      .expectOne('/api/applications/a1/history')
      .flush([
        created,
        { fromStatus: 'INTERVIEWING', toStatus: 'OFFER', changedAt: '2026-10-05T12:00:00Z' },
      ]);
    await fixture.whenStable();

    expect(el.querySelector('.status')?.textContent).toContain('Offer');
    expect(buttonLabelled(el, 'Accepted')).toBeTruthy();
    expect(el.querySelectorAll('.timeline li').length).toBe(2);
  });

  it('reloads with a notice when the move conflicts', async () => {
    const app = anApplication({ id: 'a1', status: 'SAVED', allowedTransitions: ['APPLIED'] });
    const { fixture, el } = await open(app);

    buttonLabelled(el, 'Applied').click();
    http
      .expectOne('/api/applications/a1/status')
      .flush({ title: 'Conflict' }, { status: 409, statusText: 'Conflict' });
    flushLoad({ ...app, status: 'WITHDRAWN', version: 1, allowedTransitions: [] }, [created]);
    await fixture.whenStable();

    expect(el.querySelector('.notice')?.textContent).toContain('changed somewhere else');
    expect(el.querySelector('.status')?.textContent).toContain('Withdrawn');
    expect(el.querySelector('.moves')).toBeNull();
  });

  it('deletes after confirmation and returns to the board', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    const { fixture, el } = await open(anApplication({ id: 'a1' }));

    deleteButton(el).click();

    const req = http.expectOne('/api/applications/a1');
    expect(req.request.method).toBe('DELETE');
    req.flush(null, { status: 204, statusText: 'No Content' });
    await fixture.whenStable();

    expect(navigate).toHaveBeenCalledWith(['/']);
  });

  it('does nothing when the delete is not confirmed', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(false);
    const { el } = await open(anApplication({ id: 'a1' }));

    deleteButton(el).click();

    http.expectNone('/api/applications/a1');
    expect(navigate).not.toHaveBeenCalled();
  });

  it('stays on the page with a notice when the delete fails', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    const { fixture, el } = await open(anApplication({ id: 'a1' }));

    deleteButton(el).click();
    http.expectOne('/api/applications/a1').flush(null, { status: 500, statusText: 'Server Error' });
    await fixture.whenStable();

    expect(el.querySelector('.notice')?.textContent).toContain('Could not delete');
    expect(navigate).not.toHaveBeenCalled();
  });
});
