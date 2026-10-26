import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, HostListener, OnDestroy, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Observable, switchMap } from 'rxjs';
import {
  Candidate, CandidateStage, ScreeningAction, ScreeningInput, StageDecision, TimelineEvent, statusLabel,
} from '../../../core/models/candidate.model';
import { RESUME_ACCEPT, RESUME_MAX_MB, resumeFileError } from '../../../core/config/upload.config';
import { AuthService } from '../../../core/services/auth.service';
import { SiteCandidateService } from '../../../core/services/site-candidate.service';
import { siteSlug } from '../../../core/services/site-hiring.service';
import { errorText } from '../../../core/utils/http-error';
import { PillComponent } from '../../../shared/components/pill/pill.component';

type FieldName = 'totalExperience' | 'relevantExperience' | 'currentCtc' | 'expectedCtc' | 'noticePeriodDays' | 'skills' | 'remarks';

/**
 * One candidate: details, resume, recruiter screening, stages, remarks and the full history.
 * The server tells us what the logged-in user may do (canScreen, canRemark, stage.canEdit),
 * and it checks every rule again. The checks here only give the recruiter quick, clear messages.
 */
@Component({
  selector: 'app-site-candidate-details',
  imports: [RouterLink, DatePipe, FormsModule, PillComponent],
  styles: `
    .grid { display: grid; grid-template-columns: 1fr 2fr; gap: 20px; align-items: start; }
    @media (max-width: 900px) { .grid { grid-template-columns: 1fr; } }
    .col { display: flex; flex-direction: column; gap: 20px; }
    .stage { border: 1px solid #e5e7eb; border-radius: 10px; padding: 12px 14px; margin-bottom: 10px; }
    .stage.current { border-color: var(--primary); background: var(--primary-soft); }
    .stage-head { display: flex; justify-content: space-between; align-items: center; gap: 8px; }
    .meta { color: #6b7280; font-size: 12px; margin-top: 4px; }
    .notes { white-space: pre-wrap; margin: 8px 0 0; }
    textarea { width: 100%; min-height: 70px; margin-top: 8px; box-sizing: border-box; }
    .actions { margin-top: 8px; display: flex; gap: 8px; flex-wrap: wrap; }
    .view-only { color: #6b7280; font-size: 12px; margin-top: 6px; }
    .two { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
    @media (max-width: 600px) { .two { grid-template-columns: 1fr; } }
    .two .field { margin-bottom: 12px; }
    .tl { border-left: 2px solid #e5e7eb; margin: 8px 0 0 6px; padding-left: 16px; }
    .tl-item { position: relative; margin-bottom: 14px; }
    .tl-item::before { content: ''; position: absolute; left: -22px; top: 5px; width: 10px; height: 10px; border-radius: 50%; background: var(--primary); }
    .tl-item.remark::before { background: #f59e0b; }
    .tl-title { font-weight: 600; }
    .tl-details { white-space: pre-wrap; margin: 2px 0 0; }
    .chips { display: flex; flex-wrap: wrap; gap: 6px; }
    .chip { background: var(--primary-soft); color: var(--text); border-radius: 999px; padding: 2px 10px; font-size: 12px; font-weight: 600; }
    .resume-box { display: flex; flex-direction: column; gap: 8px; }
    .resume-name { word-break: break-all; font-weight: 500; }
    .banner { padding: 12px 16px; border-radius: 8px; margin-bottom: 20px; border: 1px solid; }
    .banner.warn { background: #fffbeb; border-color: #fde68a; color: #92400e; }
    .banner.bad { background: #fef2f2; border-color: #fecaca; color: #991b1b; }
    .banner.ok { background: #f0fdf4; border-color: #bbf7d0; color: #166534; }
    .backdrop { position: fixed; inset: 0; background: rgba(17, 24, 39, .55); display: grid; place-items: center; padding: 16px; z-index: 50; }
    .dialog { background: #fff; border-radius: 12px; padding: 24px; width: 100%; max-width: 460px; box-shadow: 0 20px 40px rgba(0, 0, 0, .25); }
    .dialog h3 { margin: 0 0 6px; font-size: 18px; }
    .dialog p { margin: 0 0 12px; color: #667085; }
  `,
  template: `
    @if (loading) { <div class="spinner"></div> }
    @else if (!candidate) {
      <div class="card empty"><h3>{{ error || 'Candidate not found' }}</h3>
        <a class="btn" [routerLink]="['/app', slug, 'manage', 'requirements']">Back to Requirements</a></div>
    } @else {
      <div class="page-head">
        <div><h1>{{ candidate.fullName }}</h1>
          <p>Application #{{ candidate.id }} · {{ candidate.jobId }} · {{ candidate.jobTitle }} ·
            <app-pill [label]="label(candidate.status)" /> <app-pill [label]="'Screening: ' + label(candidate.screeningStatus)" /></p></div>
        <div><a class="btn" [routerLink]="['/app', slug, 'manage', 'requirements', candidate.requirementId, 'candidates']">← Candidates</a></div>
      </div>

      @if (notice) { <div class="alert success" role="status">{{ notice }}</div> }
      @if (error) { <div class="alert error" role="alert">{{ error }}</div> }

      @if (candidate.status === 'REJECTED') {
        <div class="banner bad">This candidate was rejected. The decision is final and no further screening or stage changes are possible.</div>
      } @else if (candidate.status === 'ON_HOLD') {
        <div class="banner warn">This candidate is on hold and cannot move to the next stage. Choose Proceed or Reject when you are ready.</div>
      } @else if (candidate.screeningStatus === 'PROCEEDED') {
        <div class="banner ok">Screening is complete. The candidate is ready for Hiring Manager Review.</div>
      }

      <div class="grid">
        <div class="col">
          <div class="card card-body">
            <h2 class="card-title">Profile</h2>
            <dl>
              <dt>Email</dt><dd>{{ candidate.email }}</dd>
              <dt>Phone</dt><dd>{{ candidate.phone || '—' }}</dd>
              <dt>Applied</dt><dd>{{ candidate.appliedAt | date: 'medium' }}</dd>
              <dt>Job requirement</dt><dd>{{ candidate.jobId }} · {{ candidate.jobTitle }}</dd>
              <dt>Current Stage</dt><dd>{{ candidate.currentStageName || '—' }}</dd>
              <dt>Total Experience</dt><dd>{{ candidate.totalExperience ?? '—' }}{{ candidate.totalExperience !== null ? ' years' : '' }}</dd>
              <dt>Relevant Experience</dt><dd>{{ candidate.relevantExperience ?? '—' }}{{ candidate.relevantExperience !== null ? ' years' : '' }}</dd>
              <dt>Current CTC</dt><dd>{{ candidate.currentCtc ?? '—' }}</dd>
              <dt>Expected CTC</dt><dd>{{ candidate.expectedCtc ?? '—' }}</dd>
              <dt>Notice Period</dt><dd>{{ candidate.noticePeriodDays ?? '—' }}{{ candidate.noticePeriodDays !== null ? ' days' : '' }}</dd>
              <dt>Skills</dt>
              <dd>@if (skillList.length) { <div class="chips">@for (k of skillList; track k) { <span class="chip">{{ k }}</span> }</div> } @else { — }</dd>
            </dl>
          </div>

          <div class="card card-body">
            <h2 class="card-title">Resume</h2>
            <div class="resume-box">
              @if (candidate.hasResume) {
                <span class="resume-name">{{ candidate.resumeName }}</span>
                <span class="hint">Uploaded</span>
                <div class="actions">
                  <button class="btn sm" type="button" [disabled]="fileBusy" (click)="openResume()">Open</button>
                  <button class="btn sm" type="button" [disabled]="fileBusy" (click)="downloadResume()">Download</button>
                </div>
              } @else {
                <span class="resume-name">No resume uploaded</span>
                <span class="hint">A resume is required before the candidate can Proceed.</span>
              }
              @if (canReplace) {
                <div class="actions">
                  <input #picker type="file" hidden [accept]="accept" (change)="replaceResume($event)" />
                  <button class="btn sm" type="button" [disabled]="busy || fileBusy" (click)="picker.click()">
                    {{ candidate.hasResume ? 'Replace resume' : 'Upload resume' }}
                  </button>
                </div>
                <span class="hint">PDF, DOC or DOCX, up to {{ maxMb }} MB.</span>
              }
            </div>
          </div>

          @if (candidate.canScreen && !viewOnly) {
            <div class="card card-body">
              <h2 class="card-title">Screening</h2>
              @if (candidate.missingScreeningFields.length > 0) {
                <div class="alert error">Still needed before Proceed: {{ candidate.missingScreeningFields.join(', ') }}.</div>
              }
              <div class="two">
                <div class="field"><label for="te">Total experience (years) <span class="req">*</span></label>
                  <input id="te" class="input" [class.invalid]="errs.totalExperience" type="number" min="0" max="80" step="0.1" name="te" [(ngModel)]="screening.totalExperience" (ngModelChange)="revalidate()" />
                  @if (errs.totalExperience) { <span class="error">{{ errs.totalExperience }}</span> }</div>
                <div class="field"><label for="re">Relevant experience (years)</label>
                  <input id="re" class="input" [class.invalid]="errs.relevantExperience" type="number" min="0" max="80" step="0.1" name="re" [(ngModel)]="screening.relevantExperience" (ngModelChange)="revalidate()" />
                  @if (errs.relevantExperience) { <span class="error">{{ errs.relevantExperience }}</span> }</div>
                <div class="field"><label for="cc">Current CTC <span class="req">*</span></label>
                  <input id="cc" class="input" [class.invalid]="errs.currentCtc" type="number" min="0" name="cc" [(ngModel)]="screening.currentCtc" (ngModelChange)="revalidate()" />
                  @if (errs.currentCtc) { <span class="error">{{ errs.currentCtc }}</span> }</div>
                <div class="field"><label for="ec">Expected CTC <span class="req">*</span></label>
                  <input id="ec" class="input" [class.invalid]="errs.expectedCtc" type="number" min="0" name="ec" [(ngModel)]="screening.expectedCtc" (ngModelChange)="revalidate()" />
                  @if (errs.expectedCtc) { <span class="error">{{ errs.expectedCtc }}</span> }</div>
                <div class="field"><label for="np">Notice period (days) <span class="req">*</span></label>
                  <input id="np" class="input" [class.invalid]="errs.noticePeriodDays" type="number" min="0" max="365" step="1" name="np" [(ngModel)]="screening.noticePeriodDays" (ngModelChange)="revalidate()" />
                  @if (errs.noticePeriodDays) { <span class="error">{{ errs.noticePeriodDays }}</span> }</div>
                <div class="field"><label for="sk">Skills <span class="req">*</span></label>
                  <input id="sk" class="input" [class.invalid]="errs.skills" name="sk" maxlength="500" placeholder="Java, Spring Boot, Angular" [(ngModel)]="screening.skills" (ngModelChange)="revalidate()" />
                  @if (errs.skills) { <span class="error">{{ errs.skills }}</span> }</div>
              </div>
              <div class="field"><label for="rm">Screening notes (optional, added to the history when you save)</label>
                <textarea id="rm" class="input" [class.invalid]="errs.remarks" name="rm" maxlength="1000" [(ngModel)]="screening.remarks" (ngModelChange)="revalidate()"></textarea>
                @if (errs.remarks) { <span class="error">{{ errs.remarks }}</span> }</div>
              <div class="actions">
                <button class="btn" type="button" [disabled]="busy" (click)="saveScreening()">{{ busy ? 'Working…' : 'Save details' }}</button>
                <button class="btn primary" type="button" [disabled]="busy" (click)="openDialog('PROCEED')">Proceed</button>
                @if (candidate.status !== 'ON_HOLD') {
                  <button class="btn" type="button" [disabled]="busy" (click)="openDialog('ON_HOLD')">On Hold</button>
                }
                <button class="btn danger" type="button" [disabled]="busy" (click)="openDialog('REJECT')">Reject</button>
              </div>
            </div>
          }
        </div>

        <div class="col">
          <div class="card card-body">
            <h2 class="card-title">Stages</h2>
            @for (s of candidate.stages; track s.code) {
              <div class="stage" [class.current]="s.current">
                <div class="stage-head">
                  <strong>{{ s.name }}</strong>
                  <span>@if (s.current) { <app-pill label="Current" /> } <app-pill [label]="label(s.decision)" /></span>
                </div>
                @if (s.notes) { <p class="notes">{{ s.notes }}</p> }
                @if (s.updatedBy) { <div class="meta">{{ s.updatedBy }} · {{ s.updatedAt | date: 'medium' }}</div> }

                @if (s.canEdit && stagesOpen && !viewOnly) {
                  <textarea class="input" [(ngModel)]="notes[s.code]" [name]="'n-' + s.code" maxlength="1000" placeholder="Notes (optional)"></textarea>
                  <div class="actions">
                    <button class="btn primary sm" type="button" [disabled]="busy" (click)="decide(s, 'APPROVED')">Approve</button>
                    <button class="btn danger sm" type="button" [disabled]="busy" (click)="decide(s, 'REJECTED')">Reject</button>
                  </div>
                } @else {
                  <div class="view-only">View only</div>
                }
              </div>
            }
          </div>

          <div class="card card-body">
            <h2 class="card-title">Remarks</h2>
            @if (remarks.length === 0) { <p class="card-sub">No remarks yet.</p> }
            @for (r of remarks; track r.id) {
              <div class="tl-item remark" style="margin-left:0;padding-left:0">
                <p class="tl-details">{{ r.details }}</p>
                <div class="meta">{{ r.actor }} · {{ r.at | date: 'medium' }}</div>
              </div>
            }
            @if (candidate.canRemark && !viewOnly) {
              <textarea class="input" name="newRemark" maxlength="1000" placeholder="Add a remark. It is kept in the history and never overwritten." [(ngModel)]="newRemark"></textarea>
              <div class="actions"><button class="btn sm" type="button" [disabled]="busy || !newRemark.trim()" (click)="addRemark()">Add remark</button></div>
            }
          </div>

          <div class="card card-body">
            <h2 class="card-title">History</h2>
            <div class="tl">
              @for (e of candidate.timeline; track e.id) {
                <div class="tl-item" [class.remark]="e.type === 'REMARK'">
                  <div class="tl-title">{{ e.title }}</div>
                  @if (e.details) { <p class="tl-details">{{ e.details }}</p> }
                  <div class="meta">{{ e.actor }} · {{ e.at | date: 'medium' }}</div>
                </div>
              }
            </div>
          </div>
        </div>
      </div>

      @if (dialog) {
        <div class="backdrop" (click)="closeDialog()">
          <div class="dialog" role="dialog" aria-modal="true" aria-labelledby="dlg-title" (click)="$event.stopPropagation()">
            <h3 id="dlg-title">{{ dialogTitle }}</h3>
            <p>{{ dialogText }}</p>
            <div class="field">
              <label for="dlg-remarks">{{ dialog === 'PROCEED' ? 'Remarks (optional)' : 'Reason' }} @if (dialog !== 'PROCEED') { <span class="req">*</span> }</label>
              <textarea id="dlg-remarks" class="input" [class.invalid]="dialogError" maxlength="1000" [(ngModel)]="dialogRemarks" name="dlgRemarks"></textarea>
              @if (dialogError) { <span class="error" role="alert">{{ dialogError }}</span> }
            </div>
            <div class="actions" style="justify-content:flex-end">
              <button class="btn" type="button" [disabled]="busy" (click)="closeDialog()">Cancel</button>
              <button class="btn" type="button" [class.primary]="dialog === 'PROCEED'" [class.danger]="dialog === 'REJECT'" [disabled]="busy" (click)="confirmDialog()">
                {{ busy ? 'Working…' : dialogConfirmLabel }}
              </button>
            </div>
          </div>
        </div>
      }
    }
  `,
})
export class SiteCandidateDetailsComponent implements OnDestroy {
  private route = inject(ActivatedRoute);
  private params = this.route.snapshot.paramMap;
  slug = siteSlug(this.route);
  private auth = inject(AuthService);
  private service = inject(SiteCandidateService).forSite(this.slug);
  private noticeTimer?: ReturnType<typeof setTimeout>;
  candidate?: Candidate;
  notes: Record<string, string> = {};
  screening: ScreeningInput = this.emptyScreening();
  errs: Partial<Record<FieldName, string>> = {};
  newRemark = '';
  loading = true;
  busy = false;
  fileBusy = false;
  error = '';
  notice = '';
  label = statusLabel;
  accept = RESUME_ACCEPT;
  maxMb = RESUME_MAX_MB;

