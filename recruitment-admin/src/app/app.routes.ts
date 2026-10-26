import { Routes } from '@angular/router';
import { authGuard, clientGuard, roleGuard } from './core/guards/auth.guard';
import { clientRoleGuard } from './core/guards/client-role.guard';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'admin/login' },

  {
    path: 'admin/set-password',
    loadComponent: () =>
      import('./features/admin/login/set-password.component')
        .then((m) => m.SetPasswordComponent),
  },

  {
    path: 'admin/login',
    loadComponent: () =>
      import('./features/admin/login/login.component')
        .then((m) => m.LoginComponent),
  },

  {
    path: 'admin',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./shared/components/admin-layout/admin-layout.component')
        .then((m) => m.AdminLayoutComponent),

    children: [
      { path: '', pathMatch: 'full', redirectTo: 'dashboard' },

      {
        path: 'dashboard',
        loadComponent: () =>
          import('./features/admin/dashboard/dashboard.component')
            .then((m) => m.DashboardComponent),
      },

      {
        path: 'clients',
        loadComponent: () =>
          import('./features/admin/clients/clients.component')
            .then((m) => m.ClientsComponent),
      },

      {
        path: 'clients/create',
        canActivate: [roleGuard(['ADMIN'])],
        loadComponent: () =>
          import('./features/admin/client-form/client-form.component')
            .then((m) => m.ClientFormComponent),
      },

      {
        path: 'clients/edit/:id',
        canActivate: [roleGuard(['ADMIN'])],
        loadComponent: () =>
          import('./features/admin/client-form/client-form.component')
            .then((m) => m.ClientFormComponent),
      },

      {
        path: 'clients/view/:id',
        loadComponent: () =>
          import('./features/admin/client-view/client-view.component')
            .then((m) => m.ClientViewComponent),
      },

      // Hiring Manager module
      {
        path: 'clients/:clientId/hiring',
        loadComponent: () =>
          import('./features/admin/hiring/hiring-dashboard.component')
            .then((m) => m.HiringDashboardComponent),
      },

      {
        path: 'clients/:clientId/hiring-managers',
        loadComponent: () =>
          import('./features/admin/hiring/hiring-manager-list.component')
            .then((m) => m.HiringManagerListComponent),
      },

      {
        path: 'clients/:clientId/hiring-managers/create',
        loadComponent: () =>
          import('./features/admin/hiring/hiring-manager-form.component')
            .then((m) => m.HiringManagerFormComponent),
      },

      {
        path: 'clients/:clientId/hiring-managers/edit/:id',
        loadComponent: () =>
          import('./features/admin/hiring/hiring-manager-form.component')
            .then((m) => m.HiringManagerFormComponent),
      },

      {
        path: 'clients/:clientId/requirements',
        loadComponent: () =>
          import('./features/admin/hiring/requirement-list.component')
            .then((m) => m.RequirementListComponent),
      },

      {
        path: 'clients/:clientId/requirements/create',
        loadComponent: () =>
          import('./features/admin/hiring/requirement-form.component')
            .then((m) => m.RequirementFormComponent),
      },

      {
        path: 'clients/:clientId/requirements/view/:id',
        loadComponent: () =>
          import('./features/admin/hiring/requirement-details.component')
            .then((m) => m.RequirementDetailsComponent),
      },

      {
        path: 'clients/:clientId/requirements/:id/candidates',
        loadComponent: () =>
          import('./features/admin/hiring/candidate-list.component')
            .then((m) => m.CandidateListComponent),
      },

      {
        path: 'clients/:clientId/requirements/:id/candidates/new',
        canActivate: [roleGuard(['ADMIN', 'RECRUITER'])],
        loadComponent: () =>
          import('./features/admin/hiring/candidate-form.component')
            .then((m) => m.CandidateFormComponent),
      },

      {
        path: 'clients/:clientId/candidates/:candidateId',
        loadComponent: () =>
          import('./features/admin/hiring/candidate-details.component')
            .then((m) => m.CandidateDetailsComponent),
      },

      {
        path: 'clients/:clientId/requirements/edit/:id',
        loadComponent: () =>
          import('./features/admin/hiring/requirement-form.component')
            .then((m) => m.RequirementFormComponent),
      },

      // Settings landing page
      {
        path: 'settings',
        pathMatch: 'full',
        canActivate: [roleGuard(['ADMIN'])],
        loadComponent: () =>
          import('./features/admin/settings/settings.component')
            .then((m) => m.SettingsComponent),
      },

      // User Management page
      {
        path: 'settings/users',
        canActivate: [roleGuard(['ADMIN'])],
        loadComponent: () =>
          import('./features/admin/settings/user-management.component')
            .then((m) => m.UserManagementComponent),
      },
    ],
  },

  // Client application login
  {
    path: 'app/:slug/login',
    loadComponent: () =>
      import('./features/public/client-login.component')
        .then((m) => m.ClientLoginComponent),
  },

  // Management pages inside the logged-in client's website
  {
    path: 'app/:slug/manage',
    canActivate: [clientGuard],
    loadComponent: () =>
      import('./features/public/manage/site-manage-layout.component')
        .then((m) => m.SiteManageLayoutComponent),

    children: [
      { path: '', pathMatch: 'full', redirectTo: 'requirements' },

      {
        path: 'requirements',
        loadComponent: () =>
          import('./features/public/manage/site-requirement-list.component')
            .then((m) => m.SiteRequirementListComponent),
      },

      // Create requirement (Admin and Recruiter only)
      {
        path: 'requirements/create',
        canActivate: [clientRoleGuard(['ADMIN', 'RECRUITER'])],
        loadComponent: () =>
          import('./features/public/manage/site-requirement-form.component')
            .then((m) => m.SiteRequirementFormComponent),
      },

      // View requirement
      {
        path: 'requirements/view/:id',
        loadComponent: () =>
          import('./features/public/manage/site-requirement-details.component')
            .then((m) => m.SiteRequirementDetailsComponent),
      },

      // Edit requirement (Admin and Recruiter only)
      {
        path: 'requirements/edit/:id',
        canActivate: [clientRoleGuard(['ADMIN', 'RECRUITER'])],
        loadComponent: () =>
          import('./features/public/manage/site-requirement-form.component')
            .then((m) => m.SiteRequirementFormComponent),
      },

      // Candidates for a requirement
      {
        path: 'requirements/:id/candidates',
        loadComponent: () =>
          import('./features/public/manage/site-candidate-list.component')
            .then((m) => m.SiteCandidateListComponent),
      },

      // Add candidate to a requirement (Admin and Recruiter only)
      {
        path: 'requirements/:id/candidates/new',
        canActivate: [clientRoleGuard(['ADMIN', 'RECRUITER'])],
        loadComponent: () =>
          import('./features/public/manage/site-candidate-form.component')
            .then((m) => m.SiteCandidateFormComponent),
      },

      // Candidate profile, resume, screening, stages and history
      {
        path: 'candidates/:candidateId',
        loadComponent: () =>
          import('./features/public/manage/site-candidate-details.component')
            .then((m) => m.SiteCandidateDetailsComponent),
      },
    ],
  },

  // Public client application
  {
    path: 'app/:slug',
    canActivate: [clientGuard],
    loadComponent: () =>
      import('./features/public/public-app.component')
        .then((m) => m.PublicAppComponent),
  },

  { path: '**', redirectTo: 'admin/login' },
];
