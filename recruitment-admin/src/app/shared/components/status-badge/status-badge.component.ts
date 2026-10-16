import { Component, Input } from '@angular/core';
import { ClientStatus } from '../../../core/models/client-configuration.model';

@Component({
  selector: 'app-status-badge',
  template: `<span class="badge" [class]="status.toLowerCase()">{{ status }}</span>@if (frozen) { <span class="badge frozen" title="Frozen: no new applications or changes until it is unfrozen">Frozen</span> }`,
  styles: [`
    .badge { display:inline-block; padding:2px 10px; border-radius:999px; font-size:12px; font-weight:600; }
    .draft { background:#fef3c7; color:#92400e; } .ready { background:#dbeafe; color:#1e40af; } .active { background:#dcfce7; color:#166534; }
    .frozen { background:#e0f2fe; color:#075985; border:1px solid #bae6fd; margin-left:6px; }
  `],
})
export class StatusBadgeComponent {
  @Input({ required: true }) status!: ClientStatus;
  /** When true, a "Frozen" badge is shown next to the status. */
  @Input() frozen = false;
}
