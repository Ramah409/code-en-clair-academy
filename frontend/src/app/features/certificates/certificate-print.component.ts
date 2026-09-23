import { ChangeDetectionStrategy, Component, computed, effect, inject, input, signal, untracked } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';

import { ApiService } from '../../core/api/api.service';
import { Certificate } from '../../core/api/models';
import { toFormError } from '../../core/auth/api-error';
import { IconComponent } from '../../shared/icon.component';

/** Attestation au format imprimable (A4 paysage), avec son code et l'adresse de vérification. */
@Component({
  selector: 'app-certificate-print',
  imports: [DatePipe, RouterLink, IconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './certificate-print.component.html',
  styleUrl: './certificate-print.component.scss',
})
export class CertificatePrintComponent {
  private readonly api = inject(ApiService);

  readonly code = input.required<string>();
  readonly cert = signal<Certificate | null>(null);
  readonly error = signal<string | null>(null);

  readonly verifyUrl = computed(() => `${location.origin}/verification/${this.cert()?.code ?? ''}`);
  readonly chapters = computed<{ chapitre: string; meilleurScore?: number }[]>(
    () => this.cert()?.details?.chapitres ?? [],
  );
  readonly kindLabel = computed(() => {
    switch (this.cert()?.kind) {
      case 'GLOBALE':
        return 'Certification';
      case 'PARCOURS':
        return 'Attestation de parcours';
      default:
        return 'Attestation de niveau';
    }
  });

  constructor() {
    effect(() => {
      const code = this.code();
      untracked(() =>
        this.api.certificate(code).subscribe({
          next: (c) => this.cert.set(c),
          error: (e: unknown) => this.error.set(toFormError(e).message),
        }),
      );
    });
  }

  print(): void {
    window.print();
  }
}
