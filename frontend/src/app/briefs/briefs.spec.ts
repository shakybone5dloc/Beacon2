import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { anApplication } from '../applications/testing';
import { anInterview } from '../interviews/testing';
import { onIt } from '../people/testing';
import { aResume } from '../resumes/testing';
import { Brief, BriefSection, briefMarkdown, renderMarkdown } from './brief.model';
import { BriefPage } from './brief-page';

function aSection(overrides: Partial<BriefSection> = {}): BriefSection {
  return {
    kind: 'PITCH',
    interviewId: null,
    title: 'The role and your pitch',
    writable: true,
    content: null,
    generatedAt: null,
    model: null,
    edited: false,
    version: null,
    ...overrides,
  };
}

function aBrief(sections: BriefSection[], overrides: Partial<Brief> = {}): Brief {
  return {
    resumeDocumentId: 'r1',
    resumeLabel: 'Backend',
    configured: true,
    sections,
    ...overrides,
  };
}

describe('Markdown', () => {
  it('renders tables and turns checklist boxes into characters that survive sanitizing', () => {
    const html = renderMarkdown(
      '| They want | You have |\n|---|---|\n| Kafka | RabbitMQ |\n\n- [ ] Water\n- [x] Link',
    );

    expect(html).toContain('<th>They want</th>');
    expect(html).toContain('<td>RabbitMQ</td>');
    expect(html).toContain('☐ Water');
    expect(html).toContain('☑ Link');
    expect(html).not.toContain('<input');
  });

  it('opens source links in a new tab', () => {
    const html = renderMarkdown('GHX is in Colorado. [[1]](https://www.ghx.com/about)');

    expect(html).toContain(
      '<a target="_blank" rel="noopener" href="https://www.ghx.com/about">[1]</a>',
    );
  });

  it('builds the download from the glance and the written sections only', () => {
    const markdown = briefMarkdown(
      'Interview brief: GHX – Java Engineer',
      ['Role: Java Engineer at GHX', 'Pay: $120k–$150k/yr'],
      [
        { title: 'The role and your pitch', content: '**Why you:** Java.' },
        { title: 'Technical refresher', content: null },
      ],
    );

    expect(markdown).toBe(
      '# Interview brief: GHX – Java Engineer\n\n## At a glance\n\n- Role: Java Engineer at GHX\n' +
        '- Pay: $120k–$150k/yr\n\n## The role and your pitch\n\n**Why you:** Java.\n',
    );
  });
});

