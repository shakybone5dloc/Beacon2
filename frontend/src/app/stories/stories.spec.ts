import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { StoriesPage } from './stories-page';
import { Story, StoryDraft, tagsFromText } from './story.model';

function aStory(overrides: Partial<Story> = {}): Story {
  return {
    id: 's1',
    title: 'Multi-tenant authorization at PropKnot',
    situation: 'Landlords shared one app.',
    task: 'Keep their data apart.',
    action: 'Built row-level policies.',
    result: 'No cross-tenant leaks.',
    metrics: '[how many tenants?]',
    tags: ['auth', 'security'],
    gaps: ['how many tenants?'],
    version: 0,
    createdAt: '2026-11-05T12:00:00Z',
    updatedAt: '2026-11-05T12:00:00Z',
    ...overrides,
  };
}

function aDraft(overrides: Partial<StoryDraft> = {}): StoryDraft {
  return {
    title: 'Cut checkout latency',
    situation: 'Checkout was slow.',
    task: 'Make it fast.',
    action: 'Cached price lookups.',
    result: 'Checkout got faster.',
    metrics: '[how much faster?]',
    tags: ['performance'],
    gaps: ['how much faster?'],
    ...overrides,
  };
}

it('reads tags the way the server stores them', () => {
  expect(tagsFromText(' Leadership, hardest problem ,, leadership')).toEqual([
    'leadership',
    'hardest problem',
  ]);
});

