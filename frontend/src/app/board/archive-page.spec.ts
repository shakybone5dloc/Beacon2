import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { anApplication } from '../applications/testing';
import { ArchivePage } from './archive-page';

const daysAgo = (days: number) => new Date(Date.now() - days * 24 * 60 * 60 * 1000).toISOString();

describe('ArchivePage', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lists only applications closed long enough ago, newest first, and searches them', async () => {
    const fixture = TestBed.createComponent(ArchivePage);
    fixture.detectChanges();
    http.expectOne('/api/applications').flush([
      anApplication({
        id: 'a',
        company: 'Acme',
        role: 'Java dev',
        status: 'REJECTED',
        statusChangedAt: daysAgo(60),
      }),
      anApplication({
        id: 'b',
        company: 'Globex',
        role: 'Backend',
        status: 'WITHDRAWN',
        statusChangedAt: daysAgo(20),
      }),
      anApplication({
        id: 'c',
        company: 'Initech',
        status: 'REJECTED',
        statusChangedAt: daysAgo(2),
      }),
      anApplication({
        id: 'd',
        company: 'Hooli',
        status: 'APPLIED',
        statusChangedAt: daysAgo(90),
      }),
    ]);
    await fixture.whenStable();
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    const rows = () =>
      Array.from(el.querySelectorAll('ul.archived li a')).map((a) => a.getAttribute('href'));

    expect(rows()).toEqual(['/applications/b', '/applications/a']);
    expect(el.textContent).toContain('Withdrawn');

    const search = el.querySelector<HTMLInputElement>('input.search')!;
    search.value = 'acme';
    search.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    expect(rows()).toEqual(['/applications/a']);
  });

  it('says when nothing is archived', async () => {
    const fixture = TestBed.createComponent(ArchivePage);
    fixture.detectChanges();
    http.expectOne('/api/applications').flush([anApplication({ status: 'APPLIED' })]);
    await fixture.whenStable();
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.empty')?.textContent).toContain(
      'Nothing archived',
    );
  });
});
