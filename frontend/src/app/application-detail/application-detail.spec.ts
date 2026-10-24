import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { ApplicationDetail } from './application-detail';
import { anApplication } from '../applications/testing';
import { ApplicationResponse, StatusChange } from '../applications/application.model';

describe('ApplicationDetail', () => {
  let http: HttpTestingController;
  let navigate: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [ApplicationDetail],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    http = TestBed.inject(HttpTestingController);
    navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
  });

  afterEach(() => {
    http.verify();
    vi.restoreAllMocks();
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
