import { Component, inject } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { Title } from '@angular/platform-browser';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { catchError, forkJoin, of } from 'rxjs';
import { API_BASE_URL } from '../../core/config/api.config';
import { RESUME_ACCEPT, RESUME_MAX_MB, resumeFileError } from '../../core/config/upload.config';
import { errorText } from '../../core/utils/http-error';
import { PortalColors, portalColors } from '../../core/models/client-configuration.model';
import { PublicApp, PublicFooter, PublicJob } from '../../core/models/public-app.model';
import { FooterEditorComponent } from './footer-editor.component';
import { AuthService } from '../../core/services/auth.service';
import { PublicAppService } from '../../core/services/public-app.service';
import { skillList } from '../../core/models/hiring.model';
import { PillComponent } from '../../shared/components/pill/pill.component';

/**
 * The reusable public client application, shown at /app/:slug (needs a login for that client).
 * One page for every client: the slug in the URL decides whose name, logo, theme, jobs and process it shows.
 *
 * UI-only refresh: the template markup and styles were rearranged. All bindings, text and logic are unchanged.
 */
@Component({
  selector: 'app-public-app',
  imports: [PillComponent, FormsModule, FooterEditorComponent],
  styles: `
    :host { display: block; min-height: 100vh; background: #f5f7fa; color: #1a2233; -webkit-font-smoothing: antialiased; }

    /* ---------- loading / error states (unchanged) ---------- */
    .center { min-height: 100vh; display: flex; flex-direction: column; align-items: center; justify-content: center; text-align: center; padding: 24px; }
    .center h1 { font-size: 24px; margin: 0 0 8px; } .center p { color: #667085; margin: 0 0 16px; }

    .wrap { max-width: 1040px; margin: 0 auto; padding: 0 24px; }
    .hero .wrap { position: relative; }
    .site { min-height: 100vh; display: flex; flex-direction: column; }
    .site > main { flex: 1 0 auto; width: 100%; }

    /* ---------- company header ---------- */
    .hero {
      background: linear-gradient(135deg, var(--accent), var(--accent2));
      color: var(--on-header);
      padding: 22px 0 24px;
    }
    .brand { display: flex; align-items: flex-start; gap: 16px; min-width: 0; }
    .brand img, .mark { flex: none; width: 60px; height: 60px; border-radius: 12px; background: #fff; object-fit: contain; padding: 5px; box-shadow: 0 2px 8px rgba(0,0,0,.18); }
    .mark { display: flex; align-items: center; justify-content: center; font-size: 26px; font-weight: 700; color: var(--accent-text); }
    .brand-text { min-width: 0; }
    .brand h1 { margin: 0; font-size: 24px; line-height: 1.2; font-weight: 700; letter-spacing: -0.01em; }
    .by { margin: 2px 0 0; font-size: 14px; opacity: .85; }
    .desc { margin: 10px 0 0; max-width: 640px; font-size: 14.5px; line-height: 1.55; opacity: .95; }

    /* ---------- page layout: jobs left, hiring process right ---------- */
    .layout {
      display: grid;
      grid-template-columns: minmax(0, 1fr) 330px;
      gap: 28px;
      align-items: start;
      margin: 28px auto 48px;
    }
    .layout > section:only-child { grid-column: 1 / -1; }
    .process { position: sticky; top: 20px; }

    h2 { display: flex; align-items: center; gap: 10px; margin: 0 0 14px; font-size: 18px; font-weight: 700; letter-spacing: -0.01em; }
    h2::before { content: ''; width: 4px; height: 18px; border-radius: 2px; background: var(--accent); }
    .count { background: color-mix(in srgb, var(--accent) 12%, #fff); color: var(--accent-text); font-size: 12px; font-weight: 700; border-radius: 999px; padding: 2px 10px; }

    /* ---------- job card ---------- */
    .job { background: #fff; border: 1px solid #e3e8ef; border-radius: 14px; padding: 20px 22px; margin-bottom: 16px; box-shadow: 0 1px 3px rgba(16,24,40,.06); }
    .job-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; }
    .job h3 { margin: 0; font-size: 18px; line-height: 1.3; font-weight: 700; letter-spacing: -0.01em; }
    .id { color: #667085; font-size: 13px; margin-top: 3px; }
    .meta { display: flex; flex-wrap: wrap; gap: 8px; margin: 14px 0 12px; }
    .meta span { display: inline-flex; align-items: center; gap: 5px; background: #f3f5f8; border: 1px solid #e8ecf2; border-radius: 999px; padding: 3px 12px; color: #475467; font-size: 13px; }
    .skills { display: flex; flex-wrap: wrap; gap: 6px; }
    .skills span { background: color-mix(in srgb, var(--accent) 9%, #fff); color: color-mix(in srgb, var(--accent) 80%, #000); border: 1px solid color-mix(in srgb, var(--accent) 18%, #fff); border-radius: 6px; padding: 3px 10px; font-size: 12.5px; }
    .job-desc { margin-top: 14px; padding-top: 12px; border-top: 1px solid #edf0f5; }
    .job-desc h4 { margin: 0 0 6px; font-size: 14px; font-weight: 700; color: var(--accent-text); }
    .job-desc p { white-space: pre-wrap; margin: 0; color: #344054; font-size: 14.5px; line-height: 1.6; }
    .apply-btn { margin-top: 16px; background: var(--accent); color: var(--on-accent); border: 0; border-radius: 8px; padding: 10px 22px; font: inherit; font-weight: 600; cursor: pointer; }
    .apply-btn:hover:not(:disabled) { filter: brightness(.92); }
    .apply-btn:disabled { opacity: .6; cursor: not-allowed; }
    .apply-btn.ghost { background: #fff; color: #475467; border: 1px solid #d0d5dd; margin-left: 8px; }
    .apply { margin-top: 16px; padding-top: 16px; border-top: 1px solid #edf0f5; }
    .apply h4 { margin: 0 0 12px; font-size: 15px; }
    .apply .row { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
    .apply label { display: block; font-size: 13px; font-weight: 600; margin: 0 0 4px; }
    .apply .f { margin-bottom: 12px; }
    .apply input { width: 100%; box-sizing: border-box; padding: 9px 11px; border: 1px solid #d0d5dd; border-radius: 8px; font: inherit; background: #fff; }
    .apply input:focus { outline: 2px solid color-mix(in srgb, var(--accent) 25%, #fff); border-color: var(--accent); }
    .apply .hint { color: #667085; font-size: 12px; margin-top: 3px; }
    .apply .err { background: #fef2f2; border: 1px solid #fecaca; color: #991b1b; border-radius: 8px; padding: 9px 12px; margin-bottom: 12px; font-size: 13.5px; }
    .applied { margin-top: 16px; background: #f0fdf4; border: 1px solid #bbf7d0; color: #166534; border-radius: 8px; padding: 12px 14px; }
    @media (max-width: 560px) { .apply .row { grid-template-columns: 1fr; } }
    .empty { background: #fff; border: 1px dashed #cfd6e0; border-radius: 14px; padding: 32px; text-align: center; color: #667085; }

    /* ---------- hiring process timeline ---------- */
    ol.steps { list-style: none; margin: 0; padding: 18px 20px 4px; background: #fff; border: 1px solid #e3e8ef; border-radius: 14px; box-shadow: 0 1px 3px rgba(16,24,40,.06); }
    ol.steps li { position: relative; display: flex; gap: 14px; align-items: flex-start; padding-bottom: 20px; }
    ol.steps li:not(:last-child)::before {
      content: ''; position: absolute; left: 14px; top: 34px; bottom: 4px; width: 2px; border-radius: 1px;
      background: color-mix(in srgb, var(--accent) 28%, #e3e8ef);
    }
    .num { position: relative; flex: none; width: 30px; height: 30px; border-radius: 50%; background: var(--accent); color: var(--on-accent); display: flex; align-items: center; justify-content: center; font-weight: 600; font-size: 14px; box-shadow: 0 0 0 4px #fff; }
    .step-body { padding-top: 3px; min-width: 0; }
    .step-name { font-weight: 600; font-size: 15px; line-height: 1.3; }
    .step-desc { color: #667085; font-size: 13.5px; line-height: 1.45; margin-top: 2px; }

    /* ---------- footer ---------- */
    .site-footer { background: var(--footer-bg); color: var(--on-footer); padding: 40px 0 0; font-size: 14.5px; line-height: 1.6; }
    .foot-grid { display: flex; flex-wrap: wrap; gap: 28px 64px; align-items: flex-start; padding-bottom: 32px; }
    .foot-col { flex: 1 1 220px; min-width: 0; }
    .foot-brand { flex: 1.6 1 280px; max-width: 460px; }
    .foot-name { display: block; font-size: 20px; font-weight: 700; line-height: 1.25; letter-spacing: -.01em; margin-bottom: 8px; }
    .foot-text { margin: 0; opacity: .9; white-space: pre-line; }
    .foot-list { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 12px; }
    .foot-item { display: flex; align-items: flex-start; gap: 12px; min-width: 0; overflow-wrap: anywhere; }
    .foot-item address { font-style: normal; white-space: pre-line; }
    .ico { flex: none; width: 18px; height: 18px; margin-top: 3px; opacity: .85; }
    .site-footer a { color: inherit; text-decoration: none; }
    .site-footer a:hover span, .site-footer a:focus-visible span { text-decoration: underline; text-underline-offset: 3px; }
    .site-footer a:focus-visible, .foot-edit-btn:focus-visible { outline: 2px solid currentColor; outline-offset: 3px; border-radius: 4px; }
    .foot-bar { display: flex; flex-wrap: wrap; align-items: center; justify-content: space-between; gap: 12px 24px; padding: 18px 0; border-top: 1px solid color-mix(in srgb, var(--on-footer) 22%, transparent); }
    .foot-copy { margin: 0; font-size: 13px; opacity: .85; }
    .foot-edit-btn { display: inline-flex; align-items: center; gap: 8px; background: transparent; color: var(--on-footer); border: 1px solid color-mix(in srgb, var(--on-footer) 45%, transparent); border-radius: 999px; padding: 6px 14px; font: inherit; font-size: 13px; font-weight: 600; cursor: pointer; }
    .foot-edit-btn .ico { width: 14px; height: 14px; margin: 0; opacity: 1; }
    .foot-edit-btn:hover { background: color-mix(in srgb, var(--on-footer) 14%, transparent); }
    .foot-actions { display: flex; flex-wrap: wrap; gap: 10px; }
    .foot-editor { padding-bottom: 24px; }

    /* ---------- tablet / mobile ---------- */
    @media (max-width: 900px) {
      .layout { grid-template-columns: minmax(0, 1fr); gap: 24px; margin-top: 22px; }
      .process { position: static; }
    }
    @media (max-width: 560px) {
      .wrap { padding: 0 16px; }
      .hero { padding: 18px 0 20px; }
      .brand { gap: 12px; }
      .brand img, .mark { width: 48px; height: 48px; border-radius: 10px; }
      .brand h1 { font-size: 20px; }
      .job { padding: 16px; }
      .job h3 { font-size: 16.5px; }
      ol.steps { padding: 16px 16px 2px; }
      .site-footer { padding-top: 28px; }
      .foot-grid { gap: 22px; padding-bottom: 24px; }
      .foot-name { font-size: 18px; }
    }
  `,
  template: `
    @if (loading) {
      <div class="center"><div class="spinner"></div></div>
    } @else if (notFound) {
      <div class="center"><h1>Application not found</h1><p>This link is not active. Please check the address.</p></div>
    } @else if (loadError || !app) {
      <div class="center"><h1>Could not load this application</h1><p>Please try again in a moment.</p>
        <button class="btn" (click)="load(slug)">Try again</button></div>
    } @else {
      <div class="site" [style.--accent]="colors.header" [style.--accent2]="colors.header2" [style.--on-header]="colors.onHeader"
           [style.--on-accent]="colors.onHeader" [style.--accent-text]="colors.accentText"
           [style.--footer-bg]="colors.footer" [style.--on-footer]="colors.onFooter">
        <header class="hero">
          <div class="wrap">
            <div class="brand">
              @if (logo) { <img [src]="logo" [alt]="app.name + ' logo'" /> } @else { <div class="mark">{{ app.name.charAt(0).toUpperCase() }}</div> }
              <div class="brand-text">
                <h1>{{ app.applicationName || app.name }}</h1>
                <p class="by">{{ app.name }}</p>
                @if (app.description) { <p class="desc">{{ app.description }}</p> }
              </div>
            </div>
          </div>
        </header>

        <main class="wrap layout">
          <section class="positions">
            <h2>Open positions <span class="count">{{ jobs.length }}</span></h2>
            @if (jobs.length === 0) {
              <div class="empty">There are no open positions right now. Please check back soon.</div>
            } @else {
              @for (j of jobs; track j.id) {
                <article class="job">
                  <div class="job-head">
                    <div><h3>{{ j.jobTitle }}</h3><div class="id">{{ j.jobId }}</div></div>
                    <app-pill [label]="j.priority" />
                  </div>
                  <div class="meta">
                    <span>📍 {{ j.location }}</span>
                    <span>{{ j.employmentType }}</span>
                    <span>{{ j.minExperience }}–{{ j.maxExperience }} years</span>
                    <span>{{ j.numberOfPositions }} position{{ j.numberOfPositions === 1 ? '' : 's' }}</span>
                    @if (j.department) { <span>{{ j.department }}</span> }
                  </div>
                  <div class="skills">@for (s of skills(j.requiredSkills); track s) { <span>{{ s }}</span> }</div>
                  <div class="job-desc"><h4>Job description</h4><p>{{ j.jobDescription }}</p></div>

                  @if (applied.has(j.id)) {
                    <div class="applied" role="status">✔ Your application has been submitted. We will contact you if your profile matches.</div>
                  } @else if (applyingId === j.id) {
                    <form class="apply" (ngSubmit)="submit(j)">
                      <h4>Apply for {{ j.jobTitle }}</h4>
                      @if (applyError) { <div class="err" role="alert">{{ applyError }}</div> }
                      <div class="row">
                        <div class="f"><label for="an-{{ j.id }}">Full name *</label>
                          <input id="an-{{ j.id }}" name="fullName" maxlength="150" autocomplete="name" [(ngModel)]="form.fullName" /></div>
                        <div class="f"><label for="ae-{{ j.id }}">Email *</label>
                          <input id="ae-{{ j.id }}" name="email" type="email" maxlength="150" autocomplete="email" [(ngModel)]="form.email" /></div>
                      </div>
                      <div class="row">
                        <div class="f"><label for="ap-{{ j.id }}">Phone</label>
                          <input id="ap-{{ j.id }}" name="phone" maxlength="30" autocomplete="tel" [(ngModel)]="form.phone" /></div>
                        <div class="f"><label for="ar-{{ j.id }}">Resume *</label>
                          <input id="ar-{{ j.id }}" name="resume" type="file" [accept]="accept" (change)="pick($event)" />
                          <div class="hint">PDF, DOC or DOCX, up to {{ maxMb }} MB.</div></div>
                      </div>
                      <button class="apply-btn" type="submit" [disabled]="submitting">{{ submitting ? 'Submitting…' : 'Submit application' }}</button>
                      <button class="apply-btn ghost" type="button" [disabled]="submitting" (click)="cancelApply()">Cancel</button>
                    </form>
                  } @else {
                    <button class="apply-btn" type="button" (click)="startApply(j)">Apply now</button>
                  }
                </article>
              }
            }
          </section>

          @if (app.stages.length > 0) {
            <section class="process">
              <h2>Hiring process</h2>
              <ol class="steps">
                @for (s of app.stages; track s.code; let i = $index) {
                  <li><span class="num">{{ i + 1 }}</span>
                    <div class="step-body"><div class="step-name">{{ s.name }}</div>@if (s.description) { <div class="step-desc">{{ s.description }}</div> }</div></li>
                }
              </ol>
            </section>
          }
        </main>

        <footer class="site-footer">
          <div class="wrap">
            <div class="foot-grid">
              @if (footer.companyName || footer.customText) {
                <div class="foot-col foot-brand">
                  @if (footer.companyName) { <strong class="foot-name">{{ footer.companyName }}</strong> }
                  @if (footer.customText) { <p class="foot-text">{{ footer.customText }}</p> }
                </div>
              }
              @if (footer.address) {
                <div class="foot-col">
                  <ul class="foot-list">
                    <li class="foot-item"><svg class="ico" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M20 10c0 6-8 12-8 12s-8-6-8-12a8 8 0 0 1 16 0Z"/><circle cx="12" cy="10" r="3"/></svg><address>{{ footer.address }}</address></li>
                  </ul>
                </div>
              }
              @if (footer.email || footer.phone || footer.website) {
                <div class="foot-col">
                  <ul class="foot-list">
                    @if (footer.email) { <li class="foot-item"><svg class="ico" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><rect x="2" y="4" width="20" height="16" rx="2"/><path d="m22 7-10 6L2 7"/></svg><a [href]="'mailto:' + footer.email"><span>{{ footer.email }}</span></a></li> }
                    @if (footer.phone) { <li class="foot-item"><svg class="ico" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M22 16.9v3a2 2 0 0 1-2.2 2 19.8 19.8 0 0 1-8.6-3.1 19.5 19.5 0 0 1-6-6A19.8 19.8 0 0 1 2.1 4.2 2 2 0 0 1 4.1 2h3a2 2 0 0 1 2 1.7c.1 1 .4 1.9.7 2.8a2 2 0 0 1-.5 2.1L8.1 9.9a16 16 0 0 0 6 6l1.3-1.3a2 2 0 0 1 2.1-.4c.9.3 1.8.6 2.8.7a2 2 0 0 1 1.7 2Z"/></svg><a [href]="'tel:' + footer.phoneDial"><span>{{ footer.phone }}</span></a></li> }
                    @if (footer.website) { <li class="foot-item"><svg class="ico" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><circle cx="12" cy="12" r="10"/><path d="M2 12h20"/><path d="M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10Z"/></svg><a [href]="footer.website" target="_blank" rel="noopener noreferrer"><span>{{ footer.websiteLabel }}</span></a></li> }
                  </ul>
                </div>
              }
            </div>

            <div class="foot-bar">
              <p class="foot-copy">{{ footer.copyright }}</p>
              <div class="foot-actions">
                @if (canEditFooter && !editingFooter) {
                  <button class="foot-edit-btn" type="button" (click)="editingFooter = true"><svg class="ico" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M12 20h9"/><path d="M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4Z"/></svg>Edit footer</button>
                }
                <button class="foot-edit-btn" type="button" (click)="logout()"><svg class="ico" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/><path d="m16 17 5-5-5-5"/><path d="M21 12H9"/></svg>Logout</button>
              </div>
            </div>

            @if (canEditFooter && editingFooter) {
              <div class="foot-editor">
                <app-footer-editor [slug]="slug" [footer]="app.footer" (saved)="onFooterSaved($event)" (cancel)="editingFooter = false" />
              </div>
            }
          </div>
        </footer>
      </div>
    }
  `,
})
export class PublicAppComponent {
  private service = inject(PublicAppService);
  private title = inject(Title);
  private auth = inject(AuthService);
  private router = inject(Router);

