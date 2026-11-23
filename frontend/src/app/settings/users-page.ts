import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { finalize, Observable } from 'rxjs';
import { shortDurationBetween } from '../applications/durations';
import { problemText } from '../documents/application-documents';
import { AdminUser, UsersApi } from './users-api';

/**
 * Settings, admins only: everyone who has signed up, when they last used Beacon, and switching an
 * account off (it can't sign in, and its sessions end) or back on. Their data is kept either way.
 */
@Component({
  selector: 'app-users-page',
  imports: [DatePipe],
  template: `
    <h1>Users</h1>
    <p class="m-0 mb-space-lg text-body-md text-on-surface-variant">
      Everyone who has signed up, newest first. Switching an account off stops it signing in and
      ends its sessions straight away; nothing in it is deleted, and you can switch it back on.
    </p>

    @if (notice(); as text) {
      <p class="notice">{{ text }}</p>
    }

    @if (loadError()) {
      <p class="notice">Could not load the users. Try again in a moment.</p>
    } @else if (users(); as list) {
      <p class="summary m-0 mb-space-sm font-mono text-label-code text-on-surface-variant">
        {{ list.length }} {{ list.length === 1 ? 'account' : 'accounts' }}
        @if (switchedOff() > 0) {
          · {{ switchedOff() }} switched off
        }
      </p>
      <ul class="users card m-0 list-none p-0">
        @for (u of list; track u.id) {
          <li
            class="user flex flex-wrap items-center gap-x-space-md gap-y-space-xs border-b border-hairline px-space-md py-space-sm last:border-b-0"
            [class.opacity-60]="u.disabledAt"
          >
            <div class="min-w-0 flex-1 basis-[14rem]">
              <span class="email block truncate font-medium text-on-surface">{{ u.email }}</span>
              <span class="block truncate text-body-sm text-on-surface-variant">
                {{ u.displayName ?? 'No name' }} · joined {{ u.createdAt | date: 'MMM d, y' }} ·
                {{ u.applicationCount }}
                {{ u.applicationCount === 1 ? 'application' : 'applications' }}
              </span>
            </div>
            <span class="flex shrink-0 flex-wrap items-center gap-space-xs">
              @if (u.admin) {
                <span class="pill pill-blue">Admin</span>
              }
              @if (u.disabledAt) {
                <span class="pill pill-red">Switched off</span>
              } @else if (!u.emailConfirmed) {
                <span class="pill pill-amber">Email not confirmed</span>
              }
              <span class="active font-mono text-label-code text-on-surface-variant">{{
                lastActive(u)
              }}</span>
              @if (!u.admin) {
                @if (u.disabledAt) {
                  <button
                    type="button"
                    class="enable btn btn-sm"
                    [disabled]="busy() === u.id"
                    (click)="enable(u)"
                  >
                    Switch on
                  </button>
                } @else {
                  <button
                    type="button"
                    class="disable btn btn-sm"
                    [disabled]="busy() === u.id"
                    (click)="disable(u)"
                  >
                    Switch off
                  </button>
                }
              }
            </span>
          </li>
        }
      </ul>
    } @else {
      <p class="text-on-surface-variant">Loading...</p>
    }
  `,
  host: { class: 'block max-w-3xl' },
})
export class UsersPage implements OnInit {
  private readonly api = inject(UsersApi);

  protected readonly users = signal<AdminUser[] | null>(null);
  protected readonly loadError = signal(false);
  protected readonly notice = signal<string | null>(null);
  /** The account being switched off or on. */
  protected readonly busy = signal<string | null>(null);

  protected readonly switchedOff = computed(
    () => (this.users() ?? []).filter((u) => u.disabledAt).length,
  );

  ngOnInit(): void {
    this.api.list().subscribe({
      next: (users) => this.users.set(users),
      error: () => this.loadError.set(true),
    });
  }

  protected lastActive(user: AdminUser): string {
    if (!user.lastActiveAt) return 'not seen yet';
    const ago = shortDurationBetween(user.lastActiveAt, new Date());
    return ago === '<1h' ? 'active now' : `active ${ago} ago`;
  }

  protected disable(user: AdminUser): void {
    if (
      !confirm(
        `Switch off ${user.email}? They're signed out everywhere and can't sign in until you switch it back on. Nothing is deleted.`,
      )
    ) {
      return;
    }
    this.change(user, this.api.disable(user.id));
  }

  protected enable(user: AdminUser): void {
    this.change(user, this.api.enable(user.id));
  }

  private change(user: AdminUser, request: Observable<AdminUser>): void {
    this.busy.set(user.id);
    this.notice.set(null);
    request.pipe(finalize(() => this.busy.set(null))).subscribe({
      next: (updated) =>
        this.users.update((list) => list?.map((u) => (u.id === updated.id ? updated : u)) ?? null),
      error: (err: HttpErrorResponse) =>
        this.notice.set(problemText(err, "That didn't work. Try again in a moment.")),
    });
  }
}
