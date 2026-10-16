import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';

const API_URL = 'http://localhost:8080/api';

interface InvitationInfo {
  email: string;
  clientName: string | null;
  logoUrl: string | null;
  clientSlug: string | null;
}

@Component({
  selector: 'app-set-password',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterLink],
  template: `
    <div class="setup-page">
      <form class="setup-card" (ngSubmit)="submit()">
        <div class="brand" *ngIf="info">
          <img *ngIf="info.logoUrl && !logoFailed" [src]="info.logoUrl" [alt]="(info.clientName || 'Company') + ' logo'" (error)="logoFailed = true">
          <div class="mark" *ngIf="!info.logoUrl || logoFailed" aria-hidden="true">{{ initial }}</div>
          <div class="brand-name" *ngIf="info.clientName">{{ info.clientName }}</div>
        </div>

        <h1>Create your account</h1>
        <p *ngIf="loading">Checking your invitation…</p>
        <p *ngIf="!loading && info">
          Set a password to activate your account<ng-container *ngIf="info.clientName"> for {{ info.clientName }}</ng-container>.
        </p>

        <label>Email address
          <input type="email" name="email" [value]="info?.email ?? ''" readonly>
        </label>
        <label>Password
          <div class="pw">
            <input [type]="showPassword ? 'text' : 'password'" name="password" [(ngModel)]="password" required minlength="8" maxlength="72"
                   autocomplete="new-password" [disabled]="!info || !!success">
            <button type="button" class="toggle" (click)="showPassword = !showPassword"
                    [attr.aria-label]="showPassword ? 'Hide password' : 'Show password'" [attr.aria-pressed]="showPassword">
              <svg *ngIf="!showPassword" viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M1 12s4-7 11-7 11 7 11 7-4 7-11 7S1 12 1 12z"/><circle cx="12" cy="12" r="3"/></svg>
              <svg *ngIf="showPassword" viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M17.94 17.94A10.94 10.94 0 0 1 12 19c-7 0-11-7-11-7a19.8 19.8 0 0 1 5.06-5.94"/><path d="M9.9 4.24A10.9 10.9 0 0 1 12 4c7 0 11 7 11 7a19.7 19.7 0 0 1-3.17 4.19"/><path d="M14.12 14.12A3 3 0 1 1 9.88 9.88"/><line x1="1" y1="1" x2="23" y2="23"/></svg>
            </button>
          </div>
        </label>
        <label>Confirm password
          <div class="pw">
            <input [type]="showConfirm ? 'text' : 'password'" name="confirmPassword" [(ngModel)]="confirmPassword" required minlength="8" maxlength="72"
                   autocomplete="new-password" [disabled]="!info || !!success">
            <button type="button" class="toggle" (click)="showConfirm = !showConfirm"
                    [attr.aria-label]="showConfirm ? 'Hide password' : 'Show password'" [attr.aria-pressed]="showConfirm">
              <svg *ngIf="!showConfirm" viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M1 12s4-7 11-7 11 7 11 7-4 7-11 7S1 12 1 12z"/><circle cx="12" cy="12" r="3"/></svg>
              <svg *ngIf="showConfirm" viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M17.94 17.94A10.94 10.94 0 0 1 12 19c-7 0-11-7-11-7a19.8 19.8 0 0 1 5.06-5.94"/><path d="M9.9 4.24A10.9 10.9 0 0 1 12 4c7 0 11 7 11 7a19.7 19.7 0 0 1-3.17 4.19"/><path d="M14.12 14.12A3 3 0 1 1 9.88 9.88"/><line x1="1" y1="1" x2="23" y2="23"/></svg>
            </button>
          </div>
        </label>

        <div class="error" *ngIf="error" role="alert">{{ error }}</div>
        <div class="success" *ngIf="success" role="status">{{ success }}</div>

        <button type="submit" [disabled]="saving || !!success || !info">{{ saving ? 'Creating…' : 'Create Account' }}</button>
        <a [routerLink]="loginLink">{{ success ? 'Go to login' : 'Back to login' }}</a>
      </form>
    </div>
  `,
  styles: [`
    .setup-page{min-height:100vh;display:grid;place-items:center;background:#fdf3e3;padding:24px;box-sizing:border-box}
    .setup-card{width:min(100%,420px);box-sizing:border-box;background:#fff;border:1px solid #eee1d5;border-radius:16px;padding:30px;box-shadow:0 12px 32px #50301a12;display:flex;flex-direction:column;gap:15px}
    .brand{display:flex;flex-direction:column;align-items:center;gap:10px;margin-bottom:4px}
    .brand img,.mark{width:64px;height:64px;border-radius:14px;object-fit:contain;background:#fff;border:1px solid #eee1d5;padding:5px;box-sizing:border-box}
    .mark{display:flex;align-items:center;justify-content:center;background:#fff0e9;color:#f4623a;font-size:28px;font-weight:700}
    .brand-name{font-size:17px;font-weight:700;color:#35271f;text-align:center}
    h1{font-size:24px;color:#35271f;margin:0;text-align:center}p{font-size:14px;color:#776b64;margin:0 0 6px;line-height:1.5;text-align:center}
    label{display:flex;flex-direction:column;gap:7px;color:#51433a;font-size:13px;font-weight:600}
    input{border:1px solid #ddd1c7;border-radius:8px;padding:12px;font:inherit}
    input[readonly]{background:#f7f2ed;color:#776b64}
    button{border:0;border-radius:8px;padding:12px;background:#f4623a;color:white;font-weight:700;cursor:pointer}
    button:disabled{opacity:.55;cursor:not-allowed}a{text-align:center;color:#a84327;font-size:13px}
    .pw{position:relative;display:flex}.pw input{flex:1;min-width:0;padding-right:46px}
    .pw .toggle{position:absolute;top:50%;right:6px;transform:translateY(-50%);width:34px;height:34px;padding:0;display:flex;align-items:center;justify-content:center;background:transparent;color:#776b64;border-radius:6px}
    .pw .toggle:hover{background:#f7f2ed;color:#35271f}.pw .toggle:focus-visible{outline:2px solid #f4623a;outline-offset:1px}
    .error,.success{font-size:13px;padding:10px;border-radius:8px}.error{background:#fff0ed;color:#a83220}.success{background:#edf8ef;color:#236331}
  `],
})
export class SetPasswordComponent {
  private http = inject(HttpClient);
  private route = inject(ActivatedRoute);
  token = this.route.snapshot.queryParamMap.get('token') ?? '';
  info?: InvitationInfo;
  logoFailed = false;
  loading = true;
  password = '';
  showPassword = false;
  showConfirm = false;
  confirmPassword = '';
  error = '';
  success = '';
  saving = false;

