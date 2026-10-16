import { AbstractControl, ValidationErrors } from '@angular/forms';

const EMAIL = /^[^@\s]+@[^@\s]+\.[^@\s]+$/;
const HEX = /^#[0-9a-fA-F]{6}$/;

/** A required 6-digit HEX colour such as #2563EB. */
export const hexColor = (c: AbstractControl): ValidationErrors | null =>
  HEX.test(String(c.value ?? '').trim()) ? null : { hex: true };

/** Empty is fine; otherwise it must look like an email address. */
export const optionalEmail = (c: AbstractControl): ValidationErrors | null => {
  const v = String(c.value ?? '').trim();
  return !v || EMAIL.test(v) ? null : { email: true };
};

/** Empty is fine; otherwise it must be a full http(s) address such as https://example.com. */
export const optionalUrl = (c: AbstractControl): ValidationErrors | null => {
  const v = String(c.value ?? '').trim();
  if (!v) return null;
  try {
    const u = new URL(v);
    return (u.protocol === 'http:' || u.protocol === 'https:') && u.hostname.includes('.') ? null : { url: true };
  } catch { return { url: true }; }
};

/** Empty is fine; otherwise digits, spaces and + ( ) - . only (same rule as the backend). */
export const optionalPhone = (c: AbstractControl): ValidationErrors | null => {
  const v = String(c.value ?? '').trim();
  return !v || /^[+0-9 ()\-.]{3,30}$/.test(v) ? null : { phone: true };
};