import { Component, inject, OnInit, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { finalize } from 'rxjs';
import { ResumesApi } from './resumes-api';
import { Resume, splitList } from './resume.model';

/**
 * The library's resumes: a label for each, which one is the default, a short summary
 * fit scoring will use, and the text Beacon read from the file.
 */
@Component({
  selector: 'app-resume-list',
  imports: [ReactiveFormsModule],
  templateUrl: './resume-list.html',
})
export class ResumeList implements OnInit {
  private readonly api = inject(ResumesApi);
  private readonly fb = inject(NonNullableFormBuilder);

  protected readonly resumes = signal<Resume[] | null>(null);
  protected readonly error = signal<string | null>(null);
  /** The resume being edited. */
  protected readonly editing = signal<string | null>(null);
  protected readonly saving = signal(false);
  protected readonly formError = signal<string | null>(null);
  /** Text shown per resume, loaded when asked for; null while loading. */
  protected readonly texts = signal<ReadonlyMap<string, string | null>>(new Map());
  protected readonly openText = signal<string | null>(null);

  protected readonly form = this.fb.group({
    label: ['', [Validators.required, Validators.maxLength(100)]],
    targetTitles: [''],
    skills: [''],
    yearsExperience: [''],
  });

  ngOnInit(): void {
    this.reload();
  }

  /** After an upload or delete in the library list below. */
  reload(): void {
    this.api.list().subscribe({
      next: (resumes) => {
        this.error.set(null);
        this.resumes.set(resumes);
      },
      error: () => this.error.set('Could not load your resumes.'),
    });
  }

  protected makeDefault(resume: Resume): void {
    this.api.makeDefault(resume.documentId).subscribe({
      next: () => this.reload(),
      error: () => this.error.set('Could not change the default resume.'),
    });
  }

  protected edit(resume: Resume): void {
    this.form.reset({
      label: resume.label,
      targetTitles: resume.targetTitles.join(', '),
      skills: resume.skills.join(', '),
      yearsExperience: resume.yearsExperience?.toString() ?? '',
    });
    this.formError.set(null);
    this.editing.set(resume.documentId);
  }

  protected save(resume: Resume): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.formError.set('Give the resume a label.');
      return;
    }
    const v = this.form.getRawValue();
    const years = v.yearsExperience.trim() === '' ? null : Number(v.yearsExperience);
    if (years != null && (!Number.isInteger(years) || years < 0 || years > 60)) {
      this.formError.set('Years of experience should be a whole number from 0 to 60.');
      return;
    }
    this.saving.set(true);
    this.formError.set(null);
    this.api
      .update(resume.documentId, {
        label: v.label.trim(),
        targetTitles: splitList(v.targetTitles),
        skills: splitList(v.skills),
        yearsExperience: years,
        version: resume.version,
      })
      .pipe(finalize(() => this.saving.set(false)))
      .subscribe({
        next: (updated) => {
          this.resumes.update((list) =>
            (list ?? []).map((r) => (r.documentId === updated.documentId ? updated : r)),
          );
          this.editing.set(null);
        },
        error: (err: HttpErrorResponse) =>
          this.formError.set(
            err.status === 409
              ? 'This resume was changed somewhere else. Close the form and try again.'
              : 'Could not save the resume. Try again.',
          ),
      });
  }

  protected toggleText(resume: Resume): void {
    const id = resume.documentId;
    if (this.openText() === id) {
      this.openText.set(null);
      return;
    }
    this.openText.set(id);
    if (this.texts().has(id)) return;
    this.api.text(id).subscribe({
      next: (text) => this.texts.update((m) => new Map(m).set(id, text ?? '')),
      error: () => this.error.set('Could not load the resume text.'),
    });
  }

  protected summary(resume: Resume): string {
    return [
      resume.targetTitles.join(', '),
      resume.skills.slice(0, 8).join(', ') + (resume.skills.length > 8 ? '…' : ''),
      resume.yearsExperience != null ? `${resume.yearsExperience} years` : '',
    ]
      .filter((part) => part.length > 0)
      .join(' · ');
  }
}
