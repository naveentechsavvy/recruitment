import { Component, ElementRef, ViewChild, inject } from '@angular/core';
import { FormBuilder, FormControl, ReactiveFormsModule, Validators, AbstractControl, ValidationErrors } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import {
  ClientConfigurationInput, EMPTY_FOOTER, FooterSettings, PortalColors, StageId, STAGE_OPTIONS, THEME_OPTIONS, ThemeId,
  ThemeOption, isHex, portalColors, sortStages,
} from '../../../core/models/client-configuration.model';
import { ClientConfigurationService } from '../../../core/services/client-configuration.service';
import { WorkflowPreviewComponent } from '../../../shared/components/workflow-preview/workflow-preview.component';
import { hexColor, optionalEmail, optionalPhone, optionalUrl } from '../../../core/utils/form-validators';


const MAX_LOGO_BYTES = 500 * 1024;

const atLeastOne = (c: AbstractControl): ValidationErrors | null =>
  Array.isArray(c.value) && c.value.length > 0 ? null : { required: true };

@Component({
  selector: 'app-client-form',
  imports: [ReactiveFormsModule, RouterLink, WorkflowPreviewComponent],
  templateUrl: './client-form.component.html',
  styleUrl: './client-form.component.scss',
})
export class ClientFormComponent {
  private fb = inject(FormBuilder);
  private service = inject(ClientConfigurationService);
  private router = inject(Router);

  @ViewChild('previewCard') previewCard?: ElementRef<HTMLElement>;

  readonly stageOptions = STAGE_OPTIONS;
  readonly themes = THEME_OPTIONS;

  /**
   * STEP 4: older stages (Technical Round, Rejected) are hidden, unless this client already uses them.
   * They stay in the list for that client so saving the form never silently removes them.
   */
  private legacyInUse = new Set<StageId>();
  get visibleStages() {
    return STAGE_OPTIONS.filter((s) => !s.legacy || this.legacyInUse.has(s.id));
  }

  clientId: string | null = inject(ActivatedRoute).snapshot.paramMap.get('id');
  get isEdit() { return !!this.clientId; }

  loading = this.isEdit;
  /** True when the saved application has already been generated (taken from the backend, not guessed). Then the form shows Save. */
  isGenerated = false;
  saving = false;
  message: { type: 'success' | 'error'; text: string } | null = null;
  logoError = '';

  /** Last valid colours, so the preview keeps working while a HEX value is half typed. */
  private lastValid = { headerColor: THEME_OPTIONS[0].color, footerColor: THEME_OPTIONS[0].secondary };

  form = this.fb.nonNullable.group({
    clientName: ['', [Validators.required, Validators.maxLength(100)]],
    logo: new FormControl<string | null>(null),
    description: ['', Validators.maxLength(500)],
    applicationName: ['', [Validators.required, Validators.maxLength(100)]],
    /** A preset id, or 'custom'. For a custom theme the two colours below are what counts. */
    theme: ['default' as ThemeId],
    headerColor: [THEME_OPTIONS[0].color, hexColor],
    footerColor: [THEME_OPTIONS[0].secondary, hexColor],
    footer: this.fb.nonNullable.group({
      companyName: ['', Validators.maxLength(150)],
      companyAddress: ['', Validators.maxLength(300)],
      contactEmail: ['', [Validators.maxLength(150), optionalEmail]],
      phone: ['', Validators.maxLength(30)],
      websiteUrl: ['', [Validators.maxLength(200), optionalUrl]],
      copyrightText: ['', Validators.maxLength(200)],
      customText: ['', Validators.maxLength(500)],
    }),
    recruitmentStages: [[] as StageId[], atLeastOne],
  });

