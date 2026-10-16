import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute, Router } from '@angular/router';
import { API_BASE_URL } from '../../core/config/api.config';
import { portalColors } from '../../core/models/client-configuration.model';
import { PublicApp } from '../../core/models/public-app.model';
import { AuthService } from '../../core/services/auth.service';
import { PublicAppService } from '../../core/services/public-app.service';

/**
 * Login page of one client's application, at /app/:slug/login.
 * The slug in the address decides whose name, logo and colours are shown (loaded from the backend, nothing hardcoded).
 * The slug is also sent with the login, so the server gives the person THEIR role for THIS client, or refuses
 * the login if they have no role here. The same person can use several client portals with the same password.
 */
@Component({
  selector: 'app-client-login',
  imports: [ReactiveFormsModule],
  styles: `
    :host { display: block; min-height: 100vh; background: #f5f7fa; color: #1a2233; -webkit-font-smoothing: antialiased; }
    .center { min-height: 100vh; display: flex; flex-direction: column; align-items: center; justify-content: center; text-align: center; padding: 24px; }
    .center h1 { font-size: 24px; margin: 0 0 8px; } .center p { color: #667085; margin: 0 0 16px; }
    .band { background: linear-gradient(135deg, var(--accent), var(--accent2)); color: var(--on-accent); padding: 36px 16px 84px; }
    .brand { max-width: 440px; margin: 0 auto; display: flex; align-items: center; gap: 14px; }
    .brand img, .mark { flex: none; width: 56px; height: 56px; border-radius: 12px; background: #fff; object-fit: contain; padding: 5px; box-shadow: 0 2px 8px rgba(0, 0, 0, .18); }
    .mark { display: flex; align-items: center; justify-content: center; font-size: 24px; font-weight: 700; color: var(--accent-text); }
    .brand h1 { margin: 0; font-size: 21px; line-height: 1.25; }
    .brand p { margin: 2px 0 0; font-size: 13.5px; opacity: .88; }
    .card { position: relative; max-width: 440px; margin: -52px auto 40px; background: #fff; border: 1px solid #e3e8ef; border-radius: 14px; padding: 28px; box-shadow: 0 4px 16px rgba(16, 24, 40, .08); }
    .card-wrap { padding: 0 16px; }
    h2 { margin: 0 0 4px; font-size: 20px; }
    .sub { margin: 0 0 20px; color: #667085; }
    .pw { position: relative; }
    .pw .input { padding-right: 68px; }
    .toggle { position: absolute; top: 50%; right: 8px; transform: translateY(-50%); border: 0; background: transparent; color: var(--accent-text); font: inherit; font-size: 13px; font-weight: 600; padding: 6px 8px; border-radius: 6px; cursor: pointer; }
    .toggle:hover { background: #f1f4f8; }
    .toggle:focus-visible, .go:focus-visible { outline: 2px solid var(--accent-text); outline-offset: 2px; }
    .go { width: 100%; margin-top: 4px; padding: 11px 16px; border: 0; border-radius: 8px; background: var(--accent); color: var(--on-accent); font: inherit; font-weight: 600; cursor: pointer; }
    .go:hover:not(:disabled) { filter: brightness(.93); }
    .go:disabled { opacity: .6; cursor: not-allowed; }
    .note { margin: 18px 0 0; text-align: center; font-size: 12.5px; color: #667085; }
    .try { margin-top: 4px; }
  `,
  template: `
    @if (loading) {
      <div class="center"><div class="spinner" aria-label="Loading"></div></div>
    } @else if (notFound) {
      <div class="center"><h1>Application not found</h1><p>This link is not active. Please check the address.</p></div>
    } @else if (loadError || !app) {
      <div class="center"><h1>Could not load this page</h1><p>Please try again in a moment.</p>
        <button class="btn try" type="button" (click)="load(slug)">Try again</button></div>
    } @else {
      <div [style.--accent]="colors.header" [style.--accent2]="colors.header2" [style.--on-accent]="colors.onHeader"
           [style.--accent-text]="colors.accentText" [style.--primary]="colors.accentText">
        <header class="band">
          <div class="brand">
            @if (logo) { <img [src]="logo" [alt]="app.name + ' logo'" /> } @else { <div class="mark" aria-hidden="true">{{ app.name.charAt(0).toUpperCase() }}</div> }
            <div>
              <h1>{{ app.applicationName || app.name }}</h1>
              <p>{{ app.name }}</p>
            </div>
          </div>
        </header>

        <div class="card-wrap">
          <main class="card">
            <h2>Sign in</h2>
            <p class="sub">Use your work email and password to continue.</p>

            @if (error) { <div class="alert error" role="alert">{{ error }}</div> }

            <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
              <div class="field">
                <label for="email">Email address <span class="req">*</span></label>
                <input id="email" class="input" type="email" formControlName="email" autocomplete="username"
                       [class.invalid]="showError('email')" [attr.aria-invalid]="showError('email')" aria-describedby="email-error" />
                @if (showError('email')) { <span id="email-error" class="error">{{ emailMessage }}</span> }
              </div>

              <div class="field">
                <label for="password">Password <span class="req">*</span></label>
                <div class="pw">
                  <input id="password" class="input" formControlName="password" autocomplete="current-password"
                         [type]="show ? 'text' : 'password'" [class.invalid]="showError('password')"
                         [attr.aria-invalid]="showError('password')" aria-describedby="password-error" />
                  <button class="toggle" type="button" (click)="show = !show" [attr.aria-pressed]="show"
                          [attr.aria-label]="show ? 'Hide password' : 'Show password'">{{ show ? 'Hide' : 'Show' }}</button>
                </div>
                @if (showError('password')) { <span id="password-error" class="error">Please enter your password.</span> }
              </div>

              <button class="go" type="submit" [disabled]="submitting">{{ submitting ? 'Signing in…' : 'Login' }}</button>
            </form>

            <p class="note">Having trouble signing in? Please contact your administrator.</p>
          </main>
        </div>
      </div>
    }
  `,
})
export class ClientLoginComponent {
  private service = inject(PublicAppService);
  private auth = inject(AuthService);
  private router = inject(Router);
  private title = inject(Title);
  private fb = inject(FormBuilder);

