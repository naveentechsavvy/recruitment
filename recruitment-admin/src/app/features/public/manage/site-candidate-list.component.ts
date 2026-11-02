import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Candidate, statusLabel } from '../../../core/models/candidate.model';
import { AuthService } from '../../../core/services/auth.service';
import { SiteCandidateService } from '../../../core/services/site-candidate.service';
import { siteSlug } from '../../../core/services/site-hiring.service';
import { errorText } from '../../../core/utils/http-error';
import { PillComponent } from '../../../shared/components/pill/pill.component';

@Component({
  selector: 'app-site-candidate-list',
  imports: [RouterLink, DatePipe, PillComponent],
  template: `
    <div class="page-head">
      <div><h1>Candidates</h1><p>Everyone who applied for this job. Each candidate has a separate history.</p></div>
      <div>
        <a class="btn" [routerLink]="['/app', slug, 'manage', 'requirements', 'view', requirementId]">← Back to Requirement</a>
        @if (canAdd) { <a class="btn primary" [routerLink]="['/app', slug, 'manage', 'requirements', requirementId, 'candidates', 'new']">+ Add Candidate</a> }
      </div>
    </div>
    @if (notice) { <div class="alert success" role="status">{{ notice }}</div> }
    @if (error) { <div class="alert error" role="alert">{{ error }}</div> }
    <div class="card">
      @if (loading) { <div class="spinner"></div> }
      @else if (loadError) {
        <div class="empty"><h3>Could not load candidates</h3><p>{{ loadError }}</p>
          <button class="btn" (click)="load()">Try again</button></div>
      } @else if (candidates.length === 0) {
        <div class="empty"><h3>No candidates yet</h3>
          <p>Candidates appear here after they apply on the public page{{ canAdd ? ', or you can upload a resume yourself.' : '.' }}</p></div>
      } @else {
        <div class="table-wrap"><table class="table">
          <thead><tr><th>Name</th><th>Email</th><th>Resume</th><th>Screening</th><th>Current Stage</th><th>Status</th><th>Applied</th><th>Actions</th></tr></thead>
          <tbody>
            @for (c of candidates; track c.id) {
              <tr>
                <td><strong>{{ c.fullName }}</strong></td><td>{{ c.email }}</td>
                <td>{{ c.hasResume ? 'Yes' : 'No' }}</td>
                <td><app-pill [label]="label(c.screeningStatus)" /></td>
                <td>{{ c.currentStageName || '—' }}</td>
                <td><app-pill [label]="label(c.status)" /></td>
                <td>{{ c.appliedAt | date: 'medium' }}</td>
                <td>
                  <a class="btn sm" [routerLink]="['/app', slug, 'manage', 'candidates', c.id]">Open</a>
                  @if (canAdd) {
                    <button class="btn danger sm" type="button" style="margin-left:6px" [disabled]="deletingId === c.id" (click)="remove(c)">
                      {{ deletingId === c.id ? 'Deleting…' : 'Delete' }}
                    </button>
                  }
                </td>
              </tr>
            }
          </tbody></table></div>
      }
    </div>
  `,
})
export class SiteCandidateListComponent {
  private route = inject(ActivatedRoute);
  slug = siteSlug(this.route);
  private service = inject(SiteCandidateService).forSite(this.slug);
  requirementId = this.route.snapshot.paramMap.get('id')!;
  private auth = inject(AuthService);
  /** Only Admin and Recruiter may add a candidate. */
  canAdd = this.auth.canAddCandidates(this.slug);
  candidates: Candidate[] = [];
  loading = true;
  loadError = '';
  error = '';
  notice = '';
  deletingId: number | null = null;
  label = statusLabel;

  constructor() { this.load(); }

  /** Admin and Recruiter only (the same people who can add a candidate). The server checks this again. */
  remove(c: Candidate) {
    if (this.deletingId !== null) return;
    const ok = window.confirm(
      `Delete ${c.fullName} (${c.email}) permanently?\n\nTheir history, stage decisions, offer and resume are removed too. This cannot be undone.`);
    if (!ok) return;
    this.deletingId = c.id; this.error = ''; this.notice = '';
    this.service.delete(c.id).subscribe({
      next: () => {
        this.deletingId = null;
        this.candidates = this.candidates.filter((x) => x.id !== c.id);
        this.notice = `${c.fullName} was deleted.`;
      },
      error: (err: HttpErrorResponse) => { this.deletingId = null; this.error = errorText(err); },
    });
  }

  load() {
    this.loading = true; this.loadError = '';
    this.service.getByRequirement(this.requirementId).subscribe({
      next: (list) => { this.candidates = list; this.loading = false; },
      error: (err) => {
        this.loading = false;
        this.loadError = err.status === 404 ? 'This job was not found.' : err.status === 403 ? 'You do not have access to this job.' : 'Could not load candidates. Is the Spring Boot backend running on port 8080?';
      },
    });
  }
}
