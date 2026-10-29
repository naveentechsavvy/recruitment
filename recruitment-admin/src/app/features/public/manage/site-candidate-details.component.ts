import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { AfterViewChecked, Component, ElementRef, HostListener, OnDestroy, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Observable, switchMap } from 'rxjs';
import {
  Candidate, CandidateStage, ScreeningAction, ScreeningInput, StageDecision, TimelineEvent, statusLabel,
} from '../../../core/models/candidate.model';
import { RESUME_ACCEPT, RESUME_MAX_MB, resumeFileError } from '../../../core/config/upload.config';
import { AuthService, Role } from '../../../core/services/auth.service';
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
    /* Wider page: breaks out of the parent's max-width, stays centered, never wider than 1600px. */
    :host {
      display: block;
      width: min(1600px, calc(100vw - 64px));
      margin-left: calc(50% - min(1600px, calc(100vw - 64px)) / 2);
    }
    @media (max-width: 900px) {
      :host { width: auto; margin-left: 0; }
    }

    .grid { display: grid; grid-template-columns: 1fr 2fr; gap: 20px; align-items: start; }
    @media (max-width: 900px) { .grid { grid-template-columns: 1fr; } }
    .col { display: flex; flex-direction: column; gap: 20px; min-width: 0; }
    .meta { color: #6b7280; font-size: 12px; margin-top: 4px; }
    .notes { white-space: pre-wrap; margin: 8px 0 0; }
    textarea { width: 100%; min-height: 70px; margin-top: 8px; box-sizing: border-box; }
    .actions { margin-top: 8px; display: flex; gap: 8px; flex-wrap: wrap; }
    .view-only { color: #6b7280; font-size: 12px; margin-top: 8px; }
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
    .profile { display: grid; grid-template-columns: 150px 1fr; margin: 0; }
    .profile dt, .profile dd { padding: 11px 0; border-bottom: 1px solid #eef0f3; }
    .profile dt { color: #6b7280; font-size: 13px; font-weight: 500; padding-right: 12px; }
    .profile dd { margin: 0; color: var(--text); font-size: 14px; font-weight: 600; word-break: break-word; }
    .profile dt:last-of-type, .profile dd:last-of-type { border-bottom: 0; }
    @media (max-width: 480px) {
      .profile { grid-template-columns: 1fr; }
      .profile dt { border-bottom: 0; padding-bottom: 0; }
    }
    .resume-box { display: flex; flex-direction: column; gap: 8px; }
    .resume-name { word-break: break-all; font-weight: 500; }
    .banner { padding: 12px 16px; border-radius: 8px; margin-bottom: 20px; border: 1px solid; }
    .banner.warn { background: #fffbeb; border-color: #fde68a; color: #92400e; }
    .banner.bad { background: #fef2f2; border-color: #fecaca; color: #991b1b; }
    .banner.ok { background: #f0fdf4; border-color: #bbf7d0; color: #166534; }
    .backdrop { position: fixed; inset: 0; background: rgba(17, 24, 39, .35); display: grid; place-items: center; padding: 16px; z-index: 1000; }
    .dialog { background: #fff; border-radius: 12px; padding: 24px; width: 100%; max-width: 460px; box-shadow: 0 20px 40px rgba(0, 0, 0, .25); }
    .dialog h3 { margin: 0 0 6px; font-size: 18px; }
    .dialog p { margin: 0 0 12px; color: #667085; }

    /* ---- horizontal workflow ---- */
    .workflow { margin: 0; }
    .stepper { list-style: none; display: flex; margin: 0 0 18px; padding: 0 0 8px; overflow-x: auto; position: relative; }
    .step { flex: 1 0 140px; min-width: 140px; position: relative; }
    .step:not(:last-child)::after { content: ''; position: absolute; top: 17px; left: calc(50% + 22px); width: calc(100% - 44px); height: 2px; background: #e5e7eb; }
    .step.done:not(:last-child)::after { background: #16a34a; }
    .step-btn { width: 100%; display: flex; flex-direction: column; align-items: center; gap: 4px; padding: 0 6px; border: 0; background: none; font: inherit; color: inherit; cursor: pointer; text-align: center; }
    .step-btn:disabled { cursor: not-allowed; }
    .dot { width: 34px; height: 34px; border-radius: 50%; display: grid; place-items: center; font-weight: 700; font-size: 14px; border: 2px solid #d1d5db; background: #fff; color: #6b7280; }
    .step.done .dot { background: #16a34a; border-color: #16a34a; color: #fff; }
    .step.bad .dot { background: #dc2626; border-color: #dc2626; color: #fff; }
    .step.cur .dot { border-color: var(--primary); color: var(--primary); }
    .step.sel .dot { box-shadow: 0 0 0 4px var(--primary-soft); border-color: var(--primary); }
    .step.sel.done .dot { background: #16a34a; color: #fff; }
    .step.sel.bad .dot { background: #dc2626; color: #fff; }
    .step-no { font-size: 11px; font-weight: 600; letter-spacing: .04em; text-transform: uppercase; color: #6b7280; }
    .step-name { font-size: 13px; font-weight: 600; line-height: 1.3; }
    .step-state { font-size: 11px; color: #6b7280; }
    .step.locked { opacity: .55; }
    .step.sel .step-name { color: var(--primary); }
    .step-box { border: 1px solid #e5e7eb; border-radius: 12px; padding: 18px 20px; background: #fff; }
    .box-head { display: flex; justify-content: space-between; align-items: flex-start; gap: 12px; margin-bottom: 10px; }
    .box-head h3 { margin: 2px 0 0; font-size: 18px; }
    .hint-text { color: #6b7280; font-size: 12px; }
    .tabs { display: flex; gap: 6px; margin-bottom: 4px; }
    .tab { border: 1px solid #e5e7eb; background: #fff; border-radius: 8px; padding: 8px 16px; font: inherit; font-weight: 600; cursor: pointer; color: #374151; }
    .tab.on { background: var(--primary); border-color: var(--primary); color: #fff; }
    .rating { display: flex; align-items: center; gap: 4px; margin-top: 10px; }
    .star { border: 0; background: none; font-size: 26px; line-height: 1; color: #d1d5db; cursor: pointer; padding: 0 2px; }
    .star.on { color: #f59e0b; }
    .star:disabled { cursor: not-allowed; }
    .locked { opacity: .5; }
    .locked-note { color: #6b7280; font-size: 12px; margin-top: 8px; }
    .box-nav { display: flex; justify-content: space-between; align-items: center; gap: 12px; margin-top: 18px; padding-top: 14px; border-top: 1px solid #eef0f3; }
    @media (max-width: 600px) { .box-nav { flex-wrap: wrap; } }
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
        @if (candidate.screeningStatus === 'ON_HOLD') {
          <div class="banner warn">This candidate is on hold and cannot move to the next stage. Choose Proceed or Reject when you are ready.</div>
        } @else {
          <div class="banner warn">{{ holdBanner }}</div>
        }
      } @else if (candidate.screeningStatus === 'PROCEEDED') {
        <div class="banner ok">Screening is complete. The candidate is ready for Hiring Manager Review.</div>
      }

      <div class="grid">
        <!-- LEFT COLUMN: Profile is at the top-left -->
        <div class="col">
          <div class="card card-body">
            <h2 class="card-title">Profile</h2>
            <dl class="profile">
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

        <!-- RIGHT COLUMN: Workflow, Remarks (everyone) and History (admin only) -->
        <div class="col">
          @if (canSeeHistory) {
            <div class="tabs" role="tablist">
              <button type="button" class="tab" role="tab" [class.on]="tab === 'workflow'" [attr.aria-selected]="tab === 'workflow'" (click)="tab = 'workflow'">Workflow</button>
              <button type="button" class="tab" role="tab" [class.on]="tab === 'history'" [attr.aria-selected]="tab === 'history'" (click)="tab = 'history'">History</button>
            </div>
          }
          @if (tab === 'workflow' || !canSeeHistory) {
          @if (stages.length) {
            <div class="card card-body workflow">
              <h2 class="card-title">Workflow</h2>
              <ol class="stepper">
                @for (s of stages; track s.code; let i = $index) {
                  <li class="step" [class.done]="s.decision === 'APPROVED'" [class.bad]="s.decision === 'REJECTED'"
                      [class.sel]="i === selectedIndex" [class.locked]="!isUnlocked(i)" [class.cur]="s.current">
                    <button type="button" class="step-btn" [disabled]="!isUnlocked(i)"
                            [attr.aria-current]="i === selectedIndex ? 'step' : null"
                            [attr.title]="isUnlocked(i) ? null : 'Locked until the previous step is approved'"
                            (click)="select(i)">
                      <span class="dot">{{ stepIcon(s, i) }}</span>
                      <span class="step-no">Step {{ i + 1 }}</span>
                      <span class="step-name">{{ s.name }}</span>
                      <span class="step-state">{{ stepState(s, i) }}</span>
                    </button>
                  </li>
                }
              </ol>

              @if (selectedStage; as s) {
                <div class="step-box">
                  <div class="box-head">
                    <div>
                      <div class="step-no">Step {{ selectedIndex + 1 }} of {{ stages.length }}</div>
                      <h3>{{ s.name }}</h3>
                    </div>
                    <span>@if (s.current) { <app-pill label="Current" /> } <app-pill [label]="label(s.decision)" /></span>
                  </div>

                  @if (canAct(s) || canResume(s) || (decided(s) && s.canEdit)) {
                    <label class="hint-text" for="stage-notes">Notes <span class="req">*</span></label>
                    <textarea id="stage-notes" class="input" [class.invalid]="notesError" [class.locked]="decided(s)" [disabled]="decided(s)"
                              [(ngModel)]="notes[s.code]" (ngModelChange)="notesError = ''" [name]="'n-' + s.code" maxlength="1000"
                              placeholder="Notes (required)"></textarea>
                    @if (notesError) { <span class="error" role="alert">{{ notesError }}</span> }

                    @if (isRated(s)) {
                      <div class="rating" [class.locked]="decided(s)" role="radiogroup" aria-label="Rating">
                        <span class="hint-text">Rating <span class="req">*</span></span>
                        @for (n of [1, 2, 3, 4, 5]; track n) {
                          <button type="button" class="star" [class.on]="n <= (ratings[s.code] ?? 0)"
                                  [disabled]="decided(s) || busy" [attr.aria-label]="n + ' out of 5'" (click)="setRating(s, n)">★</button>
                        }
                        @if (ratings[s.code]) {
                          <span class="hint-text">{{ ratings[s.code] }} / 5</span>
                          @if (!decided(s)) {
                            <button type="button" class="btn sm" [disabled]="busy" (click)="ratings[s.code] = null">Clear</button>
                          }
                        }
                      </div>
                      @if (ratingError) { <span class="error" role="alert">{{ ratingError }}</span> }
                    }

                    <div class="actions" [class.locked]="decided(s)">
                      @if (canAct(s) || decided(s)) {
                        <button class="btn primary sm" type="button" [disabled]="busy || decided(s)" (click)="decide(s, 'APPROVED')">Approve</button>
                        <button class="btn danger sm" type="button" [disabled]="busy || decided(s)" (click)="decide(s, 'REJECTED')">Reject</button>
                      }
                      @if (canHold(s) || (decided(s) && supportsHold(s))) {
                        <button class="btn sm" type="button" [disabled]="busy || decided(s)" (click)="decide(s, 'HOLD')">Hold</button>
                      }
                      @if (canResume(s)) {
                        <button class="btn primary sm" type="button" [disabled]="busy" (click)="decide(s, 'PENDING')">Resume</button>
                      }
                    </div>
                    @if (decided(s)) { <div class="locked-note">This step is {{ s.decision === 'APPROVED' ? 'approved' : 'rejected' }} and can no longer be changed.</div> }
                  } @else {
                    @if (s.notes) { <p class="notes">{{ s.notes }}</p> }
                    @if (s.rating) { <p class="notes">Rating: {{ s.rating }} / 5</p> }
                    <div class="view-only">{{ viewOnlyText }}</div>
                  }
                  @if (s.updatedBy && canSeeHistory) { <div class="meta">{{ s.updatedBy }} · {{ s.updatedAt | date: 'medium' }}</div> }

                  <div class="box-nav">
                    <button class="btn sm" type="button" [disabled]="!canPrev" (click)="prev()">← Previous</button>
                    <span class="hint-text">{{ nextHint }}</span>
                    <button class="btn primary sm" type="button" [disabled]="!canNext" (click)="next()">Next →</button>
                  </div>
                </div>
              }
            </div>
          }

          <div class="card card-body">
            <h2 class="card-title">Remarks</h2>
            @if (remarks.length === 0) { <p class="card-sub">No remarks yet.</p> }
            @for (r of remarks; track r.id) {
              <div class="tl-item remark" style="margin-left:0;padding-left:0">
                <p class="tl-details">{{ r.details }}</p>
                <div class="meta">{{ r.actor }} · {{ r.at | date: 'medium' }}</div>
              </div>
            }
            @if (candidate.canRemark) {
              <textarea class="input" name="newRemark" maxlength="1000" placeholder="Add a remark. It is kept in the history and never overwritten." [(ngModel)]="newRemark"></textarea>
              <div class="actions"><button class="btn sm" type="button" [disabled]="busy || !newRemark.trim()" (click)="addRemark()">Add remark</button></div>
            }
          </div>

          }

          @if (canSeeHistory && tab === 'history') {
          <div class="card card-body">
            <h2 class="card-title">History</h2>
            <p class="card-sub">Who did what, and when. Only admins can see this.</p>
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
          }
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

      @if (stageDialog) {
        <div class="backdrop" (click)="closeStageDialog()">
          <div class="dialog" role="dialog" aria-modal="true" aria-labelledby="stg-title" (click)="$event.stopPropagation()">
            <h3 id="stg-title">{{ stageDialogTitle }}</h3>
            <p>{{ stageDialogText }}</p>
            @if (stageDialogError) { <span class="error" role="alert">{{ stageDialogError }}</span> }
            <div class="actions" style="justify-content:flex-end">
              <button class="btn" type="button" [disabled]="busy" (click)="closeStageDialog()">Cancel</button>
              <button class="btn" type="button" [class.primary]="stageDialog.decision === 'APPROVED' || stageDialog.decision === 'PENDING'" [class.danger]="stageDialog.decision === 'REJECTED'" [disabled]="busy" (click)="confirmStageDialog()">
                {{ busy ? 'Working…' : stageVerb(stageDialog.decision) }}
              </button>
            </div>
          </div>
        </div>
      }
    }
  `,
})
export class SiteCandidateDetailsComponent implements OnDestroy, AfterViewChecked {
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  /** What the step bar was last scrolled to, so it scrolls once per change and never fights the user's own scrolling. */
  private scrolledTo: { el: Element; code: string } | null = null;

  /** Keeps the selected step visible: the step bar scrolls sideways by itself when the steps do not all fit. */
  ngAfterViewChecked(): void {
    const bar = this.host.nativeElement.querySelector<HTMLElement>('.stepper');
    const step = bar?.querySelector<HTMLElement>('.step.sel');
    if (!bar || !step) return;
    if (this.scrolledTo?.el === bar && this.scrolledTo.code === this.selectedCode) return;
    this.scrolledTo = { el: bar, code: this.selectedCode };
    // Only the step bar moves (the page does not jump): put the selected step in the middle.
    const left = step.offsetLeft - (bar.clientWidth - step.clientWidth) / 2;
    bar.scrollTo({ left: Math.max(0, left), behavior: 'smooth' });
  }

  private route = inject(ActivatedRoute);
  private params = this.route.snapshot.paramMap;
  slug = siteSlug(this.route);
  private auth = inject(AuthService);
  private service = inject(SiteCandidateService).forSite(this.slug);
  private noticeTimer?: ReturnType<typeof setTimeout>;
  candidate?: Candidate;
  notes: Record<string, string> = {};
  /** 1 to 5 stars per stage (technical rounds). */
  ratings: Record<string, number | null> = {};
  ratingError = '';
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

  /** Which tab of the right column is open. The History tab exists for admins only. */
  tab: 'workflow' | 'history' = 'workflow';

  /** Only an admin (the client's own Admin or the platform Admin) may see the history of who did what. The server filters it too. */
  get canSeeHistory(): boolean {
    const role = this.auth.clientRole(this.slug);
    return role === 'ADMIN' || role === 'CLIENT_ADMIN';
  }

  /** The step (stage code) opened in the workflow box. */
  selectedCode = '';

  /** The decision dialog (Proceed, Reject or On Hold). */
  dialog: ScreeningAction | null = null;
  dialogRemarks = '';
  dialogError = '';

  /** The stage decision dialog (Approve or Reject at a hiring step). */
  stageDialog: { stage: CandidateStage; decision: StageDecision } | null = null;
  stageDialogError = '';
  /** Shown under the stage notes box when Approve or Reject is clicked with empty notes. */
  notesError = '';

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
  onEscape() {
    if (this.busy) return;
    if (this.dialog) this.closeDialog();
    else if (this.stageDialog) this.closeStageDialog();
  }

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

  // ---------------------------------------------------------------- workflow (steps)

  get stages(): CandidateStage[] { return this.candidate?.stages ?? []; }

  get selectedIndex(): number { return this.stages.findIndex((s) => s.code === this.selectedCode); }

  get selectedStage(): CandidateStage | undefined { return this.stages[this.selectedIndex]; }

  /**
   * The last step that may be opened: every step up to the first one that is not approved yet,
   * and never behind the step the server marks as current. Every step counts as required.
   */
  get unlockedLimit(): number {
    const list = this.stages;
    if (!list.length) return -1;
    const firstOpen = list.findIndex((s) => s.decision !== 'APPROVED');
    const currentIdx = list.findIndex((s) => s.current);
    return Math.max(firstOpen === -1 ? list.length - 1 : firstOpen, currentIdx);
  }

  isUnlocked(i: number): boolean { return i <= this.unlockedLimit; }

  stepIcon(s: CandidateStage, i: number): string {
    if (s.decision === 'APPROVED') return '✓';
    if (s.decision === 'REJECTED') return '✕';
    if (s.decision === 'HOLD') return 'II';
    return String(i + 1);
  }

  stepState(s: CandidateStage, i: number): string {
    if (s.decision === 'APPROVED') return 'Approved';
    if (s.decision === 'REJECTED') return 'Rejected';
    if (s.decision === 'HOLD') return 'On hold';
    if (!this.isUnlocked(i)) return 'Locked';
    return s.current ? 'Current' : 'Pending';
  }

  select(i: number) {
    if (i < 0 || i >= this.stages.length || !this.isUnlocked(i)) return;
    this.selectedCode = this.stages[i].code;
  }

  prev() { this.select(this.selectedIndex - 1); }
  next() { this.select(this.selectedIndex + 1); }

  get canPrev(): boolean { return this.selectedIndex > 0; }

  get canNext(): boolean {
    const i = this.selectedIndex;
    return i >= 0 && i < this.stages.length - 1 && this.isUnlocked(i + 1);
  }

  get nextHint(): string {
    const i = this.selectedIndex;
    if (i < 0 || i >= this.stages.length - 1 || this.canNext) return '';
    const s = this.stages[i];
    if (s.decision === 'REJECTED') return 'This step was rejected, so the next steps stay locked.';
    return this.canAct(s) ? 'Approve this step to unlock the next one.' : 'The next step opens once this step is approved.';
  }

  /** Stages that need a 1 to 5 rating when approved or rejected. */
  private static readonly RATED = ['technical-round', 'technical-round-l1', 'technical-round-l2'];
  /** Stages that have no Hold button (screening has its own panel). */
  private static readonly NO_HOLD = ['candidate-application', 'resume-screening'];

  isRated(s: CandidateStage): boolean { return SiteCandidateDetailsComponent.RATED.includes(s.code); }
  supportsHold(s: CandidateStage): boolean { return !SiteCandidateDetailsComponent.NO_HOLD.includes(s.code); }
  /** Approved or rejected: the buttons stay faded out and cannot be used again. */
  decided(s: CandidateStage): boolean { return s.decision === 'APPROVED' || s.decision === 'REJECTED'; }

  /**
   * Who is responsible for a step, and so the only role that may hold, resume, approve or reject it while it is on hold.
   * Hiring Manager Review belongs to the Hiring Manager, Offer Released to the HR BP, every other step to the Recruiter.
   * To give another step to someone else, add it here. The server must apply the same rule.
   */
  private static readonly STEP_OWNER: Record<string, Role> = { 'hiring-manager-review': 'HIRING_MANAGER', 'offer-released': 'HR_BP' };

  ownerRole(s: CandidateStage): Role { return SiteCandidateDetailsComponent.STEP_OWNER[s.code] ?? 'RECRUITER'; }

  ownerLabel(s: CandidateStage): string {
    const r = this.ownerRole(s);
    return r === 'HIRING_MANAGER' ? 'Hiring Manager' : r === 'HR_BP' ? 'HR BP' : 'Recruiter';
  }

  /** The signed-in user holds the role that is responsible for this step. */
  isResponsible(s: CandidateStage): boolean { return this.auth.clientRole(this.slug) === this.ownerRole(s); }

  /** The step is the one the candidate is currently held at. */
  private isHeld(s: CandidateStage): boolean { return this.candidate?.status === 'ON_HOLD' && s.decision === 'HOLD'; }

  /** The step may be changed: not decided yet, and the candidate is not rejected or held at some other step. */
  private stageOpen(s: CandidateStage): boolean {
    const c = this.candidate;
    if (!c || c.status === 'REJECTED') return false;
    return c.status !== 'ON_HOLD' || s.decision === 'HOLD';
  }

  /**
   * Approve or reject a step. While a step is on hold only the role responsible for that step may act on it (rule: nobody else
   * can approve, reject or move the candidate on). Otherwise the server says who may edit the step. The server checks again.
   */
  canAct(s: CandidateStage): boolean {
    if (this.viewOnly || !this.stageOpen(s) || this.decided(s)) return false;
    return this.isHeld(s) ? this.isResponsible(s) : !!s.canEdit;
  }

  /** Resume: only for the step that is on hold, and only for the role responsible for it. */
  canResume(s: CandidateStage): boolean {
    return !this.viewOnly && !this.decided(s) && this.isHeld(s) && this.isResponsible(s);
  }

  /** Hold: only the role responsible for the step may put it on hold, so the same role can always resume it. */
  canHold(s: CandidateStage): boolean {
    return this.canAct(s) && this.supportsHold(s) && s.current && this.candidate?.status === 'ACTIVE' && this.isResponsible(s);
  }

  /** The banner under the page title while a step is on hold. */
  get holdBanner(): string {
    const held = this.stages.find((s) => s.decision === 'HOLD');
    if (!held) return `This candidate is on hold at ${this.candidate?.currentStageName ?? 'this step'}.`;
    const who = this.ownerLabel(held);
    return this.isResponsible(held) && !this.viewOnly
      ? `This candidate is on hold at ${held.name}. Resume, approve or reject that step to continue.`
      : `This candidate is on hold at ${held.name}. Only the ${who} can resume, approve or reject that step.`;
  }

  setRating(s: CandidateStage, n: number) {
    if (this.decided(s) || this.busy) return;
    // Clicking the star that is already selected clears the rating again.
    this.ratings[s.code] = this.ratings[s.code] === n ? null : n;
    this.ratingError = '';
  }

  get viewOnlyText(): string {
    if (this.viewOnly) return 'View only.';
    const sel = this.selectedStage;
    if (sel && this.isHeld(sel)) return `This step is on hold. Only the ${this.ownerLabel(sel)} can resume, approve or reject it.`;
    if (!this.stagesOpen) return 'Stage changes are closed while the candidate is rejected or on hold.';
    if (this.selectedStage && this.decided(this.selectedStage)) return 'This step is already decided and can no longer be changed.';
    return 'View only. Only the role assigned to this step can approve or reject it.';
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
    if (this.decided(stage)) return; // an approved or rejected step is final
    // Resume has its own rule. Every other decision is for the role assigned to the step; the server refuses everyone else too.
    if (decision === 'PENDING' ? !this.canResume(stage) : !this.canAct(stage)) return;
    // Resuming reuses the notes already on the step, so they are only required for Approve, Reject and Hold.
    if (decision !== 'PENDING' && !(this.notes[stage.code] ?? '').trim()) {
      this.notesError = `Notes are required before you ${this.stageVerb(decision).toLowerCase()}.`;
      return;
    }
    this.notesError = '';
    if (this.isRated(stage) && (decision === 'APPROVED' || decision === 'REJECTED') && !this.ratings[stage.code]) {
      this.ratingError = 'Please give a rating from 1 to 5.';
      return;
    }
    this.ratingError = '';
    this.stageDialogError = '';
    this.stageDialog = { stage, decision };
  }

  stageVerb(d: StageDecision): string {
    switch (d) {
      case 'APPROVED': return 'Approve';
      case 'REJECTED': return 'Reject';
      case 'HOLD': return 'Hold';
      default: return 'Resume';
    }
  }

  get stageDialogTitle(): string {
    const name = this.candidate?.fullName ?? 'candidate';
    switch (this.stageDialog?.decision) {
      case 'APPROVED': return `Approve ${name}?`;
      case 'REJECTED': return `Reject ${name}?`;
      case 'HOLD': return `Put ${name} on hold?`;
      default: return `Resume ${name}?`;
    }
  }

  get stageDialogText(): string {
    const d = this.stageDialog;
    if (!d) return '';
    const at = `at "${d.stage.name}"`;
    switch (d.decision) {
      case 'APPROVED': return `Approve this candidate ${at}? This cannot be changed afterwards.`;
      case 'REJECTED': return `Reject this candidate ${at}? This cannot be changed afterwards.`;
      case 'HOLD': return `The candidate cannot move forward while on hold ${at}. Your notes are kept in the history.`;
      default: return `The candidate becomes active again ${at}. Your notes are kept in the history.`;
    }
  }

  closeStageDialog() {
    if (this.busy) return;
    this.stageDialog = null;
    this.stageDialogError = '';
  }

  confirmStageDialog() {
    if (!this.candidate || !this.stageDialog || this.busy) return;
    const { stage, decision } = this.stageDialog;
    // Always send the rating the step already has, so Hold and Resume never wipe it.
    const rating = this.isRated(stage) ? (this.ratings[stage.code] ?? stage.rating ?? null) : null;
    const done: Record<StageDecision, string> = {
      APPROVED: 'approved.', REJECTED: 'rejected.', HOLD: 'candidate put on hold.', PENDING: 'candidate resumed.',
    };
    this.run(
      this.service.updateStage(this.candidate.id, stage.code, decision, (this.notes[stage.code] ?? '').trim(), rating),
      `${stage.name}: ${done[decision]}`,
      () => {
        this.stageDialog = null;
        this.stageDialogError = '';
        if (decision === 'APPROVED') this.openNextStep(stage);
      },
    );
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
        if (this.dialog) this.dialogError = message;
        else if (this.stageDialog) this.stageDialogError = message;
        else this.error = message;
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
    this.ratings = Object.fromEntries(c.stages.map((s) => [s.code, s.rating ?? null]));
    this.ratingError = '';
    this.screening = {
      totalExperience: c.totalExperience,
      relevantExperience: c.relevantExperience,
      currentCtc: c.currentCtc,
      expectedCtc: c.expectedCtc,
      noticePeriodDays: c.noticePeriodDays,
      skills: c.skills ?? '',
      remarks: '',
    };
    this.syncSelection();
  }

  /** After an approval the next step opens by itself (no Next click), as long as the candidate has moved on to it. */
  private openNextStep(approved: CandidateStage) {
    if (this.selectedCode !== approved.code) return;
    const list = this.stages;
    const from = list.findIndex((s) => s.code === approved.code);
    const next = list.find((s, i) => i > from && s.current);
    if (next) this.selectedCode = next.code;
  }

  /** Keeps the opened step when the page reloads after a change. Otherwise it opens the current step. */
  private syncSelection() {
    const list = this.stages;
    const idx = list.findIndex((s) => s.code === this.selectedCode);
    if (idx === -1 || !this.isUnlocked(idx)) {
      this.selectedCode = (list.find((s) => s.current) ?? list[0])?.code ?? '';
    }
  }

  private emptyScreening(): ScreeningInput {
    return { totalExperience: null, relevantExperience: null, currentCtc: null, expectedCtc: null, noticePeriodDays: null, skills: '', remarks: '' };
  }
}