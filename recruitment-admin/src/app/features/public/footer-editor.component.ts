import { HttpErrorResponse } from '@angular/common/http';
import { Component, EventEmitter, Input, OnInit, Output, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { PublicFooter } from '../../core/models/public-app.model';
import { PublicAppService } from '../../core/services/public-app.service';
import { errorText } from '../../core/utils/http-error';
import { optionalEmail, optionalUrl } from '../../core/utils/form-validators';

/**
 * The footer edit form shown inside a client's own website (/app/:slug) for people who are allowed to edit it.
 * It only edits THIS client's footer: the slug goes in the address and the backend matches it to the login.
 */
@Component({
  selector: 'app-footer-editor',
  imports: [ReactiveFormsModule],
  styles: `
    :host { display: block; }
    .panel { background: #fff; color: #1a2233; border-radius: 12px; padding: 20px; margin: 4px 0 8px; box-shadow: 0 4px 16px rgba(16, 24, 40, .18); text-decoration: none; }
    h3 { margin: 0 0 4px; font-size: 17px; }
    .sub { margin: 0 0 14px; font-size: 13px; color: #667085; }
    .row { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
    @media (max-width: 640px) { .row { grid-template-columns: 1fr; } }
    .f { display: flex; flex-direction: column; margin-bottom: 12px; }
    label { font-size: 13px; font-weight: 600; margin-bottom: 4px; }
    input, textarea { font: inherit; font-size: 14px; padding: 9px 11px; border: 1px solid #d0d5dd; border-radius: 8px; color: #1a2233; background: #fff; }
    textarea { min-height: 64px; resize: vertical; }
    input.invalid, textarea.invalid { border-color: #d92d20; }
    .err { color: #d92d20; font-size: 12.5px; margin-top: 3px; }
    .alert { padding: 9px 12px; border-radius: 8px; font-size: 13.5px; margin-bottom: 12px; background: #fef3f2; color: #b42318; }
    .actions { display: flex; gap: 10px; justify-content: flex-end; }
    button { font: inherit; font-size: 14px; font-weight: 600; padding: 9px 16px; border-radius: 8px; cursor: pointer; border: 1px solid #d0d5dd; background: #fff; color: #1a2233; }
    button.save { background: #2563eb; border-color: #2563eb; color: #fff; }
    button:disabled { opacity: .6; cursor: not-allowed; }
  `,
  template: `
    <form class="panel" [formGroup]="form" (ngSubmit)="save()" novalidate>
      <h3>Edit footer</h3>
      <p class="sub">Shown at the bottom of your recruitment website. Leave a field empty to hide it.</p>
      @if (error) { <div class="alert" role="alert">{{ error }}</div> }

      <div class="row">
        <div class="f"><label for="fe-company">Company Name</label>
          <input id="fe-company" formControlName="companyName" placeholder="Enter company name" [class.invalid]="bad('companyName')" />
          @if (bad('companyName')) { <span class="err">Company name must be 150 characters or fewer.</span> }</div>
        <div class="f"><label for="fe-web">Website URL</label>
          <input id="fe-web" formControlName="websiteUrl" placeholder="https://www.example.com" [class.invalid]="bad('websiteUrl')" />
          @if (bad('websiteUrl')) { <span class="err">Enter a full web address, like https://www.example.com.</span> }</div>
      </div>
      <div class="f"><label for="fe-addr">Company Address</label>
        <textarea id="fe-addr" formControlName="address" placeholder="Street, city, postal code" [class.invalid]="bad('address')"></textarea>
        @if (bad('address')) { <span class="err">Address must be 300 characters or fewer.</span> }</div>
      <div class="row">
        <div class="f"><label for="fe-mail">Contact Email</label>
          <input id="fe-mail" type="email" formControlName="contactEmail" placeholder="careers@example.com" [class.invalid]="bad('contactEmail')" />
          @if (bad('contactEmail')) { <span class="err">Enter a valid email address.</span> }</div>
        <div class="f"><label for="fe-phone">Phone Number</label>
          <input id="fe-phone" formControlName="phone" placeholder="+91 98765 43210" [class.invalid]="bad('phone')" />
          @if (bad('phone')) { <span class="err">Phone number can only contain digits, spaces, + ( ) - and .</span> }</div>
      </div>
      <div class="f"><label for="fe-copy">Copyright Text</label>
        <input id="fe-copy" formControlName="copyrightText" placeholder="Enter copyright text" [class.invalid]="bad('copyrightText')" />
        @if (bad('copyrightText')) { <span class="err">Copyright text must be 200 characters or fewer.</span> }</div>
      <div class="f"><label for="fe-custom">Additional Footer Text</label>
        <textarea id="fe-custom" formControlName="customText" placeholder="e.g. We are an equal opportunity employer." [class.invalid]="bad('customText')"></textarea>
        @if (bad('customText')) { <span class="err">Additional text must be 500 characters or fewer.</span> }</div>

      <div class="actions">
        <button type="button" [disabled]="saving" (click)="cancel.emit()">Cancel</button>
        <button type="submit" class="save" [disabled]="saving">{{ saving ? 'Saving…' : 'Save footer' }}</button>
      </div>
    </form>
  `,
})
export class FooterEditorComponent implements OnInit {
  private fb = inject(FormBuilder);
  private service = inject(PublicAppService);

  @Input({ required: true }) slug = '';
  @Input() footer: PublicFooter | null | undefined;
  @Output() saved = new EventEmitter<PublicFooter>();
  @Output() cancel = new EventEmitter<void>();

  saving = false;
  error = '';

  form = this.fb.nonNullable.group({
    companyName: ['', Validators.maxLength(150)],
    address: ['', Validators.maxLength(300)],
    contactEmail: ['', [Validators.maxLength(150), optionalEmail]],
    phone: ['', [Validators.maxLength(30), Validators.pattern(/^[+0-9 ()\-.]*$/)]],
    websiteUrl: ['', [Validators.maxLength(200), optionalUrl]],
    copyrightText: ['', Validators.maxLength(200)],
    customText: ['', Validators.maxLength(500)],
  });

  ngOnInit() {
    const f = this.footer;
    this.form.patchValue({
      companyName: f?.companyName ?? '', address: f?.address ?? '', contactEmail: f?.contactEmail ?? '',
      phone: f?.phone ?? '', websiteUrl: f?.websiteUrl ?? '', copyrightText: f?.copyrightText ?? '', customText: f?.customText ?? '',
    });
  }

  bad(name: keyof PublicFooter) {
    const c = this.form.controls[name];
    return c.invalid && (c.dirty || c.touched);
  }

  save() {
    if (this.saving) return;
    if (this.form.invalid) { this.form.markAllAsTouched(); this.error = 'Fix the highlighted fields before saving.'; return; }
    const v = this.form.getRawValue();
    const body: PublicFooter = {
      companyName: v.companyName.trim(), address: v.address.trim(), contactEmail: v.contactEmail.trim(),
      phone: v.phone.trim(), websiteUrl: v.websiteUrl.trim(), copyrightText: v.copyrightText.trim(), customText: v.customText.trim(),
    };
    this.saving = true; this.error = '';
    this.service.updateFooter(this.slug, body).subscribe({
      next: (saved) => { this.saving = false; this.saved.emit(saved ?? body); },
      error: (err: HttpErrorResponse) => {
        this.saving = false;
        this.error = errorText(err);
      },
    });
  }
}
