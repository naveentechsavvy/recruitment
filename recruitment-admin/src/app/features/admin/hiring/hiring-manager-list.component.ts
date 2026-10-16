import { Component, inject } from '@angular/core';
import { DatePipe } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { HiringManager } from '../../../core/models/hiring.model';
import { HiringManagerService } from '../../../core/services/hiring-manager.service';
import { PillComponent } from '../../../shared/components/pill/pill.component';

@Component({
  selector: 'app-hiring-manager-list',
  imports: [RouterLink, DatePipe, PillComponent],
  template: `
    <div class="page-head">
      <div><h1>Hiring Managers</h1><p>People at this client who create and review requirements.</p></div>
      <div>
        <a class="btn" [routerLink]="['/admin/clients', clientId, 'hiring']">← Dashboard</a>
        <a class="btn primary" [routerLink]="['/admin/clients', clientId, 'hiring-managers', 'create']">+ Create Hiring Manager</a>
      </div>
    </div>
    <div class="card">
      @if (loading) { <div class="spinner"></div> }
      @else if (loadError) {
        <div class="empty"><h3>Could not load hiring managers</h3><p>Is the Spring Boot backend running on port 8080?</p>
          <button class="btn" (click)="load()">Try again</button></div>
      } @else if (managers.length === 0) {
        <div class="empty"><h3>No hiring managers yet</h3><p>Add a hiring manager before creating requirements.</p>
          <a class="btn primary" [routerLink]="['/admin/clients', clientId, 'hiring-managers', 'create']">+ Create Hiring Manager</a></div>
      } @else {
        <div class="table-wrap"><table class="table">
          <thead><tr><th>Name</th><th>Email</th><th>Phone</th><th>Department</th><th>Status</th><th>Created</th><th>Actions</th></tr></thead>
          <tbody>
            @for (m of managers; track m.id) {
              <tr>
                <td><strong>{{ m.name }}</strong></td><td>{{ m.email }}</td><td>{{ m.phone || '—' }}</td>
                <td>{{ m.department || '—' }}</td><td><app-pill [label]="m.status" /></td>
                <td>{{ m.createdAt | date: 'mediumDate' }}</td>
                <td><a class="btn sm" [routerLink]="['/admin/clients', clientId, 'hiring-managers', 'edit', m.id]">Edit</a></td>
              </tr>
            }
          </tbody></table></div>
      }
    </div>
  `,
})
export class HiringManagerListComponent {
  private service = inject(HiringManagerService);
  clientId = inject(ActivatedRoute).snapshot.paramMap.get('clientId')!;
  managers: HiringManager[] = [];
  loading = true;
  loadError = false;

  constructor() { this.load(); }
  load() {
    this.loading = true; this.loadError = false;
    this.service.getByClient(this.clientId).subscribe({
      next: (list) => { this.managers = list; this.loading = false; },
      error: () => { this.loading = false; this.loadError = true; },
    });
  }
}