  /** The decision dialog (Proceed, Reject or On Hold). */
  dialog: ScreeningAction | null = null;
  dialogRemarks = '';
  dialogError = '';

  /** Once the recruiter tries to save or proceed, empty required fields are marked. */
  private submitted = false;

  constructor() {
    this.service.getById(this.params.get('candidateId')!).subscribe({
      next: (c) => { this.show(c); this.loading = false; },
      error: (err: HttpErrorResponse) => {
        this.loading = false;
        this.error = err.status === 404 ? '' : err.status === 403 ? 'You do not have access to this candidate.' : errorText(err);
      },
    });
  }

  ngOnDestroy() { clearTimeout(this.noticeTimer); }

  @HostListener('document:keydown.escape')
  onEscape() { if (this.dialog && !this.busy) this.closeDialog(); }

  /** Remarks only, in the order they were written. */
  get remarks(): TimelineEvent[] {
    return this.candidate?.timeline.filter((e) => e.type === 'REMARK') ?? [];
  }

  /** Manager is view-only. The server decides too; this just keeps every button hidden. */
  get viewOnly(): boolean { return this.auth.isViewOnly(this.slug); }

  get skillList(): string[] {
    return (this.candidate?.skills ?? '').split(',').map((k) => k.trim()).filter((k) => k.length > 0);
  }

