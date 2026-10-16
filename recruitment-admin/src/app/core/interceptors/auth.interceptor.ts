import { HttpContextToken, HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { AuthService } from '../services/auth.service';

/** Set on a request to say it belongs to one client website (/app/:slug): that client's login token is sent instead of the Admin Portal's. */
export const CLIENT_SLUG = new HttpContextToken<string>(() => '');

/** Adds the login token to API calls, and sends the user to the login page when the token is no longer valid. */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);

  // Client website calls carry THAT client's session, never the Admin Portal's.
  const slug = req.context.get(CLIENT_SLUG);
  if (slug) {
    const clientToken = auth.clientToken(slug);
    const request = clientToken ? req.clone({ setHeaders: { Authorization: `Bearer ${clientToken}` } }) : req;
    return next(request).pipe(
      catchError((err: HttpErrorResponse) => {
        if (err.status === 401) {
          auth.logout(slug);
          router.navigateByUrl(`/app/${encodeURIComponent(slug)}/login`);
        }
        return throwError(() => err);
      }),
    );
  }

  const isApi = req.url.includes('/api/');
  const skip = req.url.includes('/api/auth/login') || req.url.includes('/api/public/');
  const token = auth.token;

  const request = isApi && !skip && token
    ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
    : req;

  return next(request).pipe(
    catchError((err: HttpErrorResponse) => {
      if (err.status === 401 && isApi && !skip) {
        // Only the Admin Portal session is ever attached to API calls, so only it is ended here.
        auth.logout();
        router.navigateByUrl('/admin/login');
      }
      return throwError(() => err);
    }),
  );
};