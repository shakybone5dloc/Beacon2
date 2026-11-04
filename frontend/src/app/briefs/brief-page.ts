import {
  Component,
  computed,
  DestroyRef,
  inject,
  input,
  OnInit,
  signal,
  ViewEncapsulation,
} from '@angular/core';
import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { RouterLink } from '@angular/router';
import { forkJoin, from, mergeMap } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ApplicationsApi } from '../applications/applications-api';
import {
  ApplicationResponse,
  EMPLOYMENT_TYPE_LABELS,
  employerLabel,
} from '../applications/application.model';
import { problemText } from '../documents/application-documents';
import {
  formatInZone,
  Interview,
  INTERVIEW_TYPE_LABELS,
  OUTCOME_LABELS,
} from '../interviews/interview.model';
import { InterviewsApi } from '../interviews/interviews-api';
import { PeopleApi } from '../people/people-api';
import { ApplicationPerson, PERSON_ROLE_LABELS } from '../people/person.model';
import { formatLocation, formatSalary } from '../postings/format';
import { Resume } from '../resumes/resume.model';
import { ResumesApi } from '../resumes/resumes-api';
import { Brief, BriefSection, briefMarkdown, renderMarkdown, sectionKey } from './brief.model';
import { BriefsApi } from './briefs-api';

/** Sections written at once by "Write the brief", so the AI provider isn't flooded. */
const PARALLEL_WRITES = 3;

/** Shown in an empty section the user fills in themselves. */
const OWN_SECTION_HINTS: Partial<Record<BriefSection['kind'], string>> = {
  COMPANY:
    'What you know about the company and team: size, products, leadership, strategy, where this role fits, and why it matters to you.',
};

/**
 * The interview brief (A1): one prep document per application, read before each round.
 * "Write the brief" drafts every empty section; each section can be rewritten or edited.
 */
@Component({
  selector: 'app-brief-page',
  imports: [DatePipe, RouterLink],
  templateUrl: './brief-page.html',
  styleUrl: './brief-page.css',
  // The sections' Markdown arrives through [innerHTML]; its styles are scoped by .brief instead.
  encapsulation: ViewEncapsulation.None,
})
export class BriefPage implements OnInit {
  private readonly briefsApi = inject(BriefsApi);
  private readonly applicationsApi = inject(ApplicationsApi);
  private readonly interviewsApi = inject(InterviewsApi);
  private readonly peopleApi = inject(PeopleApi);
  private readonly resumesApi = inject(ResumesApi);
  private readonly destroyRef = inject(DestroyRef);

  /** The application's id, from the route. */
  readonly id = input.required<string>();

  protected readonly application = signal<ApplicationResponse | null>(null);
  protected readonly brief = signal<Brief | null>(null);
  protected readonly interviews = signal<Interview[]>([]);
  protected readonly people = signal<ApplicationPerson[]>([]);
  protected readonly resumes = signal<Resume[]>([]);
  protected readonly error = signal<string | null>(null);
  protected readonly notice = signal<string | null>(null);

  /** Sections being written, by key. */
  protected readonly writing = signal<ReadonlySet<string>>(new Set());
  protected readonly sectionErrors = signal<ReadonlyMap<string, string>>(new Map());
  /** The section open for editing, with its draft text. */
  protected readonly editing = signal<string | null>(null);
  protected readonly draft = signal('');
  protected readonly saving = signal(false);

  protected readonly employerLabel = employerLabel;
  protected readonly key = sectionKey;
  protected readonly ownHints = OWN_SECTION_HINTS;
  private readonly rendered = new Map<string, { markdown: string; html: string }>();

  protected readonly heading = computed(() => {
    const app = this.application();
    return app ? `${employerLabel(app)} – ${app.role}` : '';
  });

  /** Rounds in order, not counting cancelled ones, with the next one ahead marked. */
  protected readonly rounds = computed(() => {
    const now = Date.now();
    const live = this.interviews().filter((i) => i.outcome !== 'CANCELLED');
    const next = live.find((i) => new Date(i.startsAt).getTime() > now);
    return live.map((interview, index) => ({
      interview,
      number: index + 1,
      next: interview === next,
      label: roundLabel(interview, index + 1),
    }));
  });

