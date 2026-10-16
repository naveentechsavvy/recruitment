import { Component, Input } from '@angular/core';

/** Small coloured label for hiring statuses, priorities and so on. */
@Component({
  selector: 'app-pill',
  template: `<span class="pill" [class]="tone">{{ label }}</span>`,
  styles: `
    .pill { display: inline-block; padding: 2px 10px; border-radius: 999px; font-size: 12px; font-weight: 600; background: #e5e7eb; color: #374151; }
    .green { background: #dcfce7; color: #166534; }
    .amber { background: #fef3c7; color: #92400e; }
    .red { background: #fee2e2; color: #991b1b; }
    .grey { background: #e5e7eb; color: #374151; }
    .blue { background: #dbeafe; color: #1e40af; }
  `,
})
export class PillComponent {
  @Input({ required: true }) label = '';

  get tone(): string {
    switch (this.label) {
      case 'Open': case 'Active': case 'Approved': case 'Completed': case 'Proceeded': return 'green';
      case 'On Hold': case 'Medium': case 'Pending': return 'amber';
      case 'Critical': case 'Rejected': return 'red';
      case 'High': case 'Current': return 'blue';
      default: return 'grey'; // Closed, Inactive, Low
    }
  }
}
