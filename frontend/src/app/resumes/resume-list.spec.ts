import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ResumeList } from './resume-list';
import { Resume, splitList } from './resume.model';

function aResume(overrides: Partial<Resume> = {}): Resume {
  return {
    documentId: 'r1',
    label: 'Backend Java',
    isDefault: true,
    filename: 'backend.pdf',
    contentType: 'application/pdf',
    sizeBytes: 1000,
    textStatus: 'READ',
    targetTitles: ['Senior Java Engineer'],
    skills: ['Java', 'Spring Boot'],
    yearsExperience: 7,
    version: 0,
    createdAt: '2026-10-23T12:00:00Z',
    updatedAt: '2026-10-23T12:00:00Z',
    ...overrides,
  };
}

function set(input: HTMLInputElement, value: string): void {
  input.value = value;
  input.dispatchEvent(new Event('input'));
}

function button(el: Element, label: string): HTMLButtonElement {
  return Array.from(el.querySelectorAll('button')).find((b) => b.textContent?.trim() === label)!;
}

describe('ResumeList', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  async function open(resumes: Resume[]) {
    const fixture = TestBed.createComponent(ResumeList);
    fixture.detectChanges();
    http.expectOne('/api/resumes').flush(resumes);
    await fixture.whenStable();
    return { fixture, el: fixture.nativeElement as HTMLElement };
  }

  function items(el: HTMLElement): HTMLElement[] {
    return Array.from(el.querySelectorAll<HTMLElement>('.resumes > li'));
  }

  it('shows each resume with its label, default and summary', async () => {
    const { el } = await open([
      aResume(),
      aResume({
        documentId: 'r2',
        label: 'Test',
        isDefault: false,
        targetTitles: [],
        skills: [],
        yearsExperience: null,
      }),
    ]);

    const [first, second] = items(el);
    expect(first.querySelector('.label')?.textContent).toBe('Backend Java');
    expect(first.querySelector('.pill-blue')?.textContent).toContain('Default');
    expect(first.querySelector('.summary')?.textContent).toBe(
      'Senior Java Engineer · Java, Spring Boot · 7 years',
    );
    expect(button(first, 'Make default')).toBeUndefined();
    expect(button(second, 'Make default')).toBeTruthy();
    expect(second.querySelector('.summary')).toBeNull();
  });

  it('makes another resume the default, then reloads', async () => {
    const { fixture, el } = await open([
      aResume(),
      aResume({ documentId: 'r2', isDefault: false }),
    ]);

    button(items(el)[1], 'Make default').click();
    http
      .expectOne((r) => r.method === 'PUT' && r.url === '/api/resumes/r2/default')
      .flush(aResume({ documentId: 'r2' }));
    http
      .expectOne('/api/resumes')
      .flush([aResume({ documentId: 'r2' }), aResume({ isDefault: false })]);
    await fixture.whenStable();

    expect(items(el)[0].querySelector('.pill-blue')).not.toBeNull();
  });

  it('saves the label and summary with the version it loaded', async () => {
    const { fixture, el } = await open([aResume({ version: 4 })]);

    button(items(el)[0], 'Edit').click();
    await fixture.whenStable();
    const form = el.querySelector('form')!;
    set(form.querySelector('input[formControlName=label]')!, ' Platform ');
    set(form.querySelector('input[formControlName=skills]')!, 'Java, Kafka,, Postgres ');
    set(form.querySelector('input[formControlName=yearsExperience]')!, '8');
    form.dispatchEvent(new Event('submit'));

    const req = http.expectOne((r) => r.method === 'PUT' && r.url === '/api/resumes/r1');
    expect(req.request.body).toEqual({
      label: 'Platform',
      targetTitles: ['Senior Java Engineer'],
      skills: ['Java', 'Kafka', 'Postgres'],
      yearsExperience: 8,
      version: 4,
    });
    req.flush(aResume({ label: 'Platform', version: 5 }));
    await fixture.whenStable();

    expect(el.querySelector('form')).toBeNull();
    expect(items(el)[0].querySelector('.label')?.textContent).toBe('Platform');
  });

  it('turns down years that are not a whole number', async () => {
    const { fixture, el } = await open([aResume()]);

    button(items(el)[0], 'Edit').click();
    await fixture.whenStable();
    set(el.querySelector('input[formControlName=yearsExperience]')!, 'lots');
    el.querySelector('form')!.dispatchEvent(new Event('submit'));
    await fixture.whenStable();

    expect(el.textContent).toContain('whole number from 0 to 60');
  });

  it('shows the text Beacon read, or warns when it could not read any', async () => {
    const { fixture, el } = await open([
      aResume(),
      aResume({ documentId: 'r2', textStatus: 'UNREADABLE', isDefault: false }),
    ]);

    expect(items(el)[1].querySelector('.unreadable')?.textContent).toContain("can't read text");
    expect(button(items(el)[1], 'View text')).toBeUndefined();

    button(items(el)[0], 'View text').click();
    http.expectOne('/api/resumes/r1/text').flush({ text: 'Aaron Keiper\nJava' });
    await fixture.whenStable();

    expect(items(el)[0].querySelector('.text')?.textContent).toBe('Aaron Keiper\nJava');
  });

  it('splits comma lists, dropping blanks', () => {
    expect(splitList(' Java, ,Spring Boot ,')).toEqual(['Java', 'Spring Boot']);
  });
});
