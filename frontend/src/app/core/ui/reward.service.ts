import { Injectable, signal } from '@angular/core';

import { Reward } from '../api/models';

export interface Toast {
  id: number;
  kind: 'xp' | 'level' | 'badge' | 'info' | 'error';
  title: string;
  detail?: string;
  icon: string;
}

/** Notifications de récompense (XP, niveau, badge) et messages courts. */
@Injectable({ providedIn: 'root' })
export class RewardService {
  private next = 1;
  readonly toasts = signal<Toast[]>([]);
  /** XP totale connue côté interface, mise à jour après chaque récompense. */
  readonly lastReward = signal<Reward | null>(null);

  celebrate(reward: Reward | null | undefined): void {
    if (!reward) {
      return;
    }
    if (reward.totalXp > 0) {
      this.lastReward.set(reward);
    }
    if (reward.xpEarned > 0) {
      this.push({ kind: 'xp', title: `+${reward.xpEarned} XP`, icon: 'bolt' });
    }
    if (reward.levelUp) {
      this.push({ kind: 'level', title: `Niveau ${reward.level} atteint`, detail: 'Continue sur ta lancée.', icon: 'star' });
    }
    for (const badge of reward.newBadges ?? []) {
      this.push({ kind: 'badge', title: `Badge « ${badge.name} »`, detail: badge.description, icon: badge.icon });
    }
  }

  /** Nouvelle attestation délivrée (affichée aussi longtemps qu'un badge). */
  certificate(title: string): void {
    this.push({ kind: 'badge', title: 'Nouvelle attestation', detail: title, icon: 'medal' });
  }

  info(title: string, detail?: string): void {
    this.push({ kind: 'info', title, detail, icon: 'info' });
  }

  error(title: string, detail?: string): void {
    this.push({ kind: 'error', title, detail, icon: 'alert' });
  }

  dismiss(id: number): void {
    this.toasts.update((list) => list.filter((t) => t.id !== id));
  }

  private push(toast: Omit<Toast, 'id'>): void {
    const id = this.next++;
    this.toasts.update((list) => [...list, { ...toast, id }].slice(-4));
    setTimeout(() => this.dismiss(id), toast.kind === 'badge' || toast.kind === 'level' ? 6000 : 3500);
  }
}