  constructor() {
    // Keep the last valid colours so the preview never goes blank while someone is typing a HEX value.
    (['headerColor', 'footerColor'] as const).forEach((k) =>
      this.form.controls[k].valueChanges.subscribe((v) => { if (isHex(v)) this.lastValid[k] = v; }));

    if (this.clientId) {
      this.service.getClientById(this.clientId).subscribe({
        next: (c) => {
          this.form.patchValue({
            clientName: c.clientName, logo: c.logo, description: c.description, applicationName: c.applicationName,
            recruitmentStages: c.recruitmentStages, footer: c.footer,
          });
          // A saved colour pair that matches a preset selects that preset; anything else is shown as Custom colour.
          const preset = THEME_OPTIONS.find((t) => t.id === c.theme && this.sameColors(t, c.headerColor, c.footerColor));
          this.form.patchValue({ theme: preset ? preset.id : 'custom', headerColor: c.headerColor, footerColor: c.footerColor });
          this.isGenerated = c.applicationGenerated;
          this.legacyInUse = new Set(
            c.recruitmentStages.filter((id) => STAGE_OPTIONS.find((s) => s.id === id)?.legacy),
          );
          this.loading = false;
        },
        error: (err: HttpErrorResponse) => { this.loading = false; this.message = { type: 'error', text: err.status === 404 ? 'Client not found.' : this.errorText(err) }; },
      });
    }
  }

  // ---- theme colours ----
  private sameColors(t: ThemeOption, header: string, footer: string) {
    return t.color.toLowerCase() === header.toLowerCase() && t.secondary.toLowerCase() === footer.toLowerCase();
  }
  /** True when "Custom colour" is selected: the two colour pickers are shown. */
  get isCustom() { return this.form.controls.theme.value === 'custom'; }

  /** A preset applies both of its colours at once. */
  selectPreset(t: ThemeOption) {
    this.form.patchValue({ theme: t.id, headerColor: t.color, footerColor: t.secondary });
    this.form.markAsDirty();
  }
  /** Switching to Custom keeps the current colours as the starting point; each one can then be changed on its own. */
  selectCustom() { this.form.controls.theme.setValue('custom'); this.form.markAsDirty(); }

  /** From the visual colour picker (always a valid 7-character code). */
  onPick(key: 'headerColor' | 'footerColor', event: Event) {
    this.form.controls[key].setValue((event.target as HTMLInputElement).value);
    this.form.controls[key].markAsDirty();
    this.form.markAsDirty();
  }
  /** Lets people type "2563EB" without the #. */
  fixHex(key: 'headerColor' | 'footerColor') {
    const c = this.form.controls[key];
    const v = c.value.trim();
    if (/^[0-9a-fA-F]{6}$/.test(v)) c.setValue('#' + v);
  }
  hexError(key: 'headerColor' | 'footerColor') {
    const c = this.form.controls[key];
    return this.isCustom && c.invalid && (c.touched || c.dirty);
  }
  /** Value for <input type="color">, which only accepts a valid 7-character code. */
  pickerValue(key: 'headerColor' | 'footerColor') { return this.lastValid[key]; }

  /** Colours used by the live preview (updates as soon as either colour changes). */
  get colors(): PortalColors {
    return portalColors({ headerColor: this.lastValid.headerColor, footerColor: this.lastValid.footerColor });
  }

  // ---- footer ----
  get footerValue(): FooterSettings { return this.form.controls.footer.getRawValue(); }
  /** Footer fields that actually have content, in display order. The preview and the portal both skip empty ones. */
  get footerHasContent() { return Object.values(this.footerValue).some((v) => v.trim()); }
  showFooterError(name: keyof FooterSettings) {
    const c = this.form.controls.footer.controls[name];
    return c.invalid && (c.touched || c.dirty);
  }
  clearFooter() { this.form.controls.footer.reset({ ...EMPTY_FOOTER }); this.form.markAsDirty(); }

  // ---- stages ----
  get selected(): StageId[] { return this.form.controls.recruitmentStages.value; }
  isSelected(id: StageId) { return this.selected.includes(id); }
  toggleStage(id: StageId, checked: boolean) {
    const next = checked ? [...this.selected, id] : this.selected.filter((s) => s !== id);
    this.form.controls.recruitmentStages.setValue(sortStages(next)); // predefined order is enforced
    this.form.controls.recruitmentStages.markAsTouched();
    this.form.controls.recruitmentStages.markAsDirty();
  }

