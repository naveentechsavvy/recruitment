# Recruitment Platform – Admin Portal (Day 1)

Angular 20 · standalone components · Reactive Forms · SCSS · no UI library.

## Run
    npm install
    npm start          # http://localhost:4200
    npm run build      # production build check

Login (mock): any valid email + password of 4+ chars (prefilled: contact@velzenixnova.com / admin123).

## Structure
    src/app/core/models      ClientConfiguration model, stage & theme options
    src/app/core/services    ClientConfigurationService (localStorage), AuthService (mock)
    src/app/core/guards      authGuard
    src/app/shared/components admin-layout, status-badge, workflow-preview
    src/app/features/admin   login, dashboard, clients, client-form (create + edit), client-view, settings

## Swapping in the backend later
Every ClientConfigurationService method returns an Observable. Replace the localStorage
internals with HttpClient calls to Spring Boot; components need no changes.
Data is stored in localStorage key `velzonix.clients.v1` (clear it to reset the seed data).
