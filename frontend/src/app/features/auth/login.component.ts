import { Component, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';

import { FormError, toFormError } from '../../core/auth/api-error';
import { AuthService } from '../../core/auth/auth.service';
import { AuthLayoutComponent } from './auth-layout.component';

@Component({
  selector: 'app-login',
  imports: [ReactiveFormsModule, RouterLink, AuthLayoutComponent],
  templateUrl: './login.component.html',
  styleUrl: './auth-form.scss',
})
export class LoginComponent {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  readonly form = inject(NonNullableFormBuilder).group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', Validators.required],
  });

  readonly submitting = signal(false);
  readonly submitted = signal(false);
  readonly showPassword = signal(false);
  readonly error = signal<FormError | null>(null);

  invalid(name: keyof typeof this.form.controls): boolean {
    const control = this.form.controls[name];
    return control.invalid && (control.touched || this.submitted());
  }

  submit(): void {
    this.submitted.set(true);
    this.error.set(null);
    if (this.form.invalid || this.submitting()) {
      return;
    }

    this.submitting.set(true);
    this.auth.login(this.form.getRawValue()).subscribe({
      next: () => void this.router.navigateByUrl(this.returnUrl()),
      error: (err: unknown) => {
        this.error.set(toFormError(err));
        this.submitting.set(false);
      },
    });
  }

  /** Retour vers la page demandée avant la connexion, uniquement si c'est une adresse interne. */
  private returnUrl(): string {
    const url = this.route.snapshot.queryParamMap.get('retour');
    return url?.startsWith('/') && !url.startsWith('//') ? url : '/accueil';
  }
}
