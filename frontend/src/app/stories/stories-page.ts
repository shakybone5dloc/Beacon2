import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { problemText } from '../documents/application-documents';
import { StudyApi } from '../study/study-api';
import { StoriesApi } from './stories-api';
import { STAR_PARTS, Story, StoryDraft, tagsFromText } from './story.model';

/**
 * The story bank (A7): STAR stories kept once and reused in every interview brief. Stories
 * can be written by hand, drafted by the AI from rough notes, or suggested from the resume;
 * drafts are only saved once the user keeps them.
 */
@Component({
  selector: 'app-stories-page',
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './stories-page.html',
  host: { class: 'block max-w-4xl' },
})
export class StoriesPage implements OnInit {
  private readonly api = inject(StoriesApi);
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly studyApi = inject(StudyApi);
  private readonly router = inject(Router);

  protected readonly stories = signal<Story[] | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly parts = STAR_PARTS;

  /** The story being edited, 'new' while adding one, or null when the form is closed. */
  protected readonly editing = signal<Story | 'new' | null>(null);
  protected readonly saving = signal(false);
  protected readonly formError = signal<string | null>(null);
  /** The suggestion the open form came from, dropped from the list once it's saved. */
  private fromSuggestion: StoryDraft | null = null;

  protected readonly notesOpen = signal(false);
  protected readonly notes = signal('');
  protected readonly drafting = signal(false);
  protected readonly draftError = signal<string | null>(null);

  /** Stories the AI suggested from the resume, waiting to be kept or discarded. */
  protected readonly suggestions = signal<StoryDraft[] | null>(null);
  protected readonly suggesting = signal(false);

  protected readonly tagFilter = signal<string | null>(null);
  protected readonly drilling = signal(false);
  protected readonly expanded = signal<ReadonlySet<string>>(new Set());

  protected readonly form = this.fb.group({
    title: ['', [Validators.required, Validators.maxLength(200)]],
    situation: [''],
    task: [''],
    action: [''],
    result: [''],
    metrics: [''],
    tags: [''],
  });

  /** Every tag in use, for the filter. */
  protected readonly allTags = computed(() =>
    [...new Set((this.stories() ?? []).flatMap((s) => s.tags))].sort((a, b) => a.localeCompare(b)),
  );

  protected readonly shown = computed(() => {
    const tag = this.tagFilter();
    const list = this.stories() ?? [];
    return tag ? list.filter((s) => s.tags.includes(tag)) : list;
  });

  ngOnInit(): void {
    this.api.list().subscribe({
      next: (stories) => this.stories.set(stories),
      error: () => this.error.set('Could not load your stories. Is the API running?'),
    });
  }

  protected startNew(draft: StoryDraft | null = null): void {
    this.fromSuggestion = null;
    this.form.reset({
      title: draft?.title ?? '',
      situation: draft?.situation ?? '',
      task: draft?.task ?? '',
      action: draft?.action ?? '',
      result: draft?.result ?? '',
      metrics: draft?.metrics ?? '',
      tags: draft?.tags.join(', ') ?? '',
    });
    this.formError.set(null);
    this.editing.set('new');
  }

  protected edit(story: Story): void {
    this.fromSuggestion = null;
    this.form.reset({
      title: story.title,
      situation: story.situation,
      task: story.task,
      action: story.action,
      result: story.result,
      metrics: story.metrics,
      tags: story.tags.join(', '),
    });
    this.formError.set(null);
    this.editing.set(story);
  }

  protected cancel(): void {
    this.editing.set(null);
  }

  protected save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    const target = this.editing();
    const request = {
      title: v.title.trim(),
      situation: v.situation,
      task: v.task,
      action: v.action,
      result: v.result,
      metrics: v.metrics,
      tags: tagsFromText(v.tags),
      version: target && target !== 'new' ? target.version : null,
    };
    const call =
      target && target !== 'new' ? this.api.update(target.id, request) : this.api.create(request);

    this.saving.set(true);
    this.formError.set(null);
    call.pipe(finalize(() => this.saving.set(false))).subscribe({
      next: (saved) => {
        this.upsert(saved);
        if (this.fromSuggestion) this.dropSuggestion(this.fromSuggestion);
        this.editing.set(null);
      },
      error: (err: HttpErrorResponse) =>
        this.formError.set(
          err.status === 409
            ? 'This story was changed somewhere else. Close the form, reload and try again.'
            : problemText(err, 'Could not save the story. Try again.'),
        ),
    });
  }

  protected remove(story: Story): void {
    if (!confirm(`Delete "${story.title}" from your story bank?`)) return;
    this.api.delete(story.id).subscribe({
      next: () => this.stories.update((list) => (list ?? []).filter((s) => s.id !== story.id)),
      error: () => this.error.set('Could not delete the story. Try again.'),
    });
  }

  /** Turns the user's rough notes into one story and opens it in the form to review. */
  protected draftFromNotes(): void {
    this.drafting.set(true);
    this.draftError.set(null);
    this.api
      .draft(this.notes())
      .pipe(finalize(() => this.drafting.set(false)))
      .subscribe({
        next: (drafts) => {
          this.notesOpen.set(false);
          this.notes.set('');
          this.startNew(drafts[0] ?? null);
        },
        error: (err: HttpErrorResponse) =>
          this.draftError.set(problemText(err, 'Could not draft the story. Try again.')),
      });
  }

  /**
   * Makes a study card for each story shown (all of them, or the tag being filtered by) in the
   * Stories deck, and opens it.
   */
  protected drill(): void {
    const all = this.tagFilter() == null;
    this.drilling.set(true);
    this.draftError.set(null);
    this.studyApi
      .drillStories(all ? null : this.shown().map((s) => s.id))
      .pipe(finalize(() => this.drilling.set(false)))
      .subscribe({
        next: (made) =>
          this.router.navigate(['/study', made.deck.id], {
            queryParams: { added: made.added, skipped: made.skipped, from: 'stories' },
          }),
        error: (err: HttpErrorResponse) =>
          this.draftError.set(problemText(err, 'Could not make the study cards. Try again.')),
      });
  }

  /** Asks the AI for a few stories from the default resume, to keep or discard one by one. */
  protected suggest(): void {
    this.suggesting.set(true);
    this.draftError.set(null);
    this.api
      .draft(null)
      .pipe(finalize(() => this.suggesting.set(false)))
      .subscribe({
        next: (drafts) => this.suggestions.set(drafts),
        error: (err: HttpErrorResponse) =>
          this.draftError.set(problemText(err, 'Could not suggest stories. Try again.')),
      });
  }

  /** Opens a suggestion in the form, to fill its gaps before saving. */
  protected keep(draft: StoryDraft): void {
    this.startNew(draft);
    this.fromSuggestion = draft;
  }

  protected dropSuggestion(draft: StoryDraft): void {
    this.suggestions.update((list) => {
      const rest = (list ?? []).filter((d) => d !== draft);
      return rest.length > 0 ? rest : null;
    });
  }

  protected toggle(story: Story): void {
    this.expanded.update((ids) => {
      const next = new Set(ids);
      if (!next.delete(story.id)) next.add(story.id);
      return next;
    });
  }

  protected isExpanded(story: Story): boolean {
    return this.expanded().has(story.id);
  }

  protected filterBy(tag: string | null): void {
    this.tagFilter.set(this.tagFilter() === tag ? null : tag);
  }

  private upsert(saved: Story): void {
    this.stories.update((list) => [saved, ...(list ?? []).filter((s) => s.id !== saved.id)]);
  }
}
