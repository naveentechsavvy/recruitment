import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, catchError, map, of, tap } from 'rxjs';

// Admin Portal and every client website keep their OWN session. They never share a key.
const KEY = 'velzonix.admin.session';
const CLIENT_KEY = 'velzonix.client.session.';

// IMPORTANT: use the same backend base URL your other services use (e.g. ClientService).
const API_URL = 'http://localhost:8080/api';

export type Role =
  | 'ADMIN'
  | 'RECRUITER'
  | 'HIRING_MANAGER'
  | 'MANAGER'
  | 'HR_BP'
  | 'CLIENT_ADMIN';

/**
 * Who may edit the website (footer, name, logo) of a client website.
 * Admin, Recruiter and the client's website editor (CLIENT_ADMIN). Hiring Manager, Manager and HR BP cannot.
 * The server enforces the same rule (SecurityConfig); this list only decides what the screen shows.
 */
export const FOOTER_EDIT_ROLES: Role[] = ['ADMIN', 'RECRUITER', 'CLIENT_ADMIN'];

/**
 * Who may create and edit requirements (jobs) on a client website.
 * The server enforces the same rule (SecurityConfig); this list only decides what the screen shows.
 */
export const REQUIREMENT_EDIT_ROLES: Role[] = ['ADMIN', 'RECRUITER'];

interface Session {
  token: string;
  email: string;
  role: Role;
  clientId: number | null;
}

/** Real login against the Spring Boot backend. The session (token + role) is kept in localStorage. */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private http = inject(HttpClient);

  /**
   * No portal = Admin Portal login (stored under the admin key).
   * portal = the client's slug when signing in through a client page (/app/:slug/login).
   * Each client session is stored separately from the Admin Portal session.
   */
  login(email: string, password: string, portal?: string): Observable<void> {
    return this.http.post<Session>(`${API_URL}/auth/login`, { email, password, portal }).pipe(
      tap((session) => {
        if (portal) {
          localStorage.setItem(CLIENT_KEY + portal.toLowerCase(), JSON.stringify(session));
        } else {
          localStorage.setItem(KEY, JSON.stringify(session));
        }
      }),
      map(() => void 0),
    );
  }

  /** Log out of the Admin Portal or the specified client website only. */
  logout(portal?: string): void {
    localStorage.removeItem(
      portal ? CLIENT_KEY + portal.toLowerCase() : KEY,
    );
  }

  /** Is the person signed in to this client's website? */
  isClientLoggedIn(portal: string): boolean {
    try {
      const s = JSON.parse(
        localStorage.getItem(CLIENT_KEY + portal.toLowerCase()) ?? 'null',
      );
      return !!s && !!s.token && !this.isExpired(s.token);
    } catch {
      return false;
    }
  }

  /** The session of one client website (/app/:slug), or null. */
  private readClient(portal: string): Session | null {
    try {
      const s = JSON.parse(
        localStorage.getItem(CLIENT_KEY + portal.toLowerCase()) ?? 'null',
      );
      return s && s.token && !this.isExpired(s.token)
        ? (s as Session)
        : null;
    } catch {
      return null;
    }
  }

  /** Token of the person signed in to this client website. */
  clientToken(portal: string): string {
    return this.readClient(portal)?.token ?? '';
  }

  /** Role of the person signed in to this client website. */
  clientRole(portal: string): Role | null {
    return this.readClient(portal)?.role ?? null;
  }

  /**
   * Show "Add / Edit / Change status" for requirements?
   * Admin and Recruiter only. Everyone else can look but not change (the server refuses them anyway).
   */
  canEditRequirements(portal: string): boolean {
    const role = this.clientRole(portal);
    return !!role && REQUIREMENT_EDIT_ROLES.includes(role);
  }

  /** Add Candidate button: Admin and Recruiter only. The server refuses everyone else too. */
  canAddCandidates(portal: string): boolean {
    const role = this.clientRole(portal);
    return role === 'ADMIN' || role === 'RECRUITER';
  }

  /** Manager is view-only on a client website. */
  isViewOnly(portal: string): boolean {
    return this.clientRole(portal) === 'MANAGER';
  }

  /** Footer editor visibility: Admin, Recruiter and CLIENT_ADMIN only. */
  canEditFooter(portal: string): boolean {
    const role = this.clientRole(portal);
    return !!role && FOOTER_EDIT_ROLES.includes(role);
  }

  /**
   * Asks the server who the stored Admin Portal token belongs to.
   * Only an ADMIN session issued by the Admin Portal login passes.
   */
  verifyAdminSession(): Observable<boolean> {
    if (!this.isLoggedIn()) return of(false);

    return this.http
      .get<{ role: Role; portal: string | null }>(`${API_URL}/auth/me`)
      .pipe(
        map((me) => me.role === 'ADMIN' && me.portal === 'ADMIN'),
        catchError(() => of(false)),
        tap((ok) => {
          if (!ok) this.logout();
        }),
      );
  }

  isLoggedIn(): boolean {
    const s = this.read();
    return !!s && !this.isExpired(s.token);
  }

  hasRole(roles: Role[]): boolean {
    const s = this.read();
    return !!s && this.isLoggedIn() && roles.includes(s.role);
  }

  get token(): string {
    return this.read()?.token ?? '';
  }

  get email(): string {
    return this.read()?.email ?? '';
  }

  get role(): Role | null {
    return this.read()?.role ?? null;
  }

  get clientId(): number | null {
    return this.read()?.clientId ?? null;
  }

  private read(): Session | null {
    try {
      const s = JSON.parse(localStorage.getItem(KEY) ?? 'null');
      return s && s.token ? (s as Session) : null;
    } catch {
      return null;
    }
  }

  private isExpired(token: string): boolean {
    try {
      const payload = JSON.parse(
        atob(token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')),
      );
      return payload.exp * 1000 < Date.now();
    } catch {
      return true;
    }
  }
}
