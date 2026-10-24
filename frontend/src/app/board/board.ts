import { Component, computed, inject, OnInit, signal } from '@angular/core';
import { TitleCasePipe } from '@angular/common';
import { ApplicationsApi } from '../applications/applications-api';
import {
  ApplicationResponse,
  ApplicationStatus,
  STATUSES,
} from '../applications/application.model';

interface Column {
  status: ApplicationStatus;
  cards: ApplicationResponse[];
}

@Component({
  selector: 'app-board',
  imports: [TitleCasePipe],
  templateUrl: './board.html',
  styleUrl: './board.css',
})
export class Board implements OnInit {
  private readonly api = inject(ApplicationsApi);

  protected readonly applications = signal<ApplicationResponse[] | null>(null);
  protected readonly error = signal<string | null>(null);

  protected readonly columns = computed<Column[]>(() => {
    const apps = this.applications() ?? [];

    return STATUSES.map((status) => ({
      status,
      cards: apps.filter((app) => app.status == status),
    }));
  });

  ngOnInit(): void {
    this.load();
  }

  private load(): void {
    this.api.list().subscribe({
      next: (apps) => this.applications.set(apps),
      error: () => this.error.set('Could not load applications. Is the API running?'),
    });
  }
}
