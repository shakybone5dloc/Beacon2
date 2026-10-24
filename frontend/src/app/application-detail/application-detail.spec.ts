import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { ApplicationDetail } from './application-detail';
import { anApplication } from '../applications/testing';
import { ApplicationResponse } from '../applications/application.model';

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

  async function open(app: ApplicationResponse) {
    const fixture = TestBed.createComponent(ApplicationDetail);
    fixture.componentRef.setInput('id', app.id);
    await fixture.whenStable();

    http.expectOne(`/api/applications/${app.id}`).flush(app);
    await fixture.whenStable();
    return { fixture, el: fixture.nativeElement as HTMLElement };
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

  it('shows a notice when the application does not exist', async () => {
    const fixture = TestBed.createComponent(ApplicationDetail);
    fixture.componentRef.setInput('id', 'gone');
    await fixture.whenStable();

    http.expectOne('/api/applications/gone').flush(null, { status: 404, statusText: 'Not Found' });
    await fixture.whenStable();

    expect(fixture.nativeElement.textContent).toContain('no longer exists');
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
