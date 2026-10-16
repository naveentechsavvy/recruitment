import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { catchError, forkJoin, of } from 'rxjs';
import { API_BASE_URL } from '../config/api.config';
import { portalColors } from '../models/client-configuration.model';
import { PublicApp, PublicJob } from '../models/public-app.model';
import { PublicAppService } from './public-app.service';

/**
 * Holds the one client the public site is showing (chosen by the slug in the URL).
 * Loaded once by the site shell; the home, job, apply and success pages just read from it.
 */
@Injectable({ providedIn: 'root' })
export class PublicSiteStore {
  private api = inject(PublicAppService);
  private title = inject(Title);

  slug = signal('');
  app = signal<PublicApp | null>(null);
  jobs = signal<PublicJob[]>([]);
  loading = signal(true);
  notFound = signal(false);
  loadError = signal(false);

  name = computed(() => this.app()?.name ?? '');
  description = computed(() => this.app()?.description ?? '');
  stages = computed(() => this.app()?.stages ?? []);
  portalName = computed(() => this.app()?.applicationName || `${this.name()} Recruitment Portal`);
  colors = computed(() => portalColors(this.app()));
  accent = computed(() => this.colors().header);
  logo = computed(() => (this.app()?.logoUrl ? API_BASE_URL + this.app()!.logoUrl : null));

  job(id: string): PublicJob | undefined {
    return this.jobs().find((j) => String(j.id) === id);
  }

  load(slug: string) {
    if (slug === this.slug() && this.app()) return; // already loaded: moving between pages needs no reload
    this.slug.set(slug);
    this.loading.set(true);
    this.notFound.set(false);
    this.loadError.set(false);
    this.app.set(null);
    this.jobs.set([]);
    forkJoin({
      app: this.api.getApplication(slug),
      jobs: this.api.getOpenJobs(slug).pipe(catchError(() => of([] as PublicJob[]))), // jobs failing should not hide the site
    }).subscribe({
      next: ({ app, jobs }) => {
        this.app.set(app);
        this.jobs.set(jobs);
        this.loading.set(false);
        this.title.setTitle(app.applicationName || app.name);
      },
      error: (err: HttpErrorResponse) => {
        this.loading.set(false);
        if (err.status === 404) this.notFound.set(true); else this.loadError.set(true);
      },
    });
  }

  reload() {
    const slug = this.slug();
    this.slug.set('');
    this.app.set(null);
    this.load(slug);
  }
}