  /** The facts at the top, as lines; the page and the Markdown download share them. */
  protected readonly glance = computed(() => {
    const app = this.application();
    if (!app) return [];
    const lines = [`Role: ${app.role} at ${employerLabel(app)}`];
    const where = [formatLocation(app.location), app.remote ? 'Remote' : null].filter(Boolean);
    if (where.length) lines.push(`Where: ${where.join(', ')}`);
    if (app.employmentType) lines.push(`Type: ${EMPLOYMENT_TYPE_LABELS[app.employmentType]}`);
    const pay = formatSalary(app);
    if (pay) lines.push(`Pay: ${pay}`);
    for (const round of this.rounds()) {
      lines.push(`${round.label}${round.next ? ' (next)' : ''}`);
    }
    for (const link of this.people()) {
      const p = link.person;
      lines.push(
        `${PERSON_ROLE_LABELS[link.role]}: ${p.name}${p.title ? `, ${p.title}` : ''}${p.company && p.company !== app.company ? ` (${p.company})` : ''}`,
      );
    }
    return lines;
  });

  /** Empty sections "Write the brief" would fill; a round's update needs its debrief notes. */
  protected readonly unwritten = computed(() =>
    (this.brief()?.sections ?? []).filter(
      (s) => s.writable && !s.content && (s.kind !== 'ROUND' || this.hasDebrief(s.interviewId)),
    ),
  );

