import { Component, inject } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Requirement, experienceLabel } from '../../../core/models/hiring.model';
import { SiteHiringService, siteSlug } from '../../../core/services/site-hiring.service';
import { errorText } from '../../../core/utils/http-error';
import { PillComponent } from '../../../shared/components/pill/pill.component';

/** Requirements of THIS client, inside the client's own website. Same screen as the Admin Portal, different routes and login. */
@Component({
  selector: 'app-site-requirement-list',
  imports: [RouterLink, PillComponent],
  styles: `
    .row-actions { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; }
  `,
  template: `
    <div class="page-head">
      <div><h1>Requirements</h1><p>All requirements for your company.</p></div>
      <div>
        <a class="btn" [routerLink]="['/app', slug]">← Website</a>
        <a class="btn primary" [routerLink]="['/app', slug, 'manage', 'requirements', 'create']">+ Create Requirement</a>
      </div>
    </div>
    @if (actionError) { <div class="alert error">{{ actionError }}</div> }
    <div class="card">
      @if (loading) { <div class="spinner"></div> }
      @else if (loadError) {
        <div class="empty"><h3>Could not load requirements</h3><p>{{ loadError }}</p>
          <button class="btn" (click)="load()">Try again</button></div>
      } @else if (requirements.length === 0) {
        <div class="empty"><h3>No requirements yet</h3><p>Create a requirement so recruiters can start working on it.</p>
          <a class="btn primary" [routerLink]="['/app', slug, 'manage', 'requirements', 'create']">+ Create Requirement</a></div>
      } @else {
        <div class="table-wrap"><table class="table">
          <thead><tr><th>Job ID</th><th>Job Title</th><th>Positions</th><th>Experience</th><th>Location</th><th>Status</th><th>Actions</th></tr></thead>
          <tbody>
            @for (r of requirements; track r.id) {
              <tr>
                <td><strong>{{ r.jobId }}</strong></td><td>{{ r.jobTitle }}</td><td>{{ r.numberOfPositions }}</td>
                <td>{{ exp(r) }}</td><td>{{ r.location }}</td><td><app-pill [label]="r.status" /></td>
                <td><div class="row-actions">
                  <a class="btn sm" [routerLink]="['/app', slug, 'manage', 'requirements', 'view', r.id]">View</a>
                  <a class="btn sm" [routerLink]="['/app', slug, 'manage', 'requirements', r.id, 'candidates']">Candidates</a>
                  <a class="btn sm" [routerLink]="['/app', slug, 'manage', 'requirements', 'edit', r.id]">Edit</a>
                  @if (r.status !== 'Closed') { <button class="btn sm danger" (click)="close(r)">Close Requirement</button> }
                </div></td>
              </tr>
            }
          </tbody></table></div>
      }
    </div>
  `,
})
export class SiteRequirementListComponent {
  private service = inject(SiteHiringService);
  slug = siteSlug(inject(ActivatedRoute));
  requirements: Requirement[] = [];
  loading = true;
  loadError = '';
  actionError = '';
  exp = experienceLabel;

  constructor() { this.load(); }

  load() {
    this.loading = true; this.loadError = '';
    this.service.getRequirements(this.slug).subscribe({
      next: (list) => { this.requirements = list; this.loading = false; },
      error: (err: HttpErrorResponse) => { this.loading = false; this.loadError = errorText(err); },
    });
  }

  close(r: Requirement) {
    if (!confirm(`Close requirement ${r.jobId} (${r.jobTitle})?`)) return;
    this.actionError = '';
    this.service.updateRequirementStatus(this.slug, r.id, 'Closed').subscribe({
      next: () => this.load(),
      error: (err: HttpErrorResponse) => { this.actionError = errorText(err); },
    });
  }
}