describe('StoriesPage', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  async function open(stories: Story[]) {
    const fixture = TestBed.createComponent(StoriesPage);
    fixture.detectChanges();
    http.expectOne('/api/stories').flush(stories);
    await fixture.whenStable();
    return { fixture, el: fixture.nativeElement as HTMLElement };
  }

  function set(el: HTMLElement, selector: string, value: string) {
    const field = el.querySelector<HTMLInputElement | HTMLTextAreaElement>(selector)!;
    field.value = value;
    field.dispatchEvent(new Event('input'));
  }

  it('lists stories with their tags and what is still to fill in, and filters by tag', async () => {
    const { fixture, el } = await open([
      aStory(),
      aStory({ id: 's2', title: 'Mentored two juniors', tags: ['mentoring'], gaps: [] }),
    ]);

    const first = el.querySelector('.story')!;
    expect(first.querySelector('.gaps')?.textContent).toContain(
      'Still to fill in: how many tenants?',
    );
    expect(el.querySelectorAll('.story')[1].querySelector('.gaps')).toBeNull();

    Array.from(el.querySelectorAll<HTMLButtonElement>('.tags .tag'))
      .find((b) => b.textContent?.trim() === 'mentoring')!
      .click();
    await fixture.whenStable();
    expect(
      Array.from(el.querySelectorAll('.story .title')).map((t) => t.textContent?.trim()),
    ).toEqual(['Mentored two juniors']);
  });

  it('drills the stories shown as study cards and opens the Stories deck', async () => {
    const { fixture, el } = await open([
      aStory({ id: 's1', tags: ['security'] }),
      aStory({ id: 's2', title: 'Legacy to cloud', tags: ['migration'] }),
    ]);
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const deck = { id: 'd5', name: 'Stories', kind: 'STORIES' };

    el.querySelector<HTMLButtonElement>('.drill')!.click();
    const all = http.expectOne({ method: 'POST', url: '/api/study/stories' });
    expect(all.request.body).toEqual({ storyIds: null });
    all.flush({ deck, added: 2, skipped: 0 });
    expect(navigate).toHaveBeenCalledWith(['/study', 'd5'], {
      queryParams: { added: 2, skipped: 0, from: 'stories' },
    });

    // With a tag picked, only those stories.
    Array.from(el.querySelectorAll<HTMLButtonElement>('.tags .tag'))
      .find((b) => b.textContent?.trim() === 'security')!
      .click();
    fixture.detectChanges();
    expect(el.querySelector('.drill')?.textContent).toContain('Drill these as cards');
    el.querySelector<HTMLButtonElement>('.drill')!.click();
    const some = http.expectOne('/api/study/stories');
    expect(some.request.body).toEqual({ storyIds: ['s1'] });
    some.flush({ deck, added: 0, skipped: 1 });
  });

  it('shows the whole story when its title is clicked', async () => {
    const { fixture, el } = await open([aStory()]);

    el.querySelector<HTMLButtonElement>('.story .title')!.click();
    await fixture.whenStable();

    const parts = Array.from(el.querySelectorAll('.star dt')).map((dt) => dt.textContent?.trim());
    expect(parts).toEqual(['Situation', 'Task', 'Action', 'Result', 'Numbers']);
  });

  it('saves a new story with its tags split', async () => {
    const { fixture, el } = await open([]);
    expect(el.querySelector('.empty')?.textContent).toContain('No stories yet');

    el.querySelector<HTMLButtonElement>('button.new')!.click();
    await fixture.whenStable();
    set(el, 'input[formControlName=title]', 'Led the migration');
    set(el, 'textarea[name=action]', 'Planned it in three phases.');
    set(el, 'input[formControlName=tags]', 'Leadership, legacy to cloud');
    el.querySelector('form')!.dispatchEvent(new Event('submit'));

    const req = http.expectOne((r) => r.method === 'POST' && r.url === '/api/stories');
    expect(req.request.body).toEqual(
      expect.objectContaining({
        title: 'Led the migration',
        action: 'Planned it in three phases.',
        tags: ['leadership', 'legacy to cloud'],
        version: null,
      }),
    );
    req.flush(aStory({ id: 's9', title: 'Led the migration' }));
    await fixture.whenStable();

    expect(el.querySelector('form')).toBeNull();
    expect(el.querySelector('.story .title')?.textContent?.trim()).toBe('Led the migration');
  });

  it('drafts a story from notes and opens it to review before saving', async () => {
    const { fixture, el } = await open([]);

    el.querySelector<HTMLButtonElement>('button.from-notes')!.click();
    await fixture.whenStable();
    set(el, '.notes textarea', 'Made checkout fast by caching prices.');
    await fixture.whenStable();
    el.querySelector<HTMLButtonElement>('button.draft')!.click();

    const req = http.expectOne((r) => r.method === 'POST' && r.url === '/api/stories/drafts');
    expect(req.request.body).toEqual({
      notes: 'Made checkout fast by caching prices.',
      resumeDocumentId: null,
    });
    req.flush([aDraft()]);
    await fixture.whenStable();

    expect(el.querySelector('.notes')).toBeNull();
    expect(el.querySelector<HTMLInputElement>('input[formControlName=title]')!.value).toBe(
      'Cut checkout latency',
    );
    expect(el.querySelector<HTMLInputElement>('input[formControlName=tags]')!.value).toBe(
      'performance',
    );
    http.expectNone((r) => r.url === '/api/stories' && r.method === 'POST');
  });

  it('suggests stories from the resume to keep or discard one by one', async () => {
    const { fixture, el } = await open([]);

    el.querySelector<HTMLButtonElement>('button.suggest')!.click();
    const req = http.expectOne((r) => r.url === '/api/stories/drafts');
    expect(req.request.body).toEqual({ notes: null, resumeDocumentId: null });
    req.flush([aDraft(), aDraft({ title: 'Mentored two juniors' })]);
    await fixture.whenStable();
    expect(el.querySelectorAll('.suggestion').length).toBe(2);

    el.querySelectorAll<HTMLButtonElement>('.suggestion button.discard')[1].click();
    await fixture.whenStable();
    el.querySelector<HTMLButtonElement>('.suggestion button.keep')!.click();
    await fixture.whenStable();
    el.querySelector('form')!.dispatchEvent(new Event('submit'));
    http
      .expectOne((r) => r.method === 'POST' && r.url === '/api/stories')
      .flush(aStory({ id: 's3', title: 'Cut checkout latency' }));
    await fixture.whenStable();

    // Kept and saved, so it's gone from the suggestions, and the list of them closes.
    expect(el.querySelector('.suggestions')).toBeNull();
    expect(el.querySelector('.story .title')?.textContent?.trim()).toBe('Cut checkout latency');
  });

  it('says why drafting failed', async () => {
    const { fixture, el } = await open([]);

    el.querySelector<HTMLButtonElement>('button.suggest')!.click();
    http
      .expectOne((r) => r.url === '/api/stories/drafts')
      .flush(
        { detail: 'Add an OpenAI or Anthropic API key under Settings to draft stories.' },
        { status: 422, statusText: 'Unprocessable Entity' },
      );
    await fixture.whenStable();

    expect(el.querySelector('.draft-error')?.textContent).toContain('Add an OpenAI or Anthropic');
  });

  it('edits with the version it was loaded at, and deletes after asking', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    const { fixture, el } = await open([aStory({ version: 3 })]);

    el.querySelector<HTMLButtonElement>('.story button.edit')!.click();
    await fixture.whenStable();
    set(el, 'textarea[formControlName=metrics]', '40 tenants');
    el.querySelector('form')!.dispatchEvent(new Event('submit'));
    const req = http.expectOne((r) => r.method === 'PUT' && r.url === '/api/stories/s1');
    expect(req.request.body).toEqual(
      expect.objectContaining({ metrics: '40 tenants', version: 3 }),
    );
    req.flush(aStory({ metrics: '40 tenants', gaps: [], version: 4 }));
    await fixture.whenStable();
    expect(el.querySelector('.story .gaps')).toBeNull();

    el.querySelector<HTMLButtonElement>('.story button.delete')!.click();
    http.expectOne((r) => r.method === 'DELETE' && r.url === '/api/stories/s1').flush(null);
    await fixture.whenStable();
    expect(el.querySelector('.story')).toBeNull();
  });
});
