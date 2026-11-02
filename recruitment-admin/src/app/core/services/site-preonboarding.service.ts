import { HttpClient, HttpContext } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../config/api.config';
import { CLIENT_SLUG } from '../interceptors/auth.interceptor';
import { PreonboardingSettings } from '../models/preonboarding.model';

/** The pre-onboarding settings of ONE client website. Every call carries that website's own login (CLIENT_SLUG). */
export class SitePreonboardingApi {
  private url = `${API_BASE_URL}/api/site/preonboarding`;
  private context: HttpContext;

  constructor(private http: HttpClient, slug: string) {
    this.context = new HttpContext().set(CLIENT_SLUG, slug);
  }

  getSettings(): Observable<PreonboardingSettings> {
    return this.http.get<PreonboardingSettings>(`${this.url}/settings`, { context: this.context });
  }

  saveSettings(settings: PreonboardingSettings): Observable<PreonboardingSettings> {
    return this.http.put<PreonboardingSettings>(
      `${this.url}/settings`,
      {
        submissionDays: settings.submissionDays,
        items: settings.items.map((i) => ({ code: i.code, enabled: i.enabled, mandatory: i.enabled && i.mandatory })),
      },
      { context: this.context },
    );
  }
}

@Injectable({ providedIn: 'root' })
export class SitePreonboardingService {
  private http = inject(HttpClient);

  forSite(slug: string): SitePreonboardingApi {
    return new SitePreonboardingApi(this.http, slug);
  }
}
