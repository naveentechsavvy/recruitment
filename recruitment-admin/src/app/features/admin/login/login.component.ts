import { Component, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';
import { LOGO_FULL } from '../../../shared/brand';

@Component({
  selector: 'app-login',
  imports: [ReactiveFormsModule],
  templateUrl: './login.component.html',
  styleUrl: './login.component.scss',
})
export class LoginComponent {
  logoFull = LOGO_FULL;

  private fb = inject(FormBuilder);
  private auth = inject(AuthService);
  private router = inject(Router);

  error = '';
  loading = false;

  // Removed hardcoded Admin email and password.
  form = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(4)]],
  });

  submit() {
    this.form.markAllAsTouched();

    if (this.form.invalid || this.loading) return;

    this.error = '';
    this.loading = true;

    const { email, password } = this.form.getRawValue();

    this.auth.login(email, password).subscribe({
      next: () => {
        this.loading = false;
        this.router.navigateByUrl('/admin/dashboard');
      },
      error: (err) => {
        this.loading = false;

        if (err?.status === 401) {
          this.error = 'Invalid email or password.';
        } else if (err?.status === 403) {
          this.error =
            err?.error?.message ||
            'You do not have access to this portal.';
        } else {
          this.error = 'Cannot reach the server. Is the backend running?';
        }
      },
    });
  }

  invalid(name: 'email' | 'password') {
    const c = this.form.controls[name];
    return c.invalid && c.touched;
  }
}
