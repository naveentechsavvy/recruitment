import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnDestroy, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { PreonboardingItem } from '../../../core/models/preonboarding.model';
import { SitePreonboardingService } from '../../../core/services/site-preonboarding.service';
import { siteSlug } from '../../../core/services/site-hiring.service';
import { errorText } from '../../../core/utils/http-error';

/**
 * What this client asks a new joiner for after the offer is accepted: which details, which documents,
 * which of them are required, and how many days the candidate has to submit.
 * Only Admin, HR BP and Recruiter get here. The server refuses everyone else as well.
 */
@Component({
  selector: 'app-site-preonboarding-settings',
  imports: [FormsModule],
  styles: `
    .days { max-width: 160px; }
    .section { margin: 26px 0 8px; }
    .check { display: flex; justify-content: center; }
    .check input { width: 18px; height: 18px; cursor: pointer; }
    th.c, td.c { text-align: center; width: 140px; }
    .actions { margin-top: 20px; display: flex; gap: 8px; }
  `,
  template: `
    <div class="page-head">
      <div><h1>Pre-onboarding</h1>
        <p>What a new joiner is asked for after they accept the offer. The documents they upload are checked by HR.</p></div>
    </div>

    @if (notice) { <div class="alert success" role="status">{{ notice }}</div> }
    @if (error) { <div class="alert error" role="alert">{{ error }}</div> }

    <div class="card card-body">
      @if (loading) { <div class="spinner"></div> }
      @else if (!loaded) {
        <div class="empty"><h3>Could not load the settings</h3><button class="btn" type="button" (click)="load()">Try again</button></div>
      } @else {
        <div class="field">
          <label for="days">Days the candidate has to submit</label>
          <input id="days" class="input days" type="number" min="1" max="60" step="1" name="days" [(ngModel)]="days" />
          <span class="hint">Between 1 and 60. The candidate gets reminders before this day.</span>
        </div>

        @for (group of groups; track group.title) {
          <h2 class="card-title section">{{ group.title }}</h2>
          <div class="table-wrap"><table class="table">
            <thead><tr><th>Item</th><th class="c">Ask for it</th><th class="c">Required</th></tr></thead>
            <tbody>
              @for (r of group.rows; track r.code) {
                <tr>
                  <td>{{ r.label }}</td>
                  <td class="c"><span class="check"><input type="checkbox" [name]="'e-' + r.code" [(ngModel)]="r.enabled"
                        (ngModelChange)="onEnabled(r)" [attr.aria-label]="'Ask for ' + r.label" /></span></td>
                  <td class="c"><span class="check"><input type="checkbox" [name]="'m-' + r.code" [(ngModel)]="r.mandatory"
                        [disabled]="!r.enabled" [attr.aria-label]="r.label + ' is required'" /></span></td>
                </tr>
              }
            </tbody>
          </table></div>
        }

        <div class="actions">
          <button class="btn primary" type="button" [disabled]="busy" (click)="save()">{{ busy ? 'Saving…' : 'Save settings' }}</button>
        </div>
      }
    </div>
  `,
})
export class SitePreonboardingSettingsComponent implements OnDestroy {
  private slug = siteSlug(inject(ActivatedRoute));
  private api = inject(SitePreonboardingService).forSite(this.slug);
  private timer?: ReturnType<typeof setTimeout>;

  rows: PreonboardingItem[] = [];
  days = 5;
  loading = true;
  loaded = false;
  busy = false;
  error = '';
  notice = '';

  constructor() { this.load(); }

  ngOnDestroy() { clearTimeout(this.timer); }

  get groups(): { title: string; rows: PreonboardingItem[] }[] {
    return [
      { title: 'Details to fill in', rows: this.rows.filter((r) => r.kind === 'DETAIL') },
      { title: 'Documents to upload', rows: this.rows.filter((r) => r.kind === 'DOCUMENT') },
    ];
  }

  load() {
    this.loading = true; this.error = '';
    this.api.getSettings().subscribe({
      next: (s) => {
        this.rows = s.items.map((i) => ({ ...i })); // a copy, so unsaved ticks never change the saved data
        this.days = s.submissionDays;
        this.loaded = true; this.loading = false;
      },
      error: (err: HttpErrorResponse) => { this.loading = false; this.loaded = false; this.error = errorText(err); },
    });
  }

  /** An item that is switched off cannot be required. */
  onEnabled(r: PreonboardingItem) {
    if (!r.enabled) r.mandatory = false;
  }

  save() {
    if (this.busy) return;
    const days = Number(this.days);
    if (!Number.isInteger(days) || days < 1 || days > 60) {
      this.error = 'Days to submit must be a whole number between 1 and 60.'; this.notice = '';
      return;
    }
    this.busy = true; this.error = ''; this.notice = '';
    this.api.saveSettings({ submissionDays: days, items: this.rows }).subscribe({
      next: (s) => {
        this.rows = s.items.map((i) => ({ ...i }));
        this.days = s.submissionDays;
        this.busy = false;
        this.notice = 'Settings saved.';
        clearTimeout(this.timer);
        this.timer = setTimeout(() => (this.notice = ''), 6000);
      },
      error: (err: HttpErrorResponse) => { this.busy = false; this.error = errorText(err); },
    });
  }
}