  slug = '';
  app?: PublicApp;
  loading = true;
  notFound = false;
  loadError = false;

  show = false;
  submitting = false;
  error = '';

  form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required]],
  });

  constructor() {
    // Subscribing (not reading a snapshot) keeps the page right if the slug changes in the same tab.
    inject(ActivatedRoute).paramMap
      .pipe(takeUntilDestroyed())
      .subscribe((p) => this.load(p.get('slug') ?? ''));
  }

  get colors() {
    return portalColors(this.app);
  }

  get logo() {
    return this.app?.logoUrl ? API_BASE_URL + this.app.logoUrl : null;
  }

  load(slug: string) {
    this.slug = slug;
    this.loading = true;
    this.notFound = false;
    this.loadError = false;
    this.app = undefined;
    this.error = '';

    this.service.getApplication(slug).subscribe({
      next: (app) => {
        this.app = app;
        this.loading = false;
        this.title.setTitle(`Login · ${app.applicationName || app.name}`);
      },
      error: (err: HttpErrorResponse) => {
        this.loading = false;
        if (err.status === 404) {
          this.notFound = true;
        } else {
          this.loadError = true;
        }
      },
    });
  }

  showError(name: 'email' | 'password'): boolean {
    const c = this.form.controls[name];
    return c.invalid && (c.touched || c.dirty);
  }

  get emailMessage(): string {
    return this.form.controls.email.hasError('required')
      ? 'Please enter your email address.'
      : 'Please enter a valid email address.';
  }

  submit() {
    if (this.submitting) return;

    this.form.markAllAsTouched();
    if (this.form.invalid) return;

    this.error = '';
    this.submitting = true;

    const { email, password } = this.form.getRawValue();

    // Send the client slug so the backend resolves this user's role for this client.
    this.auth.login(email.trim(), password, this.slug).subscribe({
      next: () => {
        this.submitting = false;

        // FIX: return to the same client's site instead of the Admin Dashboard.
        this.router.navigateByUrl(`/app/${encodeURIComponent(this.slug)}`);
      },
      error: (err: HttpErrorResponse) => {
        this.auth.logout(this.slug); // this client's session only, never the Admin Portal's
        this.submitting = false;
        this.error = this.loginMessage(err);
      },
    });
  }

   private loginMessage(err: HttpErrorResponse): string {
    if (err.status === 0) {
      return 'Cannot reach the server. Please check your connection and try again.';
    }

    // Wrong password, no access to this client, or an admin account: all show the same simple message.
    if (err.status === 401 || err.status === 403) {
      return 'Invalid email or password.';
    }

    if (err.status === 400) {
      return 'Please enter a valid email address and your password.';
    }

    return 'Something went wrong while signing in. Please try again.';
  }
}
