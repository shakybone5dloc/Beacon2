import { Routes } from '@angular/router';
import { Board } from './board/board';
import { ApplicationForm } from './application-form/application-form';
import { ApplicationDetail } from './application-detail/application-detail';
import { JobSearch } from './job-search/job-search';
import { DocumentLibrary } from './documents/document-library';
import { PrintablePosting } from './posting-view/printable-posting';
import { LoginPage } from './auth/login-page';
import { RegisterPage } from './auth/register-page';
import { authGuard, guestGuard } from './auth/auth.guards';
import { ProviderGuide } from './providers/provider-guide';
import { ApiKeysPage } from './providers/api-keys-page';
import { CompanyList } from './companies/company-list';
import { CompanyDetail } from './companies/company-detail';

export const routes: Routes = [
  { path: 'login', component: LoginPage, canActivate: [guestGuard], title: 'Beacon · Sign in' },
  {
    path: 'register',
    component: RegisterPage,
    canActivate: [guestGuard],
    title: 'Beacon · Create an account',
  },
  {
    path: '',
    canActivateChild: [authGuard],
    children: [
      { path: '', component: Board, title: 'Beacon · Board' },
      { path: 'jobs', component: JobSearch, title: 'Beacon · Find jobs' },
      { path: 'documents', component: DocumentLibrary, title: 'Beacon · Documents' },
      { path: 'companies', component: CompanyList, title: 'Beacon · Companies' },
      { path: 'companies/:id', component: CompanyDetail, title: 'Beacon · Company' },
      { path: 'providers', component: ProviderGuide, title: 'Beacon · Getting API keys' },
      { path: 'settings/api-keys', component: ApiKeysPage, title: 'Beacon · API keys' },
      { path: 'applications/new', component: ApplicationForm, title: 'Beacon · New application' },
      { path: 'applications/:id', component: ApplicationDetail, title: 'Beacon · Application' },
      {
        path: 'applications/:id/posting',
        component: PrintablePosting,
        title: 'Beacon · Job posting',
      },
      {
        path: 'applications/:id/edit',
        component: ApplicationForm,
        title: 'Beacon · Edit application',
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