  /** Hiring-stage buttons are hidden while the candidate is rejected or on hold. The server refuses them as well. */
  get stagesOpen(): boolean {
    return !!this.candidate && this.candidate.status !== 'REJECTED' && this.candidate.status !== 'ON_HOLD';
  }

  get canReplace(): boolean {
    return !!this.candidate && this.candidate.canRemark && !this.viewOnly && this.candidate.status !== 'REJECTED';
  }

  get dialogTitle(): string {
    const name = this.candidate?.fullName ?? 'candidate';
    switch (this.dialog) {
      case 'PROCEED': return `Proceed ${name}?`;
      case 'REJECT': return `Reject ${name}?`;
      case 'ON_HOLD': return `Put ${name} on hold?`;
      default: return '';
    }
  }

  get dialogText(): string {
    switch (this.dialog) {
      case 'PROCEED': return 'The screening details are saved and the candidate becomes eligible for Hiring Manager Review. The hiring manager still makes their own decision.';
      case 'REJECT': return 'This is final. The candidate cannot be moved to any other stage. Please give the reason, which is kept in the history.';
      case 'ON_HOLD': return 'The candidate cannot move forward while on hold. Please give the reason, which is kept in the history.';
      default: return '';
    }
  }

  get dialogConfirmLabel(): string {
    return this.dialog === 'PROCEED' ? 'Proceed' : this.dialog === 'REJECT' ? 'Reject candidate' : 'Put on hold';
  }