  // ---- logo ----
  onLogo(event: Event) {
    const file = (event.target as HTMLInputElement).files?.[0];
    this.logoError = '';
    if (!file) return;
    if (!file.type.startsWith('image/')) { this.logoError = 'Please choose an image file.'; return; }
    if (file.size > MAX_LOGO_BYTES) { this.logoError = 'Logo must be 500 KB or smaller.'; return; }
    const reader = new FileReader();
    reader.onload = () => { this.form.controls.logo.setValue(reader.result as string); this.form.markAsDirty(); };
    reader.readAsDataURL(file);
  }
  removeLogo() { this.form.controls.logo.setValue(null); }

  // ---- validation helpers ----
  showError(name: 'clientName' | 'applicationName' | 'recruitmentStages') {
    const c = this.form.controls[name];
    return c.invalid && (c.touched || c.dirty);
  }

  // ---- actions ----
  private value(): ClientConfigurationInput {
    const v = this.form.getRawValue();
    const f = v.footer;
    return {
      ...v,
      // A custom theme is saved as its header colour code, which the backend already accepts.
      theme: v.theme === 'custom' ? v.headerColor.trim() : v.theme,
      headerColor: v.headerColor.trim(),
      footerColor: v.footerColor.trim(),
      footer: {
        companyName: f.companyName.trim(), companyAddress: f.companyAddress.trim(), contactEmail: f.contactEmail.trim(),
        phone: f.phone.trim(), websiteUrl: f.websiteUrl.trim(), copyrightText: f.copyrightText.trim(), customText: f.customText.trim(),
      },
    };
  }

  saveDraft() {
    // A draft only needs a client name so the record is identifiable in the list.
    const name = this.form.controls.clientName;
    if (name.invalid) { name.markAsTouched(); this.fail('Enter a client name to save a draft.'); return; }
    // Colours and footer values are optional for a draft, but a value that is filled in must still be valid.
    const f = this.form.controls;
    if (f.headerColor.invalid || f.footerColor.invalid || f.footer.invalid) {
      this.form.markAllAsTouched(); this.fail('Fix the colour or footer values highlighted below before saving.'); return;
    }
    // Editing keeps the existing status; a brand-new record starts as Draft.
    const req = this.clientId
      ? this.service.updateClient(this.clientId, this.value())
      : this.service.saveDraft(this.value());
    this.persist(req, this.clientId ? 'Changes saved.' : 'Draft saved.', false);
  }

  /** Save for an application that already exists: updates that same record (same id), nothing new is created. */
  save() {
    this.form.markAllAsTouched();
    if (this.form.invalid) { this.fail('Please fix the highlighted fields before saving.'); return; }
    if (!this.clientId) return;
    this.persist(this.service.updateClient(this.clientId, this.value()), 'Changes saved.', false);
  }

  preview() {
    this.form.markAllAsTouched();
    this.previewCard?.nativeElement.scrollIntoView({ behavior: 'smooth', block: 'center' });
    this.previewCard?.nativeElement.classList.add('flash');
    setTimeout(() => this.previewCard?.nativeElement.classList.remove('flash'), 900);
  }

  generate() {
    this.form.markAllAsTouched();
    if (this.form.invalid) { this.fail('Please fix the highlighted fields before generating.'); return; }
    this.persist(
      this.service.generateConfiguration(this.value(), this.clientId ?? undefined),
      'Application configuration created successfully.',
      true,
    );
  }

  private persist(obs: ReturnType<ClientConfigurationService['saveDraft']>, text: string, goToView: boolean) {
    this.saving = true; this.message = null;
    obs.subscribe({
      next: (saved) => {
        this.saving = false;
        this.form.markAsPristine();
        this.message = { type: 'success', text };
        if (goToView) setTimeout(() => this.router.navigate(['/admin/clients/view', saved.id], { queryParams: { generated: 1 } }), 900);
        else if (!this.clientId) this.router.navigate(['/admin/clients']);
      },
      error: (err: HttpErrorResponse) => { this.saving = false; this.fail(this.errorText(err)); },
    });
  }
  /** Turns a backend error response into a readable message. */
  private errorText(err: HttpErrorResponse): string {
    if (err.status === 0) return 'Cannot reach the server. Is the Spring Boot backend running on port 8080?';
    const fields = err.error?.fieldErrors ? Object.values<string>(err.error.fieldErrors) : [];
    return fields[0] ?? err.error?.message ?? 'Something went wrong while saving. Please try again.';
  }
  private fail(text: string) { this.message = { type: 'error', text }; }
}
