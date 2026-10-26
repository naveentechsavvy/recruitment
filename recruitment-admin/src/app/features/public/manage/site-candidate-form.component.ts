import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { SiteCandidateService } from '../../../core/services/site-candidate.service';
import { siteSlug } from '../../../core/services/site-hiring.service';
import { RESUME_ACCEPT, RESUME_MAX_MB, resumeFileError } from '../../../core/config/upload.config';
import { errorText } from '../../../core/utils/http-error';

/** The recruiter uploads a candidate's resume for one job. Screening details are filled in afterwards on the candidate page. */
@Component({
  selector: 'app-site-candidate-form',
  imports: [FormsModule, RouterLink],
  styles: `.row { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; } @media (max-width: 700px) { .row { grid-template-columns: 1fr; } }`,
  template: `
    <div class="page-head">
      <div><h1>Add Candidate</h1><p>Upload a candidate's resume for this job.</p></div>
      <a class="btn" [routerLink]="['/app', slug, 'manage', 'requirements', requirementId, 'candidates']">← Back to Candidates</a>
    </div>
    @if (error) { <div class="alert error">{{ error }}</div> }
    <form class="card card-body" (ngSubmit)="save()">
      <div class="row">
        <div class="field">
          <label for="fullName">Full name <span class="req">*</span></label>
          <input id="fullName" name="fullName" class="input" [(ngModel)]="fullName" maxlength="150" />
        </div>
        <div class="field">
          <label for="email">Email <span class="req">*</span></label>
          <input id="email" name="email" type="email" class="input" [(ngModel)]="email" maxlength="150" />
        </div>
      </div>
      <div class="row">
        <div class="field">
          <label for="phone">Phone</label>
          <input id="phone" name="phone" class="input" [(ngModel)]="phone" maxlength="30" />
        </div>
        <div class="field">
          <label for="resume">Resume <span class="req">*</span></label>
          <input id="resume" name="resume" type="file" class="input" [accept]="accept" (change)="pick($event)" />
          <span class="hint">PDF, DOC or DOCX, up to {{ maxMb }} MB.</span>
        </div>
      </div>
      <div><button class="btn primary" type="submit" [disabled]="saving">{{ saving ? 'Saving…' : 'Save Candidate' }}</button></div>
    </form>
  `,
})
export class SiteCandidateFormComponent {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  slug = siteSlug(this.route);
  private service = inject(SiteCandidateService).forSite(this.slug);
  requirementId = this.route.snapshot.paramMap.get('id')!;
  fullName = '';
  email = '';
  phone = '';
  file: File | null = null;
  accept = RESUME_ACCEPT;
  maxMb = RESUME_MAX_MB;
  saving = false;
  error = '';

  pick(event: Event) {
    this.file = (event.target as HTMLInputElement).files?.[0] ?? null;
  }

  save() {
    if (this.saving) return; // ignore a second click while the first request is running
    if (!this.fullName.trim()) { this.error = 'Please enter the full name.'; return; }
    if (!this.email.trim()) { this.error = 'Please enter the email address.'; return; }
    const fileProblem = resumeFileError(this.file);
    if (fileProblem || !this.file) { this.error = fileProblem ?? 'Please choose a resume file.'; return; }

    const form = new FormData();
    form.append('fullName', this.fullName.trim());
    form.append('email', this.email.trim());
    if (this.phone.trim()) form.append('phone', this.phone.trim());
    form.append('resume', this.file);

    this.saving = true; this.error = '';
    this.service.create(this.requirementId, form).subscribe({
      next: (c) => this.router.navigate(['/app', this.slug, 'manage', 'candidates', c.id]),
      error: (err: HttpErrorResponse) => { this.saving = false; this.error = errorText(err); },
    });
  }
}
