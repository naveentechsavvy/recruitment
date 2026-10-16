import { Component, inject } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';
import { ClientConfiguration } from '../../../core/models/client-configuration.model';
import { HiringManager, Requirement, experienceLabel } from '../../../core/models/hiring.model';
import { ClientConfigurationService } from '../../../core/services/client-configuration.service';
import { HiringManagerService } from '../../../core/services/hiring-manager.service';
import { RequirementService } from '../../../core/services/requirement.service';
import { PillComponent } from '../../../shared/components/pill/pill.component';

@Component({
  selector: 'app-hiring-dashboard',
  imports: [RouterLink, DatePipe, FormsModule, PillComponent],
  styles: `
    .stats { display: grid; grid-template-columns: repeat(auto-fit, minmax(170px, 1fr)); gap: 16px; margin-bottom: 20px; }
    .stat { padding: 18px 20px; }
    .stat strong { display: block; font-size: 28px; line-height: 1.1; }
    .stat span { color: #6b7280; font-size: 13px; }
    .two { display: grid; grid-template-columns: 2fr 1fr; gap: 20px; align-items: start; }
    @media (max-width: 900px) { .two { grid-template-columns: 1fr; } }
    .viewer { display: flex; align-items: center; gap: 8px; }
    .activity { list-style: none; margin: 0; padding: 0; }
    .activity li { padding: 10px 0; border-bottom: 1px solid #e5e7eb; font-size: 14px; }
    .activity li:last-child { border-bottom: 0; }
    .activity small { display: block; color: #6b7280; }
  `,
  template: `
    @if (loading) { <div class="spinner"></div> }
    @else if (loadError || !client) {
      <div class="card empty"><h3>Could not load the hiring dashboard</h3>
        <p>Check that the Spring Boot backend is running on port 8080 and the client exists.</p>
        <a class="btn" routerLink="/admin/clients">Back to Clients</a></div>
    } @else {
      <div class="page-head">
        <div>
          <h1>{{ client.clientName }} Recruitment</h1>
          <p>{{ viewerName ? 'Welcome, ' + viewerName : 'Showing all requirements for this client' }}</p>
        </div>
        <div class="viewer">
          <label for="viewAs">View as</label>
          <select id="viewAs" class="input" [(ngModel)]="viewAs">
            <option value="">All hiring managers</option>
            @for (m of managers; track m.id) { <option [value]="m.id">{{ m.name }}</option> }
          </select>
          <a class="btn" [routerLink]="['/admin/clients/view', clientId]">Back</a>
        </div>
      </div>

      <div class="stats">
        <div class="card stat"><strong>{{ scoped.length }}</strong><span>Total Requirements</span></div>
        <div class="card stat"><strong>{{ count('Open') }}</strong><span>Open Requirements</span></div>
        <div class="card stat"><strong>{{ count('Closed') }}</strong><span>Closed Requirements</span></div>
        <div class="card stat"><strong>0</strong><span>Candidates (coming soon)</span></div>
      </div>

      <div class="two">
        <div class="card card-body">
          <div class="page-head" style="margin-bottom:8px">
            <h2 class="card-title">My Requirements</h2>
            <div>
              <a class="btn sm" [routerLink]="['/admin/clients', clientId, 'requirements']">View all</a>
              <a class="btn sm primary" [routerLink]="['/admin/clients', clientId, 'requirements', 'create']">+ Create Requirement</a>
            </div>
          </div>
          @if (scoped.length === 0) {
            <div class="empty"><h3>No requirements yet</h3>
              <p>{{ managers.length === 0 ? 'Add a hiring manager first, then create a requirement.' : 'Create the first requirement to get started.' }}</p>
              @if (managers.length === 0) {
                <a class="btn primary" [routerLink]="['/admin/clients', clientId, 'hiring-managers', 'create']">+ Add Hiring Manager</a>
              }
            </div>
          } @else {
            <div class="table-wrap"><table class="table">
              <thead><tr><th>Job ID</th><th>Job Title</th><th>Positions</th><th>Experience</th><th>Status</th></tr></thead>
              <tbody>
                @for (r of top; track r.id) {
                  <tr>
                    <td><a [routerLink]="['/admin/clients', clientId, 'requirements', 'view', r.id]">{{ r.jobId }}</a></td>
                    <td>{{ r.jobTitle }}</td><td>{{ r.numberOfPositions }}</td><td>{{ exp(r) }}</td>
                    <td><app-pill [label]="r.status" /></td>
                  </tr>
                }
              </tbody></table></div>
          }
        </div>

        <div>
          <div class="card card-body" style="margin-bottom:20px">
            <h2 class="card-title">Hiring Managers</h2>
            <p class="card-sub">{{ managers.length }} for this client</p>
            <a class="btn sm" [routerLink]="['/admin/clients', clientId, 'hiring-managers']">Manage</a>
            <a class="btn sm primary" [routerLink]="['/admin/clients', clientId, 'hiring-managers', 'create']">+ Add</a>
          </div>
          <div class="card card-body">
            <h2 class="card-title">Recent Activity</h2>
            @if (recent.length === 0) { <p class="card-sub">Nothing yet.</p> }
            @else {
              <ul class="activity">
                @for (r of recent; track r.id) {
                  <li>{{ r.jobId }} · {{ r.jobTitle }} <app-pill [label]="r.status" />
                    <small>Updated {{ r.updatedAt | date: 'medium' }}</small></li>
                }
              </ul>
            }
          </div>
        </div>
      </div>
    }
  `,
})
export class HiringDashboardComponent {
  clientId = inject(ActivatedRoute).snapshot.paramMap.get('clientId')!;
  client?: ClientConfiguration;
  managers: HiringManager[] = [];
  requirements: Requirement[] = [];
  viewAs = '';
  loading = true;
  loadError = false;
  exp = experienceLabel;

  constructor() {
    forkJoin({
      client: inject(ClientConfigurationService).getClientById(this.clientId),
      managers: inject(HiringManagerService).getByClient(this.clientId),
      requirements: inject(RequirementService).getByClient(this.clientId),
    }).subscribe({
      next: (d) => { this.client = d.client; this.managers = d.managers; this.requirements = d.requirements; this.loading = false; },
      error: () => { this.loading = false; this.loadError = true; },
    });
  }

  /** Requirements for the selected hiring manager, or all of this client's requirements. */
  get scoped(): Requirement[] {
    return this.viewAs ? this.requirements.filter((r) => r.hiringManagerId === this.viewAs) : this.requirements;
  }
  get top() { return this.scoped.slice(0, 5); }
  get recent() { return [...this.scoped].sort((a, b) => b.updatedAt.localeCompare(a.updatedAt)).slice(0, 5); }
  get viewerName() { return this.managers.find((m) => m.id === this.viewAs)?.name ?? ''; }
  count(status: string) { return this.scoped.filter((r) => r.status === status).length; }
}