  // ---------------------------------------------------------------- screening form

  saveScreening() {
    if (!this.candidate || this.busy) return;
    this.submitted = true;
    if (!this.revalidate(false)) { this.error = 'Please fix the highlighted fields.'; return; }
    this.run(this.service.updateScreening(this.candidate.id, this.payload(this.screening.remarks)), 'Screening details saved.');
  }

  openDialog(action: ScreeningAction) {
    if (!this.candidate || this.busy) return;
    this.error = '';
    if (action === 'PROCEED') {
      this.submitted = true;
      if (!this.revalidate(true)) { this.error = 'Please complete the highlighted screening fields before you proceed.'; return; }
      if (!this.candidate.hasResume) { this.error = 'Upload the candidate\'s resume before you proceed.'; return; }
    } else {
      this.submitted = this.submitted || action === 'ON_HOLD';
      if (!this.revalidate(false)) { this.error = 'Please fix the highlighted fields.'; return; }
    }
    this.dialog = action;
    this.dialogRemarks = '';
    this.dialogError = '';
  }

  closeDialog() {
    if (this.busy) return;
    this.dialog = null;
    this.dialogError = '';
  }

  /**
   * Proceed and On Hold save the details first, then record the decision.
   * Reject only records the decision. A rejected candidate's details do not need saving.
   */
  confirmDialog() {
    if (!this.candidate || !this.dialog || this.busy) return;
    const action = this.dialog;
    const reason = this.dialogRemarks.trim();
    if ((action === 'REJECT' || action === 'ON_HOLD') && !reason) {
      this.dialogError = action === 'REJECT' ? 'Please enter the reason for rejecting this candidate.' : 'Please enter the reason for putting this candidate on hold.';
      return;
    }
    const id = this.candidate.id;
    const done: Record<ScreeningAction, string> = {
      PROCEED: 'Candidate proceeded to Hiring Manager Review.',
      REJECT: 'Candidate rejected. The reason is saved in the history.',
      ON_HOLD: 'Candidate put on hold.',
    };
    const decision$ = action === 'REJECT'
      ? this.service.screeningAction(id, action, reason)
      : this.service.updateScreening(id, this.payload(this.screening.remarks))
          .pipe(switchMap(() => this.service.screeningAction(id, action, reason)));
    this.run(decision$, done[action], () => { this.dialog = null; }, true);
  }

