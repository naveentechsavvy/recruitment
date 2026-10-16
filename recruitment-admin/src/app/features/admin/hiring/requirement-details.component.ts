import { Component, inject } from '@angular/core';
import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Requirement, experienceLabel, skillList } from '../../../core/models/hiring.model';
import { RequirementService } from '../../../core/services/requirement.service';
import { errorText } from '../../../core/utils/http-error';
import { PillComponent } from '../../../shared/components/pill/pill.component';

@Component({
  selector: 'app-requirement-details',
  imports: [RouterLink, DatePipe, PillComponent],
  styles: `
    .skills { display: flex; flex-wrap: wrap; gap: 6px; }
    .skills span { background: #eef2ff; color: #3730a3; border-radius: 6px; padding: 2px 8px; font-size: 13px; }
    .desc { white-space: pre-wrap; margin: 0; }
    .grid { display: grid; grid-template-columns: 2fr 1fr; gap: 20px; align-items: start; }
    @media (max-width: 900px) { .grid { grid-template-columns: 1fr; } }
  `,
  template: `
    @if (loading) { <div class="spinner"></div> }
    @else if (!requirement) {
      <div class="card empty"><h3>{{ error || 'Requirement not found' }}</h3>
        <a class="btn" [routerLink]="['/admin/clients', clientId, 'requirements']">Back to Requirements</a></div>
    } @else {
      <div class="page-head">
        <div><h1>{{ requirement.jobTitle }}</h1>
          <p>{{ requirement.jobId }} · <app-pill [label]="requirement.status" /> <app-pill [label]="requirement.priority" /></p></div>
        <div>
          <a class="btn" [routerLink]="['/admin/clients', clientId, 'requirements']">Back</a>
          <a class="btn" [routerLink]="['/admin/clients', clientId, 'requirements', requirement.id, 'candidates']">Candidates</a>
          <a class="btn primary" [routerLink]="['/admin/clients', clientId, 'requirements', 'edit', requirement.id]">Edit</a>
          @if (requirement.status !== 'Closed') { <button class="btn danger" (click)="close()">Close Requirement</button> }
        </div>
      </div>
      @if (error) { <div class="alert error">{{ error }}</div> }
      <div class="grid">
        <div class="card card-body">
          <h2 class="card-title">Job Description</h2>
          <p class="desc">{{ requirement.jobDescription }}</p>
          <h2 class="card-title" style="margin-top:20px">Required Skills</h2>
          <div class="skills">@for (s of skills; track s) { <span>{{ s }}</span> }</div>
        </div>
        <div class="card card-body">
          <h2 class="card-title">Details</h2>
          <dl>
            <dt>Positions</dt><dd>{{ requirement.numberOfPositions }}</dd>
            <dt>Experience</dt><dd>{{ exp(requirement) }}</dd>
            <dt>Location</dt><dd>{{ requirement.location }}</dd>
            <dt>Employment Type</dt><dd>{{ requirement.employmentType }}</dd>
            <dt>Department</dt><dd>{{ requirement.department || '—' }}</dd>
            <dt>Hiring Manager</dt><dd>{{ requirement.hiringManagerName }}</dd>
            <dt>Created</dt><dd>{{ requirement.createdAt | date: 'medium' }}</dd>
            <dt>Last Updated</dt><dd>{{ requirement.updatedAt | date: 'medium' }}</dd>
          </dl>
        </div>
      </div>
    }
  `,
})
export class RequirementDetailsComponent {
  private service = inject(RequirementService);
  private route = inject(ActivatedRoute).snapshot.paramMap;
  clientId = this.route.get('clientId')!;
  requirement?: Requirement;
  loading = true;
  error = '';
  exp = experienceLabel;

  constructor() {
    this.service.getById(this.route.get('id')!).subscribe({
      next: (r) => {
        // A requirement is only shown under the client that owns it.
        if (r.clientId === this.clientId) this.requirement = r;
        this.loading = false;
      },
      error: (err: HttpErrorResponse) => { this.loading = false; this.error = err.status === 404 ? '' : errorText(err); },
    });
  }

  get skills() { return this.requirement ? skillList(this.requirement.requiredSkills) : []; }

  close() {
    if (!this.requirement || !confirm(`Close requirement ${this.requirement.jobId}?`)) return;
    this.service.updateStatus(this.requirement.id, 'Closed').subscribe({
      next: (r) => { this.requirement = r; this.error = ''; },
      error: (err: HttpErrorResponse) => { this.error = errorText(err); },
    });
  }
}
