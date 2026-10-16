import { Component, inject } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ClientConfiguration } from '../../../core/models/client-configuration.model';
import { ClientConfigurationService } from '../../../core/services/client-configuration.service';
import { StatusBadgeComponent } from '../../../shared/components/status-badge/status-badge.component';

@Component({
  selector: 'app-dashboard',
  imports: [RouterLink, DatePipe, StatusBadgeComponent],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss',
})
export class DashboardComponent {
  clients: ClientConfiguration[] = [];
  loading = true;
  loadError = false;
  stats = { total: 0, active: 0, draft: 0, recent: 0 };

  constructor() {
    inject(ClientConfigurationService).getClients().subscribe({ error: () => { this.loading = false; this.loadError = true; }, next: (list) => {
      this.clients = [...list].sort((a, b) => b.createdAt.localeCompare(a.createdAt));
      const weekAgo = Date.now() - 7 * 864e5;
      this.stats = {
        total: list.length,
        active: list.filter((c) => c.status === 'Active').length,
        draft: list.filter((c) => c.status === 'Draft').length,
        recent: list.filter((c) => new Date(c.createdAt).getTime() > weekAgo).length,
      };
      this.loading = false;
    } });
  }
}
