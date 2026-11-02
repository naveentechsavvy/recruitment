import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { OfferPublicView, PublicOfferService } from '../../core/services/public-offer.service';
import { errorText } from '../../core/utils/http-error';

type Choice = 'ACCEPT' | 'DECLINE';

/**
 * The page behind the Accept Offer / Reject Offer buttons of the offer email. No login.
 * The two buttons of the email only open this page (with the choice ticked). Nothing is decided until
 * the candidate presses Confirm here, so an email scanner opening the link can never answer for them.
 */
@Component({
  selector: 'app-offer-response',
  imports: [DatePipe, FormsModule],
  styles: `
    :host { display: block; min-height: 100vh; background: #f6f3ef; padding: 32px 16px; box-sizing: border-box; }
    .wrap { max-width: 560px; margin: 0 auto; }
    .brand { text-align: center; font-weight: 700; font-size: 14px; letter-spacing: .08em; text-transform: uppercase; color: #776b64; margin-bottom: 16px; }
    .box { background: #fff; border: 1px solid #e8e1d9; border-radius: 14px; padding: 28px; box-shadow: 0 8px 24px rgba(53, 39, 31, .06); }
    h1 { margin: 0 0 6px; font-size: 24px; color: #35271f; }
    p { color: #4b3d35; line-height: 1.55; margin: 8px 0; }
    .muted { color: #776b64; font-size: 13px; }
    .job { background: #faf7f3; border: 1px solid #eee6dd; border-radius: 10px; padding: 12px 16px; margin: 16px 0; }
    .job strong { display: block; font-size: 17px; color: #35271f; }
    .choices { display: flex; gap: 12px; flex-wrap: wrap; margin-top: 18px; }
    button { font: inherit; font-weight: 700; border-radius: 8px; padding: 12px 26px; cursor: pointer; border: 1px solid transparent; }
    button:disabled { opacity: .6; cursor: not-allowed; }
    .accept { background: #16a34a; color: #fff; }
    .reject { background: #fff; color: #b42318; border-color: #b42318; }
    .plain { background: #fff; color: #35271f; border-color: #d6cdc3; }
    .confirm { margin-top: 18px; padding: 16px; border: 1px solid #e8e1d9; border-radius: 10px; background: #faf7f3; }
    textarea { width: 100%; min-height: 80px; box-sizing: border-box; margin-top: 8px; padding: 10px; font: inherit; border: 1px solid #d6cdc3; border-radius: 8px; }
    .error { color: #b42318; margin-top: 10px; }
    .done { text-align: center; }
    .big { font-size: 40px; line-height: 1; margin-bottom: 8px; }
  `,
  template: `
    <div class="wrap">
      <div class="brand">{{ view?.companyName || 'Job offer' }}</div>
      <div class="box">
        @if (loading) {
          <p>Loading your offer…</p>
        } @else if (!view) {
          <h1>This link is not valid</h1>
          <p>{{ error || 'The offer link could not be found. Please check that you opened the full link from the email, or contact the HR team.' }}</p>
        } @else if (result) {
          <div class="done">
            <div class="big">{{ result === 'ACCEPT' ? '🎉' : '✔' }}</div>
            @if (result === 'ACCEPT') {
              <h1>Thank you, {{ view.candidateName }}!</h1>
              <p>You have accepted the offer for <strong>{{ view.jobTitle }}</strong>. The HR team has been told and will contact you with the next steps.</p>
            } @else {
              <h1>Your answer is recorded</h1>
              <p>You have rejected the offer for <strong>{{ view.jobTitle }}</strong>. The HR team has been told. We wish you all the best.</p>
            }
          </div>
        } @else if (!view.canRespond) {
          <h1>{{ closedTitle }}</h1>
          <p>{{ closedText }}</p>
        } @else {
          <h1>Hello {{ view.candidateName }}</h1>
          <p>You have an offer waiting for your answer.</p>
          <div class="job">
            <strong>{{ view.jobTitle }}</strong>
            @if (view.companyName) { <span class="muted">{{ view.companyName }}</span> }
          </div>
          <p class="muted">Your offer letter is attached to the email we sent you. Please read it before you answer. This link works until {{ view.expiresAt | date: 'longDate' }}.</p>

          @if (!choice) {
            <div class="choices">
              <button type="button" class="accept" (click)="pick('ACCEPT')">Accept Offer</button>
              <button type="button" class="reject" (click)="pick('DECLINE')">Reject Offer</button>
            </div>
          } @else {
            <div class="confirm">
              @if (choice === 'ACCEPT') {
                <p><strong>Please confirm that you accept this offer.</strong></p>
              } @else {
                <p><strong>Please confirm that you reject this offer.</strong></p>
                <label class="muted" for="why">Reason (optional)</label>
                <textarea id="why" name="why" maxlength="500" [(ngModel)]="reason" placeholder="You can tell us why, if you like."></textarea>
              }
              @if (error) { <div class="error" role="alert">{{ error }}</div> }
              <div class="choices">
                <button type="button" [class.accept]="choice === 'ACCEPT'" [class.reject]="choice === 'DECLINE'" [disabled]="busy" (click)="confirm()">
                  {{ busy ? 'Saving…' : choice === 'ACCEPT' ? 'Yes, accept the offer' : 'Yes, reject the offer' }}
                </button>
                <button type="button" class="plain" [disabled]="busy" (click)="back()">Go back</button>
              </div>
            </div>
          }
        }
      </div>
    </div>
  `,
})
export class OfferResponseComponent {
  private route = inject(ActivatedRoute);
  private offers = inject(PublicOfferService);
  private token = this.route.snapshot.paramMap.get('token') ?? '';

