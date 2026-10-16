import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { map } from 'rxjs';
import { AuthService, Role } from '../services/auth.service';

/** Must hold an Admin Portal session that the server confirms as ADMIN. A client website login does not count. */
export const authGuard: CanActivateFn = () => {
  const router = inject(Router);
  return inject(AuthService).verifyAdminSession().pipe(
    map((ok) => (ok ? true : router.createUrlTree(['/admin/login']))),
  );
};

/** Must have one of the given roles, e.g. canActivate: [roleGuard(['ADMIN'])]. Others go back to the dashboard. */
export const roleGuard = (roles: Role[]): CanActivateFn => () => {
  const auth = inject(AuthService);
  return auth.hasRole(roles) ? true : inject(Router).createUrlTree(['/admin/dashboard']);
};

/** The client website at /app/:slug needs a login for THAT client. Its session is separate from the Admin Portal's. */
export const clientGuard: CanActivateFn = (route) => {
  const slug = route.paramMap.get('slug') ?? '';
  return inject(AuthService).isClientLoggedIn(slug)
    ? true
    : inject(Router).createUrlTree(['/app', slug, 'login']);
};