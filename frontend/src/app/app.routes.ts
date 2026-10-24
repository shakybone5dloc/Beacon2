import { Routes } from '@angular/router';
import { Board } from './board/board';
import { ApplicationForm } from './application-form/application-form';
import { ApplicationDetail } from './application-detail/application-detail';

export const routes: Routes = [
  { path: '', component: Board, title: 'Beacon · Board' },
  { path: 'applications/new', component: ApplicationForm, title: 'Beacon · New application' },
  { path: 'applications/:id', component: ApplicationDetail, title: 'Beacon · Application' },
  { path: 'applications/:id/edit', component: ApplicationForm, title: 'Beacon · Edit application' },
  { path: '**', redirectTo: '' },
];
