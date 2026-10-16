import { Component, inject } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { Requirement, experienceLabel } from '../../../core/models/hiring.model';
import { RequirementService } from '../../../core/services/requirement.service';
import { PillComponent } from '../../../shared/components/pill/pill.component';

@Component({
  selector: 'app-requirement-list',
  imports: [RouterLink, PillComponent],
  template: `
    <div class="page-head">
      <div><h1>Requirements</h1><p>All requirements for this client.</p></div>
      <div>
        <a class="btn" [routerLink]="['/admin/clients', clientId, 'hiring']">← Dashboard</a>
        <a class="btn primary" [routerLink]="['/admin/clients', clientId, 'requirements', 'create']">+ Create Requirement</a>
      </div>
    </div>
    <div class="card">
      @if (loading) { <div class="spinner"></div> }
      @else if (loadError) {
        <div class="empty"><h3>Could not load requirements</h3><p>Is the Spring Boot backend running on port 8080?</p>
          <button class="btn" (click)="load()">Try again</button></div>
      } @else if (requirements.length === 0) {
        <div class="empty"><h3>No requirements yet</h3><p>Create a requirement so recruiters can start working on it.</p>
          <a class="btn primary" [routerLink]="['/admin/clients', clientId, 'requirements', 'create']">+ Create Requirement</a></div>
      } @else {
        <div class="table-wrap"><table class="table">
          <thead><tr><th>Job ID</th><th>Job Title</th><th>Positions</th><th>Experience</th><th>Location</th><th>Status</th><th>Actions</th></tr></thead>
          <tbody>
            @for (r of requirements; track r.id) {
              <tr>
                <td><strong>{{ r.jobId }}</strong></td><td>{{ r.jobTitle }}</td><td>{{ r.numberOfPositions }}</td>
                <td>{{ exp(r) }}</td><td>{{ r.location }}</td><td><app-pill [label]="r.status" /></td>
                <td style="white-space:nowrap">
                  <a class="btn sm" [routerLink]="['/admin/clients', clientId, 'requirements', 'view', r.id]">View</a>
                  <a class="btn sm" [routerLink]="['/admin/clients', clientId, 'requirements', r.id, 'candidates']">Candidates</a>
                  <a class="btn sm" [routerLink]="['/admin/clients', clientId, 'requirements', 'edit', r.id]">Edit</a>
                  @if (r.status !== 'Closed') { <button class="btn sm danger" (click)="close(r)">Close Requirement</button> }
                </td>
              </tr>
            }
          </tbody></table></div>
      }
    </div>
  `,
})
export class RequirementListComponent {
  private service = inject(RequirementService);
  clientId = inject(ActivatedRoute).snapshot.paramMap.get('clientId')!;
  requirements: Requirement[] = [];
  loading = true;
  loadError = false;
  exp = experienceLabel;

  constructor() { this.load(); }
  load() {
    this.loading = true; this.loadError = false;
    this.service.getByClient(this.clientId).subscribe({
      next: (list) => { this.requirements = list; this.loading = false; },
      error: () => { this.loading = false; this.loadError = true; },
    });
  }
  close(r: Requirement) {
    if (confirm(`Close requirement ${r.jobId} (${r.jobTitle})?`)) {
      this.service.updateStatus(r.id, 'Closed').subscribe({
        next: () => this.load(),
        error: () => alert('Could not close the requirement. Please try again.'),
      });
    }
  }
}
