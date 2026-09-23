import { Component, inject, signal } from '@angular/core';
import { AbstractControl, NonNullableFormBuilder, ReactiveFormsModule, ValidationErrors, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

import { FormError, toFormError } from '../../core/auth/api-error';
import { AuthService } from '../../core/auth/auth.service';
import { AuthLayoutComponent } from './auth-layout.component';

/** Même règle que le backend (PasswordRules) : 10 à 72 caractères, au moins une lettre et un chiffre. */
const PASSWORD_PATTERN = /^(?=.*\p{L})(?=.*\d).{10,72}$/u;

function passwordsMatch(group: AbstractControl): ValidationErrors | null {
  const { password, confirmPassword } = group.value as { password: string; confirmPassword: string };
  return confirmPassword && password !== confirmPassword ? { mismatch: true } : null;
}

/** Erreurs globales du backend (@AssertTrue) rattachées au champ concerné. */
const FIELD_ALIASES = { passwordConfirmed: 'confirmPassword', termsAccepted: 'acceptTerms' };

@Component({
  selector: 'app-register',
  imports: [ReactiveFormsModule, RouterLink, AuthLayoutComponent],
  templateUrl: './register.component.html',
  styleUrl: './auth-form.scss',
})
export class RegisterComponent {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  readonly form = inject(NonNullableFormBuilder).group(
    {
      displayName: ['', [Validators.required, Validators.minLength(2), Validators.maxLength(60)]],
      email: ['', [Validators.required, Validators.email, Validators.maxLength(254)]],
      password: ['', [Validators.required, Validators.pattern(PASSWORD_PATTERN)]],
      confirmPassword: ['', Validators.required],
      acceptTerms: [false, Validators.requiredTrue],
    },
    { validators: passwordsMatch },
  );

  readonly submitting = signal(false);
  readonly submitted = signal(false);
  readonly showPassword = signal(false);
  readonly error = signal<FormError | null>(null);

  invalid(name: keyof typeof this.form.controls): boolean {
    const control = this.form.controls[name];
    const mismatch = name === 'confirmPassword' && this.form.hasError('mismatch');
    return (control.invalid || mismatch || !!this.serverError(name)) && (control.touched || this.submitted());
  }

  serverError(name: string): string | undefined {
    return this.error()?.fields[name];
  }

  submit(): void {
    this.submitted.set(true);
    this.error.set(null);
    if (this.form.invalid || this.submitting()) {
      return;
    }

    this.submitting.set(true);
    this.auth.register(this.form.getRawValue()).subscribe({
      next: () => void this.router.navigateByUrl('/accueil'),
      error: (err: unknown) => {
        this.error.set(toFormError(err, FIELD_ALIASES));
        this.submitting.set(false);
      },
    });
  }
}