  /** Checks the form. Required fields are only checked when asked for, or after the first save attempt. */
  revalidate(requireAll = this.submitted): boolean {
    const s = this.screening;
    const e: Partial<Record<FieldName, string>> = {};
    const empty = (v: number | null | undefined) => v === null || v === undefined || Number.isNaN(v);

    const years = (v: number | null, name: string, required: boolean): string | undefined => {
      if (empty(v)) return required ? `${name} is required.` : undefined;
      if (v! < 0) return `${name} cannot be negative.`;
      if (v! > 80) return `${name} must be 80 or less.`;
      return undefined;
    };
    const money = (v: number | null, name: string): string | undefined => {
      if (empty(v)) return requireAll ? `${name} is required.` : undefined;
      if (v! < 0) return `${name} cannot be negative.`;
      if (v! > 9999999999) return `${name} is too large.`;
      return undefined;
    };

    e.totalExperience = years(s.totalExperience, 'Total experience', requireAll);
    e.relevantExperience = years(s.relevantExperience, 'Relevant experience', false);
    e.currentCtc = money(s.currentCtc, 'Current CTC');
    e.expectedCtc = money(s.expectedCtc, 'Expected CTC');
    if (empty(s.noticePeriodDays)) {
      if (requireAll) e.noticePeriodDays = 'Notice period is required.';
    } else if (s.noticePeriodDays! < 0) e.noticePeriodDays = 'Notice period cannot be negative.';
    else if (s.noticePeriodDays! > 365) e.noticePeriodDays = 'Notice period must be 365 days or less.';
    else if (!Number.isInteger(s.noticePeriodDays)) e.noticePeriodDays = 'Notice period must be a whole number of days.';

    const skills = (s.skills ?? '').trim();
    if (!skills && requireAll) e.skills = 'Add at least one skill.';
    else if (skills.length > 500) e.skills = 'Skills must be 500 characters or fewer.';
    if ((s.remarks ?? '').trim().length > 1000) e.remarks = 'Notes must be 1000 characters or fewer.';

    this.errs = Object.fromEntries(Object.entries(e).filter(([, v]) => !!v));
    return Object.keys(this.errs).length === 0;
  }

