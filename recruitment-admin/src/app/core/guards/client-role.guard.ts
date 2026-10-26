import { inject } from '@angular/core';
import { ActivatedRouteSnapshot, CanActivateFn, Router } from '@angular/router';
import { AuthService, Role } from '../services/auth.service';

/** The :slug of a client website, found on this route or any parent route. */
function slugOf(route: ActivatedRouteSnapshot): string {
  for (let r: ActivatedRouteSnapshot | null = route; r; r = r.parent) {
    const slug = r.paramMap.get('slug');
    if (slug) return slug;
  }
  return '';
}

/**
 * Lets only the given roles open a screen inside a client website (/app/:slug/manage/...).
 * It reads the role of the person signed in to THAT client, not the Admin Portal session.
 * Anyone else is sent back to the requirements list. The server refuses them as well, this just avoids a dead screen.
 */
export const clientRoleGuard = (roles: Role[]): CanActivateFn => (route) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const slug = slugOf(route);
  const role = auth.clientRole(slug);
  if (role && roles.includes(role)) return true;
  return router.createUrlTree(['/app', slug, 'manage', 'requirements']);
};
