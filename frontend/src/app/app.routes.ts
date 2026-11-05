import { Routes } from '@angular/router';
import { authGuard, guestGuard } from './auth/auth.guards';

/**
 * Every page loads when it's first opened, so the startup bundle carries only the app shell;
 * a page's own libraries (the board's drag and drop, the brief's Markdown) come with it.
 */
export const routes: Routes = [
  {
    path: 'login',
    loadComponent: () => import('./auth/login-page').then((m) => m.LoginPage),
    canActivate: [guestGuard],
    title: 'Beacon · Sign in',
  },
  {
    path: 'register',
    loadComponent: () => import('./auth/register-page').then((m) => m.RegisterPage),
    canActivate: [guestGuard],
    title: 'Beacon · Create an account',
  },
  {
    path: '',
    canActivateChild: [authGuard],
    children: [
      {
        path: '',
        loadComponent: () => import('./board/board').then((m) => m.Board),
        title: 'Beacon · Board',
      },
      {
        path: 'jobs',
        loadComponent: () => import('./job-search/job-search').then((m) => m.JobSearch),
        title: 'Beacon · Find jobs',
      },
      {
        path: 'matches',
        loadComponent: () => import('./searches/matches-page').then((m) => m.MatchesPage),
        title: 'Beacon · Matches',
      },
      {
        path: 'searches',
        loadComponent: () =>
          import('./searches/saved-searches-page').then((m) => m.SavedSearchesPage),
        title: 'Beacon · Saved searches',
      },
      {
        path: 'documents',
        loadComponent: () => import('./documents/document-library').then((m) => m.DocumentLibrary),
        title: 'Beacon · Documents',
      },
      {
        path: 'companies',
        loadComponent: () => import('./companies/company-list').then((m) => m.CompanyList),
        title: 'Beacon · Companies',
      },
      {
        path: 'companies/:id',
        loadComponent: () => import('./companies/company-detail').then((m) => m.CompanyDetail),
        title: 'Beacon · Company',
      },
      {
        path: 'people',
        loadComponent: () => import('./people/people-list').then((m) => m.PeopleList),
        title: 'Beacon · People',
      },
      {
        path: 'people/:id',
        loadComponent: () => import('./people/person-detail').then((m) => m.PersonDetail),
        title: 'Beacon · Person',
      },
      {
        path: 'stories',
        loadComponent: () => import('./stories/stories-page').then((m) => m.StoriesPage),
        title: 'Beacon · Stories',
      },
      {
        path: 'providers',
        loadComponent: () => import('./providers/provider-guide').then((m) => m.ProviderGuide),
        title: 'Beacon · Getting API keys',
      },
      {
        path: 'settings/api-keys',
        loadComponent: () => import('./providers/api-keys-page').then((m) => m.ApiKeysPage),
        title: 'Beacon · API keys',
      },
      {
        path: 'applications/new',
        loadComponent: () =>
          import('./application-form/application-form').then((m) => m.ApplicationForm),
        title: 'Beacon · New application',
      },
      {
        path: 'applications/:id',
        loadComponent: () =>
          import('./application-detail/application-detail').then((m) => m.ApplicationDetail),
        title: 'Beacon · Application',
      },
      {
        path: 'applications/:id/posting',
        loadComponent: () =>
          import('./posting-view/printable-posting').then((m) => m.PrintablePosting),
        title: 'Beacon · Job posting',
      },
      {
        path: 'applications/:id/edit',
        loadComponent: () =>
          import('./application-form/application-form').then((m) => m.ApplicationForm),
        title: 'Beacon · Edit application',
      },
      {
        path: 'applications/:id/brief',
        loadComponent: () => import('./briefs/brief-page').then((m) => m.BriefPage),
        title: 'Beacon · Interview brief',
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