  // ---------------------------------------------------------------- resume

  openResume() {
    if (!this.candidate || this.fileBusy) return;
    const popup = window.open('', '_blank'); // opened now so the browser does not block it after the download
    this.fetchResume((blob) => {
      const isPdf = blob.type.includes('pdf');
      if (!popup || !isPdf) { popup?.close(); this.save(blob); return; } // Word files cannot be shown in a tab
      const url = URL.createObjectURL(blob);
      popup.location.href = url;
      setTimeout(() => URL.revokeObjectURL(url), 60_000);
    }, () => popup?.close());
  }

  downloadResume() {
    if (!this.candidate || this.fileBusy) return;
    this.fetchResume((blob) => this.save(blob));
  }

  replaceResume(event: Event) {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = ''; // so choosing the same file again still triggers a change
    if (!this.candidate || this.busy || !file) return;
    const problem = resumeFileError(file);
    if (problem) { this.error = problem; return; }
    this.run(this.service.replaceResume(this.candidate.id, file), 'Resume uploaded.');
  }

  private fetchResume(next: (blob: Blob) => void, failed?: () => void) {
    this.fileBusy = true; this.error = '';
    this.service.downloadResume(this.candidate!.id).subscribe({
      next: (blob) => { this.fileBusy = false; next(blob); },
      error: (err: HttpErrorResponse) => {
        this.fileBusy = false; failed?.();
        // The error body of a blob request is a Blob, so a missing file gets its own message.
        this.error = err.status === 404 ? 'The resume file could not be found. Please upload it again.' : errorText(err);
      },
    });
  }

