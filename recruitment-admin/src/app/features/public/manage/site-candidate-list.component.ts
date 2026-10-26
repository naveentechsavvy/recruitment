import { DatePipe } from '@angular/common';
import { Component, inject } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Candidate, statusLabel } from '../../../core/models/candidate.model';
import { AuthService } from '../../../core/services/auth.service';
import { SiteCandidateService } from '../../../core/services/site-candidate.service';
import { siteSlug } from '../../../core/services/site-hiring.service';
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
                <td><a class="btn sm" [routerLink]="['/app', slug, 'manage', 'candidates', c.id]">Open</a></td>
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
  label = statusLabel;

  constructor() { this.load(); }

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
