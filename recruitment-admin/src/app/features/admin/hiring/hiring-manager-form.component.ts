import { Component, inject } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { HIRING_MANAGER_STATUSES, HiringManagerStatus } from '../../../core/models/hiring.model';
import { HiringManagerService } from '../../../core/services/hiring-manager.service';
import { errorText } from '../../../core/utils/http-error';

@Component({
  selector: 'app-hiring-manager-form',
  imports: [ReactiveFormsModule, RouterLink],
  styles: `.row { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; } @media (max-width: 700px) { .row { grid-template-columns: 1fr; } }`,
  template: `
    <div class="page-head">
      <div><h1>{{ isEdit ? 'Edit Hiring Manager' : 'Create Hiring Manager' }}</h1>
        <p>Hiring managers belong to this client only.</p></div>
      <a class="btn" [routerLink]="['/admin/clients', clientId, 'hiring-managers']">← Back to Hiring Managers</a>
    </div>

    @if (loading) { <div class="spinner"></div> }
    @else {
      @if (message) { <div class="alert" [class]="message.type">{{ message.text }}</div> }
      <form [formGroup]="form" novalidate class="card card-body" (ngSubmit)="save()">
        <div class="row">
          <div class="field">
            <label for="name">Name <span class="req">*</span></label>
            <input id="name" class="input" formControlName="name" placeholder="e.g. Gautam" [class.invalid]="bad('name')" />
            @if (bad('name')) { <span class="error">Name is required (max 100 characters).</span> }
          </div>
          <div class="field">
            <label for="email">Email <span class="req">*</span></label>
            <input id="email" class="input" formControlName="email" placeholder="gautam@samsung.com" [class.invalid]="bad('email')" />
            @if (bad('email')) { <span class="error">Enter a valid email address.</span> }
          </div>
        </div>
        <div class="row">
          <div class="field">
            <label for="phone">Phone</label>
            <input id="phone" class="input" formControlName="phone" [class.invalid]="bad('phone')" />
            @if (bad('phone')) { <span class="error">Enter a valid phone number.</span> }
          </div>
          <div class="field">
            <label for="department">Department</label>
            <input id="department" class="input" formControlName="department" placeholder="e.g. IT" />
          </div>
        </div>
        <div class="field">
          <label for="status">Status</label>
          <select id="status" class="input" formControlName="status">
            @for (s of statuses; track s) { <option [value]="s">{{ s }}</option> }
          </select>
        </div>
        <div class="actions">
          <button type="submit" class="btn success" [disabled]="saving">{{ saving ? 'Saving…' : 'Save hiring manager' }}</button>
          <a class="btn" [routerLink]="['/admin/clients', clientId, 'hiring-managers']">Cancel</a>
        </div>
      </form>
    }
  `,
})
export class HiringManagerFormComponent {
  private fb = inject(FormBuilder);
  private service = inject(HiringManagerService);
  private router = inject(Router);
  private route = inject(ActivatedRoute).snapshot.paramMap;

  clientId = this.route.get('clientId')!;
  managerId = this.route.get('id');
  get isEdit() { return !!this.managerId; }

  readonly statuses = HIRING_MANAGER_STATUSES;
  loading = this.isEdit;
  saving = false;
  message: { type: 'success' | 'error'; text: string } | null = null;

  form = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(100)]],
    email: ['', [Validators.required, Validators.email, Validators.maxLength(150)]],
    phone: ['', Validators.pattern(/^$|^[0-9+()\-\s]{6,20}$/)],
    department: ['', Validators.maxLength(100)],
    status: ['Active' as HiringManagerStatus],
  });

  constructor() {
    if (this.managerId) {
      this.service.getById(this.managerId).subscribe({
        next: (m) => { this.form.patchValue(m); this.loading = false; },
        error: (err: HttpErrorResponse) => {
          this.loading = false;
          this.message = { type: 'error', text: err.status === 404 ? 'Hiring manager not found.' : errorText(err) };
        },
      });
    }
  }

  bad(name: 'name' | 'email' | 'phone') {
    const c = this.form.controls[name];
    return c.invalid && (c.touched || c.dirty);
  }

  save() {
    this.form.markAllAsTouched();
    if (this.form.invalid) { this.message = { type: 'error', text: 'Please fix the highlighted fields.' }; return; }
    this.saving = true; this.message = null;
    const value = this.form.getRawValue();
    const req = this.managerId ? this.service.update(this.managerId, value) : this.service.create(this.clientId, value);
    req.subscribe({
      next: () => this.router.navigate(['/admin/clients', this.clientId, 'hiring-managers']),
      error: (err: HttpErrorResponse) => { this.saving = false; this.message = { type: 'error', text: errorText(err) }; },
    });
  }
}
