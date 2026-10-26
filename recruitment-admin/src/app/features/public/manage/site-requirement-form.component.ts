import { Component, inject } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { AbstractControl, FormBuilder, FormControl, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import {
  EMPLOYMENT_TYPES, EmploymentType, HiringManager, PRIORITIES, REQUIREMENT_STATUSES,
  RequirementInput, RequirementPriority, RequirementStatus,
} from '../../../core/models/hiring.model';
import { SiteHiringService, siteSlug } from '../../../core/services/site-hiring.service';
import { errorText } from '../../../core/utils/http-error';

type Field =
  | 'jobTitle' | 'jobId' | 'numberOfPositions' | 'jobDescription' | 'requiredSkills' | 'minExperience'
  | 'maxExperience' | 'location' | 'hiringManagerId';

/** Maximum experience must not be lower than minimum experience. */
const experienceRange = (g: AbstractControl): ValidationErrors | null => {
  const min = g.get('minExperience')?.value;
  const max = g.get('maxExperience')?.value;
  return min !== null && max !== null && max < min ? { range: true } : null;
};

/** Create / edit a requirement inside a client's own website. Same form as the Admin Portal, but it uses the site service and routes. */
@Component({
  selector: 'app-site-requirement-form',
  imports: [ReactiveFormsModule, RouterLink],
  styles: `
    .row { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
    .row3 { display: grid; grid-template-columns: repeat(3, 1fr); gap: 16px; }
    @media (max-width: 700px) { .row, .row3 { grid-template-columns: 1fr; } }
    textarea.input { min-height: 140px; }
  `,
  template: `
    <div class="page-head">
      <div><h1>{{ isEdit ? 'Edit Requirement' : 'Create Requirement' }}</h1>
        <p>Describe the role the recruiters should work on.</p></div>
      <a class="btn" [routerLink]="['/app', slug, 'manage', 'requirements']">← Back to Requirements</a>
    </div>

    @if (loading) { <div class="spinner"></div> }
    @else {
      @if (message) { <div class="alert" [class]="message.type">{{ message.text }}</div> }
      @if (managers.length === 0) {
        <div class="alert error">This client has no active hiring managers yet. Ask an admin to add one in the Admin Portal first.</div>
      }
      <form [formGroup]="form" novalidate class="card card-body" (ngSubmit)="save()">
        <div class="row">
          <div class="field">
            <label for="jobTitle">Job Title <span class="req">*</span></label>
            <input id="jobTitle" class="input" formControlName="jobTitle" placeholder="e.g. Full Stack Developer" [class.invalid]="bad('jobTitle')" />
            @if (bad('jobTitle')) { <span class="error">Job title is required.</span> }
          </div>
          <div class="field">
            <label for="jobId">Job ID <span class="req">*</span></label>
            <input id="jobId" class="input" formControlName="jobId" placeholder="e.g. SAM-FSD-001" [class.invalid]="bad('jobId')" />
            @if (bad('jobId')) { <span class="error">Job ID is required.</span> }
          </div>
        </div>

        <div class="row3">
          <div class="field">
            <label for="positions">Number of Positions <span class="req">*</span></label>
            <input id="positions" type="number" class="input" formControlName="numberOfPositions" [class.invalid]="bad('numberOfPositions')" />
            @if (bad('numberOfPositions')) { <span class="error">Positions must be greater than 0.</span> }
          </div>
          <div class="field">
            <label for="minExp">Minimum Experience (years) <span class="req">*</span></label>
            <input id="minExp" type="number" class="input" formControlName="minExperience" [class.invalid]="bad('minExperience')" />
            @if (bad('minExperience')) { <span class="error">Enter 0 to 60 years.</span> }
          </div>
          <div class="field">
            <label for="maxExp">Maximum Experience (years) <span class="req">*</span></label>
            <input id="maxExp" type="number" class="input" formControlName="maxExperience" [class.invalid]="bad('maxExperience') || rangeBad" />
            @if (bad('maxExperience')) { <span class="error">Enter 0 to 60 years.</span> }
            @else if (rangeBad) { <span class="error">Maximum cannot be less than minimum.</span> }
          </div>
        </div>

        <div class="field">
          <label for="skills">Required Skills <span class="req">*</span></label>
          <input id="skills" class="input" formControlName="requiredSkills" placeholder="Java, Spring Boot, Angular, SQL" [class.invalid]="bad('requiredSkills')" />
          @if (bad('requiredSkills')) { <span class="error">Required skills are required.</span> }
        </div>

        <div class="field">
          <label for="description">Job Description <span class="req">*</span></label>
          <textarea id="description" class="input" formControlName="jobDescription" [class.invalid]="bad('jobDescription')"></textarea>
          @if (bad('jobDescription')) { <span class="error">Job description is required.</span> }
        </div>

        <div class="row3">
          <div class="field">
            <label for="location">Location <span class="req">*</span></label>
            <input id="location" class="input" formControlName="location" placeholder="e.g. Bangalore" [class.invalid]="bad('location')" />
            @if (bad('location')) { <span class="error">Location is required.</span> }
          </div>
          <div class="field">
            <label for="type">Employment Type</label>
            <select id="type" class="input" formControlName="employmentType">
              @for (t of employmentTypes; track t) { <option [value]="t">{{ t }}</option> }
            </select>
          </div>
          <div class="field">
            <label for="department">Department</label>
            <input id="department" class="input" formControlName="department" placeholder="e.g. IT" />
          </div>
        </div>

        <div class="row3">
          <div class="field">
            <label for="priority">Priority</label>
            <select id="priority" class="input" formControlName="priority">
              @for (p of priorities; track p) { <option [value]="p">{{ p }}</option> }
            </select>
          </div>
          <div class="field">
            <label for="status">Status</label>
            <select id="status" class="input" formControlName="status">
              @for (s of statuses; track s) { <option [value]="s">{{ s }}</option> }
            </select>
          </div>
          <div class="field">
            <label for="manager">Hiring Manager <span class="req">*</span></label>
            <select id="manager" class="input" formControlName="hiringManagerId" [class.invalid]="bad('hiringManagerId')">
              <option value="">Select a hiring manager</option>
              @for (m of managers; track m.id) { <option [value]="m.id">{{ m.name }}</option> }
            </select>
            @if (bad('hiringManagerId')) { <span class="error">Hiring manager is required.</span> }
          </div>
        </div>

        <div class="actions">
          <button type="submit" class="btn success" [disabled]="saving">{{ saving ? 'Saving…' : 'Save requirement' }}</button>
          <a class="btn" [routerLink]="['/app', slug, 'manage', 'requirements']">Cancel</a>
        </div>
      </form>
    }
  `,
})
export class SiteRequirementFormComponent {
  private fb = inject(FormBuilder);
  private service = inject(SiteHiringService);
  private router = inject(Router);
  private route = inject(ActivatedRoute);

  slug = siteSlug(this.route);
  requirementId = this.route.snapshot.paramMap.get('id');
  get isEdit() { return !!this.requirementId; }

  readonly employmentTypes = EMPLOYMENT_TYPES;
  readonly priorities = PRIORITIES;
  readonly statuses = REQUIREMENT_STATUSES;
  managers: HiringManager[] = [];

  loading = true;
  saving = false;
  message: { type: 'success' | 'error'; text: string } | null = null;

  form = this.fb.nonNullable.group({
    jobTitle: ['', [Validators.required, Validators.maxLength(150)]],
    jobId: ['', [Validators.required, Validators.maxLength(50)]],
    numberOfPositions: new FormControl<number | null>(null, [Validators.required, Validators.min(1)]),
    jobDescription: ['', [Validators.required, Validators.maxLength(5000)]],
    requiredSkills: ['', [Validators.required, Validators.maxLength(500)]],
    minExperience: new FormControl<number | null>(null, [Validators.required, Validators.min(0), Validators.max(60)]),
    maxExperience: new FormControl<number | null>(null, [Validators.required, Validators.min(0), Validators.max(60)]),
    location: ['', [Validators.required, Validators.maxLength(150)]],
    employmentType: ['Full Time' as EmploymentType],
    department: ['', Validators.maxLength(100)],
    priority: ['Medium' as RequirementPriority],
    status: ['Open' as RequirementStatus],
    hiringManagerId: ['', Validators.required],
  }, { validators: experienceRange });

  constructor() {
    this.service.getHiringManagers(this.slug).subscribe({
      next: (list) => {
        // Only active hiring managers can be picked (the current owner stays visible when editing).
        this.managers = list.filter((m) => m.status === 'Active');
        if (!this.requirementId) { this.loading = false; return; }
        this.service.getRequirement(this.slug, this.requirementId).subscribe({
          next: (r) => {
            if (!this.managers.some((m) => m.id === r.hiringManagerId)) {
              this.managers = list.filter((m) => m.id === r.hiringManagerId).concat(this.managers);
            }
            this.form.patchValue(r);
            this.loading = false;
          },
          error: (err: HttpErrorResponse) => this.loadFailed(err, 'Requirement not found.'),
        });
      },
      error: (err: HttpErrorResponse) => this.loadFailed(err, 'Hiring managers could not be loaded.'),
    });
  }

  bad(name: Field) {
    const c = this.form.controls[name];
    return c.invalid && (c.touched || c.dirty);
  }
  get rangeBad() {
    return this.form.hasError('range') && (this.form.controls.maxExperience.touched || this.form.controls.minExperience.touched);
  }

  save() {
    this.form.markAllAsTouched();
    if (this.form.invalid) { this.message = { type: 'error', text: 'Please fix the highlighted fields.' }; return; }
    this.saving = true; this.message = null;
    const value = this.form.getRawValue() as RequirementInput;
    const req = this.requirementId
      ? this.service.updateRequirement(this.slug, this.requirementId, value)
      : this.service.createRequirement(this.slug, value);
    req.subscribe({
      next: (saved) => this.router.navigate(['/app', this.slug, 'manage', 'requirements', 'view', saved.id]),
      error: (err: HttpErrorResponse) => { this.saving = false; this.message = { type: 'error', text: errorText(err) }; },
    });
  }

  private loadFailed(err: HttpErrorResponse, notFound: string) {
    this.loading = false;
    this.message = { type: 'error', text: err.status === 404 ? notFound : errorText(err) };
  }
}
