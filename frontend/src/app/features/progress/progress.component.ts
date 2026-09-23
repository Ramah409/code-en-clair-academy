import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { ApiService } from '../../core/api/api.service';
import { ProgressOverview } from '../../core/api/models';
import { IconComponent } from '../../shared/icon.component';

@Component({
  selector: 'app-progress',
  imports: [RouterLink, IconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './progress.component.html',
  styleUrl: './progress.component.scss',
})
export class ProgressComponent implements OnInit {
  private readonly api = inject(ApiService);
  readonly data = signal<ProgressOverview | null>(null);

  /** Calendrier d'activité : colonnes = semaines (lundi en haut). */
  readonly calendar = computed(() => {
    const days = this.data()?.activity ?? [];
    if (!days.length) {
      return [];
    }
    const first = new Date(days[0].date + 'T12:00:00');
    const offset = (first.getDay() + 6) % 7;
    const cells: ({ date: string; minutes: number; xp: number; level: number } | null)[] = Array(offset).fill(null);
    for (const d of days) {
      const level = d.minutes === 0 ? 0 : d.minutes < 10 ? 1 : d.minutes < 20 ? 2 : d.minutes < 40 ? 3 : 4;
      cells.push({ date: d.date, minutes: d.minutes, xp: d.xp, level });
    }
    const weeks = [];
    for (let i = 0; i < cells.length; i += 7) {
      weeks.push(cells.slice(i, i + 7));
    }
    return weeks;
  });

  readonly activeDays = computed(() => (this.data()?.activity ?? []).filter((d) => d.minutes > 0).length);
  readonly earned = computed(() => (this.data()?.badges ?? []).filter((b) => b.earnedAt).length);

  ngOnInit(): void {
    this.api.progress().subscribe((d) => this.data.set(d));
  }

  hours(minutes: number): string {
    return minutes >= 60 ? `${Math.floor(minutes / 60)} h ${minutes % 60} min` : `${minutes} min`;
  }

  badgePercent(value: number, threshold: number): number {
    return Math.min(100, Math.round((value / threshold) * 100));
  }
}
