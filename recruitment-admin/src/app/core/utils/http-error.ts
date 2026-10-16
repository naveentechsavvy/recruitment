import { HttpErrorResponse } from '@angular/common/http';

/** Turns a backend error response into one readable message. */
export function errorText(err: HttpErrorResponse): string {
  if (err.status === 0) return 'Cannot reach the server. Is the Spring Boot backend running on port 8080?';
  const fields = err.error?.fieldErrors ? Object.values<string>(err.error.fieldErrors) : [];
  return fields[0] ?? err.error?.message ?? 'Something went wrong. Please try again.';
}