describe('BriefPage', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  const screen = anInterview({
    id: 'i1',
    type: 'PHONE_SCREEN',
    startsAt: '2026-10-01T15:00:00Z',
    timeZone: 'UTC',
    interviewers: [{ id: 'p1', name: 'Sam Ortiz', company: 'GHX' }],
    debriefNotes: 'Asked about Kafka.',
    outcome: 'PASSED',
  });
  const panel = anInterview({
    id: 'i2',
    type: 'PANEL',
    startsAt: '2099-03-10T19:00:00Z',
    interviewers: [],
    debriefNotes: null,
  });

  async function open(brief: Brief, interviews = [screen, panel]) {
    const fixture = TestBed.createComponent(BriefPage);
    fixture.componentRef.setInput('id', 'a1');
    fixture.detectChanges();
    http.expectOne('/api/applications/a1').flush(
      anApplication({
        id: 'a1',
        company: 'GHX',
        role: 'Senior Java Engineer',
        jobDescription: 'Java and Kafka.',
        remote: true,
        salaryMin: 120000,
        salaryMax: 150000,
        salaryCurrency: 'USD',
        salaryPeriod: 'year',
      }),
    );
    http.expectOne('/api/applications/a1/brief').flush(brief);
    http.expectOne('/api/applications/a1/interviews').flush(interviews);
    http.expectOne('/api/applications/a1/people').flush([onIt('p1', 'Sam Ortiz', 'GHX')]);
    http.expectOne('/api/resumes').flush([aResume({ documentId: 'r1', label: 'Backend' })]);
    await fixture.whenStable();
    return { fixture, el: fixture.nativeElement as HTMLElement };
  }

  function titles(el: HTMLElement): string[] {
    return Array.from(el.querySelectorAll('.section h2')).map((h) => h.textContent?.trim() ?? '');
  }

  it('shows the facts at a glance, with the next round marked', async () => {
    const { el } = await open(aBrief([]));

    const glance = Array.from(el.querySelectorAll('.glance li')).map((li) =>
      li.textContent?.trim(),
    );
    expect(glance[0]).toBe('Role: Senior Java Engineer at GHX');
    expect(glance).toContain('Pay: $120k–$150k/yr');
    expect(
      glance.some(
        (l) => l?.startsWith('Round 1: Phone screen,') && l.endsWith('with Sam Ortiz – Passed'),
      ),
    ).toBe(true);
    expect(el.querySelector('.glance li.next')?.textContent).toContain('Round 2: Panel');
    // Their role on this application, and their title; GHX is left out as it's the employer.
    expect(glance).toContain('Recruiter: Sam Ortiz, Senior Recruiter');
  });

  it('writes every empty section a few at a time, skipping a round without debrief notes', async () => {
    const { fixture, el } = await open(
      aBrief([
        aSection({ kind: 'PITCH', title: 'The role and your pitch' }),
        aSection({
          kind: 'CHECKLIST',
          title: 'Day-of checklist',
          writable: false,
          content: '- [ ] Water',
        }),
        aSection({ kind: 'GAP', title: 'Your honest gap', content: 'Already here.', version: 0 }),
        aSection({ kind: 'STORIES', title: 'Stories to tell' }),
        aSection({ kind: 'QUESTIONS', title: 'Likely questions' }),
        aSection({ kind: 'REFRESHER', title: 'Technical refresher' }),
        aSection({ kind: 'ROUND', title: 'Update after the round', interviewId: 'i1' }),
        aSection({ kind: 'ROUND', title: 'Update after the round', interviewId: 'i2' }),
      ]),
    );
    // A round's update is titled after its round.
    expect(titles(el).filter((t) => t.startsWith('After round 1: Phone screen,'))).toHaveLength(1);

    el.querySelector<HTMLButtonElement>('button.write-all')!.click();
    await fixture.whenStable();

    // Three at once; the rest wait their turn.
    const first = http.match((r) => r.url.includes('/brief/sections/'));
    expect(first.map((r) => r.request.url)).toEqual([
      '/api/applications/a1/brief/sections/PITCH/write',
      '/api/applications/a1/brief/sections/STORIES/write',
      '/api/applications/a1/brief/sections/QUESTIONS/write',
    ]);
    expect(el.querySelectorAll('.writing').length).toBe(3);
    first[0].flush(
      aSection({
        content: '### Why you\n\nJava.',
        generatedAt: '2026-11-04T12:00:00Z',
        version: 0,
      }),
    );
    first[1].flush({ title: 'Busy' } as object, { status: 502, statusText: 'Bad Gateway' });
    first[2].flush(
      aSection({ kind: 'QUESTIONS', title: 'Likely questions', content: 'Q', version: 0 }),
    );
    await fixture.whenStable();

    const rest = http.match((r) => r.url.includes('/brief/sections/'));
    expect(rest.map((r) => [r.request.url, r.request.params.get('interviewId')])).toEqual([
      ['/api/applications/a1/brief/sections/REFRESHER/write', null],
      ['/api/applications/a1/brief/sections/ROUND/write', 'i1'],
    ]);
    rest[0].flush(
      aSection({ kind: 'REFRESHER', title: 'Technical refresher', content: 'Done', version: 0 }),
    );
    rest[1].flush(aSection({ kind: 'ROUND', interviewId: 'i1', content: 'Done', version: 0 }));
    await fixture.whenStable();

    const pitch = el.querySelector('[data-kind=PITCH]')!;
    expect(pitch.querySelector('.markdown h3')?.textContent).toBe('Why you');
    expect(pitch.querySelector('.meta')?.textContent).toContain('Written');
    expect(el.querySelector('[data-kind=STORIES] .notice')?.textContent).toContain(
      'Could not write',
    );
    expect(el.querySelectorAll('[data-kind=ROUND]')[1].textContent).toContain(
      'Add your debrief notes to this interview',
    );
  });

  it('stops writing the brief, with one notice, when no section can be written', async () => {
    const { fixture, el } = await open(
      aBrief(
        ['PITCH', 'GAP', 'STORIES', 'QUESTIONS', 'REFRESHER'].map((kind) =>
          aSection({ kind: kind as BriefSection['kind'], title: kind }),
        ),
      ),
    );

    el.querySelector<HTMLButtonElement>('button.write-all')!.click();
    const first = http.match((r) => r.url.includes('/brief/sections/'));
    expect(first).toHaveLength(3);
    first.forEach((r) =>
      r.flush(
        { detail: 'OpenAI rejected your API key. Check it under Settings.' },
        { status: 422, statusText: 'Unprocessable Entity' },
      ),
    );
    await fixture.whenStable();

    http.expectNone((r) => r.url.includes('/brief/sections/'));
    const notices = Array.from(el.querySelectorAll('.notice')).map((n) => n.textContent?.trim());
    expect(notices).toEqual(['OpenAI rejected your API key. Check it under Settings.']);
    expect(el.querySelector('.writing')).toBeNull();
  });

  it('asks before rewriting a section with your edits, then replaces them', async () => {
    const confirm = vi
      .spyOn(window, 'confirm')
      .mockReturnValueOnce(false)
      .mockReturnValueOnce(true);
    const { fixture, el } = await open(
      aBrief([
        aSection({
          kind: 'GAP',
          title: 'Your honest gap',
          content: 'Mine',
          edited: true,
          version: 2,
        }),
      ]),
    );

    el.querySelector<HTMLButtonElement>('[data-kind=GAP] button.write')!.click();
    http.expectNone((r) => r.url.includes('/write'));
    el.querySelector<HTMLButtonElement>('[data-kind=GAP] button.write')!.click();

    const req = http.expectOne((r) => r.url === '/api/applications/a1/brief/sections/GAP/write');
    expect(req.request.params.get('replaceEdits')).toBe('true');
    expect(confirm).toHaveBeenCalledTimes(2);
    req.flush(aSection({ kind: 'GAP', title: 'Your honest gap', content: 'New', version: 3 }));
    await fixture.whenStable();
    expect(el.querySelector('[data-kind=GAP] .markdown')?.textContent?.trim()).toBe('New');
  });

  it('edits a section with the version it was loaded at', async () => {
    const { fixture, el } = await open(
      aBrief([aSection({ kind: 'COMPANY', title: 'About the company', writable: false })]),
    );
    expect(el.querySelector('[data-kind=COMPANY] .empty')?.textContent).toContain(
      'Use Edit to add it',
    );
    expect(el.querySelector('[data-kind=COMPANY] button.write')).toBeNull();

    el.querySelector<HTMLButtonElement>('[data-kind=COMPANY] button.edit')!.click();
    await fixture.whenStable();
    const draft = el.querySelector<HTMLTextAreaElement>('textarea.draft')!;
    draft.value = 'Hospital supply chain.';
    draft.dispatchEvent(new Event('input'));
    el.querySelector<HTMLButtonElement>('button.save')!.click();

    const req = http.expectOne(
      (r) => r.method === 'PUT' && r.url === '/api/applications/a1/brief/sections/COMPANY',
    );
    expect(req.request.body).toEqual({ content: 'Hospital supply chain.', version: null });
    req.flush(
      aSection({
        kind: 'COMPANY',
        title: 'About the company',
        writable: false,
        content: 'Hospital supply chain.',
        edited: true,
        version: 0,
      }),
    );
    await fixture.whenStable();

    expect(el.querySelector('textarea.draft')).toBeNull();
    expect(el.querySelector('[data-kind=COMPANY] .meta')?.textContent?.trim()).toBe(
      'Written by you',
    );
  });

  it('points to Settings without an AI key, and still lets you edit', async () => {
    const { el } = await open(aBrief([aSection()], { configured: false }));

    expect(el.querySelector('.notice')?.textContent).toContain(
      'Add an OpenAI or Anthropic API key',
    );
    expect(el.querySelector<HTMLButtonElement>('button.write-all')!.disabled).toBe(true);
    expect(el.querySelector<HTMLButtonElement>('[data-kind=PITCH] button.write')!.disabled).toBe(
      true,
    );
    expect(el.querySelector<HTMLButtonElement>('[data-kind=PITCH] button.edit')!.disabled).toBe(
      false,
    );
  });
});
