/**
 * Resume upload rules used by the screens. The server checks the same rules again,
 * so keep RESUME_MAX_MB equal to `app.resume.max-size-mb` in the backend application.properties.
 */
export const RESUME_MAX_MB = 5;
export const RESUME_MAX_BYTES = RESUME_MAX_MB * 1024 * 1024;
export const RESUME_EXTENSIONS = /\.(pdf|doc|docx)$/i;
export const RESUME_ACCEPT = '.pdf,.doc,.docx';

/** Returns a message when the file cannot be uploaded, or null when it is fine. */
export function resumeFileError(file: File | null | undefined): string | null {
  if (!file) return 'Please choose a resume file.';
  if (file.size === 0) return 'The selected file is empty.';
  if (!RESUME_EXTENSIONS.test(file.name)) return 'The resume must be a PDF, DOC or DOCX file.';
  if (file.size > RESUME_MAX_BYTES) return `The resume must be ${RESUME_MAX_MB} MB or smaller.`;
  return null;
}
