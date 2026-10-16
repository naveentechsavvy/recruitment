import { Component, Input } from '@angular/core';
import { StageId, STAGE_OPTIONS, sortStages } from '../../../core/models/client-configuration.model';

@Component({
  selector: 'app-workflow-preview',
  templateUrl: './workflow-preview.component.html',
  styleUrl: './workflow-preview.component.scss',
})
export class WorkflowPreviewComponent {
  @Input() set stages(ids: StageId[]) {
    const ordered = sortStages(ids ?? []);
    this.steps = ordered.map((id) => STAGE_OPTIONS.find((s) => s.id === id)!);
  }
  steps: (typeof STAGE_OPTIONS)[number][] = [];
}
