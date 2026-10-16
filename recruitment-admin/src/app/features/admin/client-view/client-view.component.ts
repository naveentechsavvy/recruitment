import { Component, inject } from '@angular/core';
import { DatePipe } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ClientConfiguration, themeLabel } from '../../../core/models/client-configuration.model';
import { ClientConfigurationService } from '../../../core/services/client-configuration.service';
import { StatusBadgeComponent } from '../../../shared/components/status-badge/status-badge.component';
import { WorkflowPreviewComponent } from '../../../shared/components/workflow-preview/workflow-preview.component';

@Component({
  selector: 'app-client-view',
  imports: [RouterLink, DatePipe, StatusBadgeComponent, WorkflowPreviewComponent],
  templateUrl: './client-view.component.html',
  styleUrl: './client-view.component.scss',
})
export class ClientViewComponent {
  client?: ClientConfiguration;
  loading = true;
  notFound = false;
  justGenerated = false; // true when we arrive straight after "Generate Application"

  constructor() {
    const route = inject(ActivatedRoute);
    const id = route.snapshot.paramMap.get('id')!;
    this.justGenerated = route.snapshot.queryParamMap.get('generated') === '1';
    inject(ClientConfigurationService).getClientById(id).subscribe({
      next: (c) => { this.client = c; this.loading = false; },
      error: () => { this.notFound = true; this.loading = false; },
    });
  }
  /** The public link, e.g. http://localhost:4200/app/samsung */
  get publicUrl() { return `${window.location.origin}/app/${this.client?.slug}`; }
  themeLabel(id: string) { return themeLabel(id); }
}
