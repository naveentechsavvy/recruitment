import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { CandidateService } from '../../../core/services/candidate.service';
import { errorText } from '../../../core/utils/http-error';
import { Candidate } from '../../../core/models/candidate.model';

/** A recruiter adds a candidate with a resume to one job. Screening details can be filled in now or later. */
@Component({
  selector: 'app-candidate-add',
  imports: [FormsModule, RouterLink],
  styles: `
    .cols { display: grid; grid-template-columns: 1fr 1fr; gap: 0 16px; }
    .full { grid-column: 1 / -1; }
    @media (max-width: 700px) { .cols { grid-template-columns: 1fr; } }
    h3 { margin: 8px 0 12px; font-size: 15px; }
  `,
  template: `
    <div class="page-head">
      <div><h1>Add Candidate</h1><p>Upload a candidate's resume for this job.</p></div>
      <div><a class="btn" [routerLink]="['/admin/clients', clientId, 'requirements', requirementId, 'candidates']">← Back to Candidates</a></div>
    </div>
    @if (error) { <div class="alert error">{{ error }}</div> }
    <div class="card card-body">
      <h3>Candidate</h3>
      <div class="cols">
        <div class="field"><label>Full name <span class="req">*</span></label>
          <input class="input" maxlength="150" [(ngModel)]="f.fullName" /></div>
        <div class="field"><label>Email <span class="req">*</span></label>
          <input class="input" type="email" maxlength="150" [(ngModel)]="f.email" /></div>
        <div class="field"><label>Phone</label>
          <input class="input" type="tel" maxlength="30" [(ngModel)]="f.phone" /></div>
        <div class="field"><label>Resume <span class="req">*</span></label>
          <input class="input" type="file" accept=".pdf,.doc,.docx" (change)="onFile($event)" />
          <span class="hint">PDF, DOC or DOCX, up to 5 MB.</span></div>
      </div>

      <h3>Screening details (can be completed later)</h3>
      <div class="cols">
        <div class="field"><label>Total experience (years)</label>
          <input class="input" type="number" min="0" max="60" step="0.5" [(ngModel)]="f.totalExperience" /></div>
        <div class="field"><label>Relevant experience (years)</label>
          <input class="input" type="number" min="0" max="60" step="0.5" [(ngModel)]="f.relevantExperience" /></div>
        <div class="field"><label>Current CTC</label>
          <input class="input" maxlength="50" [(ngModel)]="f.currentCtc" /></div>
        <div class="field"><label>Expected CTC</label>
          <input class="input" maxlength="50" [(ngModel)]="f.expectedCtc" /></div>
        <div class="field"><label>Notice period</label>
          <input class="input" maxlength="50" [(ngModel)]="f.noticePeriod" /></div>
        <div class="field full"><label>Skills</label>
          <input class="input" maxlength="500" [(ngModel)]="f.skills" />
          <span class="hint">Separate with commas, for example: Java, Spring Boot, Angular</span></div>
      </div>

      <button class="btn primary" [disabled]="saving" (click)="submit()">{{ saving ? 'Saving…' : 'Add Candidate' }}</button>
    </div>
  `,
})
export class CandidateAddComponent {
  private service = inject(CandidateService);
  private router = inject(Router);
  private params = inject(ActivatedRoute).snapshot.paramMap;
  clientId = this.params.get('clientId')!;
  requirementId = this.params.get('id')!;

  f = {
    fullName: '', email: '', phone: '',
    totalExperience: null as number | null, relevantExperience: null as number | null,
    currentCtc: '', expectedCtc: '', noticePeriod: '', skills: '',
  };
  file: File | null = null;
  saving = false;
  error = '';

  onFile(e: Event) { this.file = (e.target as HTMLInputElement).files?.[0] ?? null; }

  submit() {
    if (!this.f.fullName.trim() || !this.f.email.trim()) { this.error = 'Please enter the name and email.'; return; }
    if (!this.file) { this.error = 'Please attach the resume (PDF, DOC or DOCX).'; return; }
    if (this.file.size > 5 * 1024 * 1024) { this.error = 'Resume must be 5 MB or smaller.'; return; }

    const body = new FormData();
    const text: [string, string][] = [
      ['fullName', this.f.fullName], ['email', this.f.email], ['phone', this.f.phone],
      ['currentCtc', this.f.currentCtc], ['expectedCtc', this.f.expectedCtc],
      ['noticePeriod', this.f.noticePeriod], ['skills', this.f.skills],
    ];
    for (const [key, value] of text) if (value.trim()) body.append(key, value.trim());
    if (this.f.totalExperience !== null) body.append('totalExperience', String(this.f.totalExperience));
    if (this.f.relevantExperience !== null) body.append('relevantExperience', String(this.f.relevantExperience));
    body.append('resume', this.file);

    this.saving = true; this.error = '';
    this.service.create(this.requirementId, body).subscribe({
      next: (c: Candidate) => this.router.navigate(['/admin/clients', this.clientId, 'candidates', c.id]),
      error: (err: HttpErrorResponse) => { this.saving = false; this.error = errorText(err); },
    });
  }
}
