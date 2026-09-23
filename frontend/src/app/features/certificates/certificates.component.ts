import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';

import { ApiService } from '../../core/api/api.service';
import { CertificateGoal, CertificateOverview } from '../../core/api/models';
import { toFormError } from '../../core/auth/api-error';
import { RewardService } from '../../core/ui/reward.service';
import { IconComponent } from '../../shared/icon.component';

/** Mes attestations, et ce qu'il reste à faire pour obtenir les suivantes. */
@Component({
  selector: 'app-certificates',
  imports: [DatePipe, RouterLink, IconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './certificates.component.html',
  styleUrl: './certificates.component.scss',
})
export class CertificatesComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly rewards = inject(RewardService);

  readonly data = signal<CertificateOverview | null>(null);
  readonly error = signal<string | null>(null);

  readonly global = computed(() => this.data()?.goals.find((g) => g.kind === 'GLOBALE') ?? null);
  /** Objectifs regroupés par parcours (niveaux puis parcours complet). */
  readonly byCourse = computed(() => {
    const groups = new Map<string, CertificateGoal[]>();
    for (const g of this.data()?.goals ?? []) {
      if (g.kind === 'GLOBALE' || !g.courseSlug) {
        continue;
      }
      groups.set(g.courseSlug, [...(groups.get(g.courseSlug) ?? []), g]);
    }
    return [...groups.entries()].map(([slug, goals]) => ({
      slug,
      title: goals.find((g) => g.kind === 'PARCOURS')?.title.replace(' – parcours complet', '') ?? slug,
      goals,
    }));
  });

  ngOnInit(): void {
    this.api.certificates().subscribe({
      next: (d) => {
        this.data.set(d);
        for (const c of d.newlyIssued) {
          this.rewards.certificate(c.title);
        }
      },
      error: (e: unknown) => this.error.set(toFormError(e).message),
    });
  }

  percent(g: CertificateGoal): number {
    return g.total ? Math.round((100 * g.done) / g.total) : 0;
  }

  label(g: CertificateGoal): string {
    const text = g.kind === 'PARCOURS' ? 'Parcours complet' : (g.title.split(' – ').pop() ?? g.title);
    return text.charAt(0).toUpperCase() + text.slice(1);
  }
}