  constructor() {
    if (!this.token) {
      this.loading = false;
      this.error = 'This invitation link is missing a token. Please use the link from your invitation email.';
      return;
    }
    // Shows who is invited and to which client. This does not use up the link.
    this.http.get<InvitationInfo>(`${API_URL}/auth/invitation`, { params: { token: this.token } }).subscribe({
      next: (info) => { this.info = info; this.loading = false; },
      error: (err) => {
        this.loading = false;
        this.error = err?.error?.message || 'This invitation link is invalid or has expired. Ask your administrator to send a new invitation.';
      },
    });
  }

  get initial(): string {
    return (this.info?.clientName ?? 'V').charAt(0).toUpperCase();
  }

  /** After the account exists, the person goes to their client's own login page (or the admin login when there is none). */
  get loginLink(): string[] {
    return this.info?.clientSlug ? ['/app', this.info.clientSlug, 'login'] : ['/admin/login'];
  }

  submit(): void {
    this.error = '';
    if (!this.token || !this.info) { this.error = 'This invitation link is not valid. Please use the link from your invitation email.'; return; }
    if (this.password.length < 8 || this.password.length > 72) { this.error = 'Password must be between 8 and 72 characters.'; return; }
    if (this.password !== this.confirmPassword) { this.error = 'Passwords do not match.'; return; }
    this.saving = true;
    this.http.post<{ message: string }>(`${API_URL}/auth/accept-invitation`, { token: this.token, password: this.password }).subscribe({
      next: response => { this.success = response.message || 'Account created. You can now log in.'; this.saving = false; },
      error: err => { this.error = err?.error?.message || 'This invitation link is invalid or has expired. Ask your administrator to send a new invitation.'; this.saving = false; },
    });
  }
}