  slug = '';
  app?: PublicApp;
  jobs: PublicJob[] = [];
  loading = true;
  notFound = false;
  loadError = false;
  skills = skillList;

  // ---- footer editing (only for roles allowed by FOOTER_EDIT_ROLES in AuthService) ----
  editingFooter = false;
  get canEditFooter() { return this.auth.canEditFooter(this.slug); }

  onFooterSaved(footer: PublicFooter) {
    if (this.app) this.app = { ...this.app, footer }; // the footer on this page updates straight away
    this.editingFooter = false;
  }

  // ---- applying for a job ----
  accept = RESUME_ACCEPT;
  maxMb = RESUME_MAX_MB;
  applyingId: number | null = null;
  applied = new Set<number>();
  form = { fullName: '', email: '', phone: '' };
  file: File | null = null;
  submitting = false;
  applyError = '';

  startApply(job: PublicJob) {
    if (this.submitting) return;
    this.applyingId = job.id;
    this.form = { fullName: '', email: '', phone: '' };
    this.file = null; this.applyError = '';
  }

  cancelApply() {
    if (this.submitting) return;
    this.applyingId = null; this.applyError = '';
  }

  pick(event: Event) {
    this.file = (event.target as HTMLInputElement).files?.[0] ?? null;
  }

  submit(job: PublicJob) {
    if (this.submitting) return; // ignore a second click while the first request is running
    const name = this.form.fullName.trim();
    const email = this.form.email.trim();
    if (!name) { this.applyError = 'Please enter your full name.'; return; }
    if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email)) { this.applyError = 'Please enter a valid email address.'; return; }
    const fileProblem = resumeFileError(this.file);
    if (fileProblem || !this.file) { this.applyError = fileProblem ?? 'Please choose your resume.'; return; }

    const data = new FormData();
    data.append('fullName', name);
    data.append('email', email);
    if (this.form.phone.trim()) data.append('phone', this.form.phone.trim());
    data.append('resume', this.file);

    this.submitting = true; this.applyError = '';
    this.service.apply(this.slug, job.jobId, data).subscribe({
      next: () => { this.submitting = false; this.applied.add(job.id); this.applyingId = null; },
      error: (err: HttpErrorResponse) => { this.submitting = false; this.applyError = errorText(err); },
    });
  }

  /** Ends this client website's session only. The Admin Portal session is not touched. */
  logout() {
    this.auth.logout(this.slug);
    this.router.navigateByUrl(`/app/${encodeURIComponent(this.slug)}/login`);
  }

  constructor() {
    // Subscribing (not just reading the snapshot) keeps the page correct if the slug changes in the same tab.
    inject(ActivatedRoute).paramMap.subscribe((p) => this.load(p.get('slug') ?? ''));
  }

  load(slug: string) {
    this.slug = slug;
    this.loading = true; this.notFound = false; this.loadError = false; this.app = undefined; this.jobs = []; this.editingFooter = false;
    forkJoin({
      app: this.service.getApplication(slug),
      jobs: this.service.getOpenJobs(slug).pipe(catchError(() => of([] as PublicJob[]))), // jobs failing should not hide the page
    }).subscribe({
      next: ({ app, jobs }) => {
        this.app = app; this.jobs = jobs; this.loading = false;
        this.title.setTitle(app.applicationName || app.name);
      },
      error: (err: HttpErrorResponse) => {
        this.loading = false;
        if (err.status === 404) this.notFound = true; else this.loadError = true;
      },
    });
  }

  /** Header and footer colours. Records saved before colours existed fall back to their theme's preset. */
  get colors(): PortalColors { return portalColors(this.app); }

  /** Footer content with empty fields already removed, so the template only shows what is set. */
  get footer() {
    const a = this.app;
    const t = (v: string | null | undefined) => (v ?? '').trim();
    const website = t(a?.footer?.websiteUrl);
    const safeSite = /^https?:\/\//i.test(website) ? website : '';
    const phone = t(a?.footer?.phone);
    return {
      companyName: t(a?.footer?.companyName),
      address: t(a?.footer?.address),
      email: t(a?.footer?.contactEmail),
      phone,
      phoneDial: phone.replace(/[^\d+]/g, ''),
      website: safeSite,
      websiteLabel: safeSite.replace(/^https?:\/\//i, '').replace(/\/$/, ''),
      customText: t(a?.footer?.customText),
      // Nothing configured: a plain default line so the footer is never an empty coloured bar.
      copyright: t(a?.footer?.copyrightText) || `© ${new Date().getFullYear()} ${a?.name ?? ''}`.trim(),
    };
  }
  get logo() { return this.app?.logoUrl ? API_BASE_URL + this.app.logoUrl : null; }
}