  view?: OfferPublicView;
  loading = true;
  busy = false;
  error = '';
  /** The button the candidate chose. Nothing is sent until Confirm. */
  choice: Choice | null = null;
  reason = '';
  /** Set after the answer was saved. */
  result: Choice | null = null;

  constructor() {
    // The email buttons add ?action=accept or ?action=decline. It only opens the confirm box. It never answers.
    const preset = (this.route.snapshot.queryParamMap.get('action') ?? '').toLowerCase();

    this.offers.view(this.token).subscribe({
      next: (v) => {
        this.view = v;
        this.loading = false;
        if (v.canRespond && (preset === 'accept' || preset === 'decline')) {
          this.choice = preset === 'accept' ? 'ACCEPT' : 'DECLINE';
        }
      },
      error: (err: HttpErrorResponse) => {
        this.loading = false;
        this.error = err.status === 404 ? '' : errorText(err);
      },
    });
  }

  get closedTitle(): string {
    switch (this.view?.status) {
      case 'ACCEPTED': return 'You accepted this offer';
      case 'DECLINED': return 'You rejected this offer';
      case 'EXPIRED': return 'This offer link has expired';
      default: return 'This offer is no longer open';
    }
  }

  get closedText(): string {
    switch (this.view?.status) {
      case 'ACCEPTED': return 'Your acceptance is already recorded. The HR team will contact you with the next steps.';
      case 'DECLINED': return 'Your answer is already recorded. If you changed your mind, please contact the HR team.';
      case 'EXPIRED': return 'Please contact the HR team and ask them to send you a new link.';
      default: return 'Please contact the HR team if you have any questions.';
    }
  }

  pick(choice: Choice) { this.choice = choice; this.error = ''; }

  back() { this.choice = null; this.error = ''; }

  confirm() {
    if (!this.choice || this.busy) return;
    this.busy = true; this.error = '';
    const decision = this.choice;
    this.offers.respond(this.token, decision, decision === 'DECLINE' ? this.reason.trim() : '').subscribe({
      next: (v) => { this.busy = false; this.view = v; this.result = decision; },
      error: (err: HttpErrorResponse) => { this.busy = false; this.error = errorText(err); },
    });
  }
}
