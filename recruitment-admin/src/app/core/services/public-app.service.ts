import { HttpClient, HttpContext } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map, switchMap } from 'rxjs';
import { CLIENT_SLUG } from '../interceptors/auth.interceptor';
import { API_BASE_URL } from '../config/api.config';
import { PublicApp, PublicFooter, PublicJob } from '../models/public-app.model';

/** Reads the public (no login) endpoints that feed /app/:slug. */
@Injectable({ providedIn: 'root' })
export class PublicAppService {
  private http = inject(HttpClient);
  private url = `${API_BASE_URL}/api/public/clients`;

  getApplication(slug: string): Observable<PublicApp> {
    return this.http.get<PublicApp>(`${this.url}/${encodeURIComponent(slug)}`);
  }

  /** A candidate applies for one open job. The form carries fullName, email, phone (optional) and the resume file. */
  apply(slug: string, jobId: string, form: FormData): Observable<{ message: string }> {
    return this.http.post<{ message: string }>(
      `${this.url}/${encodeURIComponent(slug)}/jobs/${encodeURIComponent(jobId)}/apply`, form);
  }

  getOpenJobs(slug: string): Observable<PublicJob[]> {
    return this.http.get<PublicJob[]>(`${this.url}/${encodeURIComponent(slug)}/jobs`);
  }

  /**
   * The client saves its own footer through the backend's /api/site/settings (CLIENT_ADMIN only, the client is taken
   * from the login token). That endpoint also needs the application name and description, so the current ones are
   * read first and sent back unchanged. Only the footer changes.
   */
  updateFooter(slug: string, footer: PublicFooter): Observable<PublicFooter> {
    const url = `${API_BASE_URL}/api/site/settings`;
    const context = new HttpContext().set(CLIENT_SLUG, slug);
    return this.http.get<SiteSettings>(url, { context }).pipe(
      switchMap((cur) => this.http.put<SiteSettings>(url, {
        applicationName: cur.applicationName || cur.name,
        description: cur.description,
        footer,
      }, { context })),
      map((saved) => saved.footer),
    );
  }
}

interface SiteSettings {
  slug: string;
  name: string;
  applicationName: string | null;
  description: string | null;
  footer: PublicFooter;
}
