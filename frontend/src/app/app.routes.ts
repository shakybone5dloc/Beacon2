import { Routes } from '@angular/router';
import { Board } from './board/board';
import { NewApplication } from './new-application/new-application';

export const routes: Routes = [
  { path: '', component: Board, title: 'Beacon · Board' },
  { path: 'applications/new', component: NewApplication, title: 'Beacon · New application' },
  { path: '**', redirectTo: '' },
];
