import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { ApiService } from '../../core/api/api.service';
import { Dashboard, LEVEL_LABELS } from '../../core/api/models';
import { IconComponent } from '../../shared/icon.component';
import { ProgressRingComponent } from '../../shared/progress-ring.component';

@Component({
  selector: 'app-home',
  imports: [RouterLink, IconComponent, ProgressRingComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './home.component.html',
  styleUrl: './home.component.scss',
})
export class HomeComponent implements OnInit {
  private readonly api = inject(ApiService);

  readonly data = signal<Dashboard | null>(null);
  readonly error = signal(false);
  readonly levels = LEVEL_LABELS;

  readonly greeting = computed(() => {
    const h = new Date().getHours();
    return h < 5 ? 'Bonsoir' : h < 18 ? 'Bonjour' : 'Bonsoir';
  });

  readonly firstName = computed(() => this.data()?.displayName.split(' ')[0] ?? '');

  readonly week = computed(() => {
    const days = this.data()?.lastDays ?? [];
    const max = Math.max(1, ...days.map((d) => d.minutes));
    const fmt = new Intl.DateTimeFormat('fr-FR', { weekday: 'short' });
    return days.map((d) => ({
      ...d,
      label: fmt.format(new Date(d.date + 'T12:00:00')).replace('.', ''),
      height: Math.max(4, Math.round((d.minutes / max) * 100)),
    }));
  });

  readonly started = computed(() => (this.data()?.courses ?? []).filter((c) => c.lessonsDone > 0));
  readonly suggested = computed(() => (this.data()?.courses ?? []).filter((c) => c.lessonsDone === 0).slice(0, 4));

  ngOnInit(): void {
    this.api.dashboard().subscribe({
      next: (d) => this.data.set(d),
      error: () => this.error.set(true),
    });
  }
}