  private save(blob: Blob) {
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url; a.download = this.candidate?.resumeName || 'resume';
    a.click();
    URL.revokeObjectURL(url);
  }

  // ---------------------------------------------------------------- remarks and stages

  addRemark() {
    if (!this.candidate || !this.newRemark.trim()) return;
    this.run(this.service.addRemark(this.candidate.id, this.newRemark.trim()), 'Remark added.', () => { this.newRemark = ''; });
  }

  decide(stage: CandidateStage, decision: StageDecision) {
    if (!this.candidate || this.busy) return;
    const verb = decision === 'APPROVED' ? 'Approve' : 'Reject';
    if (!confirm(`${verb} ${this.candidate.fullName} at "${stage.name}"?`)) return;
    this.run(this.service.updateStage(this.candidate.id, stage.code, decision, (this.notes[stage.code] ?? '').trim()), `${stage.name}: ${decision === 'APPROVED' ? 'approved' : 'rejected'}.`);
  }

  // ---------------------------------------------------------------- helpers

  /**
   * Runs one change, then shows the fresh candidate and history. Only one request runs at a time.
   * A refused change shows the server's reason. After a failed decision the page reloads, because
   * the details may already have been saved by the first step.
   */
  private run(call: Observable<Candidate>, success: string, after?: () => void, reloadOnError = false) {
    if (this.busy) return;
    this.busy = true; this.error = ''; this.notice = '';
    call.subscribe({
      next: (c) => {
        this.show(c); this.busy = false; after?.();
        this.notice = success;
        clearTimeout(this.noticeTimer);
        this.noticeTimer = setTimeout(() => (this.notice = ''), 6000);
      },
      error: (err: HttpErrorResponse) => {
        this.busy = false;
        const message = errorText(err);
        if (this.dialog) this.dialogError = message; else this.error = message;
        if (reloadOnError && this.candidate) {
          this.service.getById(String(this.candidate.id)).subscribe({ next: (c) => this.refreshKeepingForm(c) });
        }
      },
    });
  }

  /** Updates the candidate and history but keeps what the recruiter has typed. */
  private refreshKeepingForm(c: Candidate) {
    const typed = this.screening;
    this.show(c);
    if (c.canScreen) this.screening = typed;
  }

  private payload(remarks: string): ScreeningInput {
    const s = this.screening;
    return { ...s, skills: (s.skills ?? '').trim(), remarks: (remarks ?? '').trim() };
  }

  private show(c: Candidate) {
    this.candidate = c;
    this.errs = {};
    this.notes = Object.fromEntries(c.stages.map((s) => [s.code, s.notes ?? '']));
    this.screening = {
      totalExperience: c.totalExperience,
      relevantExperience: c.relevantExperience,
      currentCtc: c.currentCtc,
      expectedCtc: c.expectedCtc,
      noticePeriodDays: c.noticePeriodDays,
      skills: c.skills ?? '',
      remarks: '',
    };
  }

  private emptyScreening(): ScreeningInput {
    return { totalExperience: null, relevantExperience: null, currentCtc: null, expectedCtc: null, noticePeriodDays: null, skills: '', remarks: '' };
  }
}