  ngOnInit(): void {
    forkJoin({
      application: this.applicationsApi.getById(this.id()),
      brief: this.briefsApi.get(this.id()),
      interviews: this.interviewsApi.onApplication(this.id()),
      people: this.peopleApi.onApplication(this.id()),
      resumes: this.resumesApi.list(),
    })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (loaded) => {
          this.application.set(loaded.application);
          this.brief.set(loaded.brief);
          this.interviews.set(loaded.interviews);
          this.people.set(loaded.people);
          this.resumes.set(loaded.resumes);
        },
        error: (err: HttpErrorResponse) =>
          this.error.set(
            err.status === 404
              ? 'That application no longer exists.'
              : 'Could not load the brief. Is the API running?',
          ),
      });
  }

  protected html(section: BriefSection): string {
    const markdown = section.content ?? '';
    const key = sectionKey(section);
    const cached = this.rendered.get(key);
    if (cached?.markdown === markdown) return cached.html;
    const html = renderMarkdown(markdown);
    this.rendered.set(key, { markdown, html });
    return html;
  }

  /** "After round 2: Panel, Oct 8" for a round's update; the kind's title otherwise. */
  protected title(section: BriefSection): string {
    if (section.kind !== 'ROUND') return section.title;
    const round = this.rounds().find((r) => r.interview.id === section.interviewId);
    if (!round) return section.title;
    const day = new Date(round.interview.startsAt).toLocaleDateString(undefined, {
      timeZone: round.interview.timeZone,
      month: 'short',
      day: 'numeric',
    });
    return `After round ${round.number}: ${INTERVIEW_TYPE_LABELS[round.interview.type]}, ${day}`;
  }

  protected hasDebrief(interviewId: string | null): boolean {
    const interview = this.interviews().find((i) => i.id === interviewId);
    return !!interview?.debriefNotes?.trim();
  }

  protected isWriting(section: BriefSection): boolean {
    return this.writing().has(sectionKey(section));
  }

  protected chooseResume(documentId: string): void {
    this.briefsApi.chooseResume(this.id(), documentId).subscribe({
      next: (brief) => this.brief.set(brief),
      error: (err: HttpErrorResponse) =>
        this.notice.set(problemText(err, 'Could not switch the resume. Try again.')),
    });
  }

  /**
   * Drafts every empty section, a few at a time; each one appears as it finishes. A failure
   * no section can get past (no key, no resume, no job description) stops the rest and is
   * shown once at the top, instead of on every section.
   */
  protected writeAll(): void {
    this.notice.set(null);
    const batch = { stopped: false };
    from(this.unwritten())
      .pipe(
        mergeMap(
          (section) =>
            batch.stopped ? Promise.resolve() : this.writeRequest(section, false, batch),
          PARALLEL_WRITES,
        ),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe();
  }

  /** Rewrites one section; one with the user's edits asks first. */
  protected write(section: BriefSection): void {
    if (
      section.edited &&
      !confirm(`You've edited "${this.title(section)}". Rewrite it and replace your changes?`)
    ) {
      return;
    }
    void this.writeRequest(section, section.edited);
  }

  /**
   * Settles when the section is written or has failed. A failure shows on the section, or,
   * in a batch, a request that can't work (422) stops the batch with one notice at the top.
   */
  private writeRequest(
    section: BriefSection,
    replaceEdits: boolean,
    batch?: { stopped: boolean },
  ): Promise<void> {
    const key = sectionKey(section);
    this.writing.update((keys) => new Set(keys).add(key));
    this.sectionErrors.update((errors) => withoutKey(errors, key));
    return new Promise<void>((resolve) => {
      this.briefsApi.write(this.id(), section.kind, section.interviewId, replaceEdits).subscribe({
        next: (written) => {
          this.replace(written);
          this.done(key);
          resolve();
        },
        error: (err: HttpErrorResponse) => {
          if (batch && err.status === 422) {
            if (!batch.stopped) {
              batch.stopped = true;
              this.notice.set(problemText(err, 'Could not write the brief.'));
            }
            this.done(key);
            resolve();
            return;
          }
          this.sectionErrors.update((errors) =>
            new Map(errors).set(
              key,
              err.status === 409
                ? 'You edited this section while it was being written. Rewrite it to replace your changes.'
                : problemText(err, 'Could not write this section. Try again.'),
            ),
          );
          this.done(key);
          resolve();
        },
      });
    });
  }

  protected edit(section: BriefSection): void {
    this.editing.set(sectionKey(section));
    this.draft.set(section.content ?? '');
    this.sectionErrors.update((errors) => withoutKey(errors, sectionKey(section)));
  }

  protected cancelEdit(): void {
    this.editing.set(null);
  }

  protected saveEdit(section: BriefSection): void {
    const key = sectionKey(section);
    this.saving.set(true);
    this.briefsApi.save(this.id(), section, this.draft()).subscribe({
      next: (saved) => {
        this.replace(saved);
        this.editing.set(null);
        this.saving.set(false);
      },
      error: (err: HttpErrorResponse) => {
        this.saving.set(false);
        this.sectionErrors.update((errors) =>
          new Map(errors).set(
            key,
            err.status === 409
              ? 'This section changed since you opened it. Copy your text, reload the page and try again.'
              : problemText(err, 'Could not save the section. Try again.'),
          ),
        );
      },
    });
  }

  /** The whole brief as a Markdown file. */
  protected download(): void {
    const app = this.application();
    if (!app) return;
    const markdown = briefMarkdown(
      `Interview brief: ${this.heading()}`,
      this.glance(),
      (this.brief()?.sections ?? []).map((s) => ({ title: this.title(s), content: s.content })),
    );
    const url = URL.createObjectURL(new Blob([markdown], { type: 'text/markdown' }));
    const link = document.createElement('a');
    link.href = url;
    link.download = `interview-brief-${slug(employerLabel(app))}.md`;
    link.click();
    URL.revokeObjectURL(url);
  }

  protected print(): void {
    window.print();
  }

  private replace(section: BriefSection): void {
    const key = sectionKey(section);
    this.brief.update((brief) =>
      brief
        ? { ...brief, sections: brief.sections.map((s) => (sectionKey(s) === key ? section : s)) }
        : brief,
    );
  }

  private done(key: string): void {
    this.writing.update((keys) => {
      const next = new Set(keys);
      next.delete(key);
      return next;
    });
  }
}

/** "Round 2: Panel, Thu, Oct 8, 2026, 2:00 PM CDT with Sam Ortiz – Passed" */
function roundLabel(interview: Interview, number: number): string {
  const who = interview.interviewers.map((p) => p.name).join(', ');
  const outcome = interview.outcome ? ` – ${OUTCOME_LABELS[interview.outcome]}` : '';
  return `Round ${number}: ${INTERVIEW_TYPE_LABELS[interview.type]}, ${formatInZone(interview.startsAt, interview.timeZone)}${who ? ` with ${who}` : ''}${outcome}`;
}

function withoutKey<V>(map: ReadonlyMap<string, V>, key: string): ReadonlyMap<string, V> {
  const next = new Map(map);
  next.delete(key);
  return next;
}

function slug(text: string): string {
  return (
    text
      .toLowerCase()
      .replace(/[^a-z0-9]+/g, '-')
      .replace(/^-|-$/g, '') || 'application'
  );
}
