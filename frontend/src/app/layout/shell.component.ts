import { ChangeDetectionStrategy, Component, computed, effect, inject, signal } from '@angular/core';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';

import { AuthService } from '../core/auth/auth.service';
import { RewardService } from '../core/ui/reward.service';
import { ThemeService } from '../core/ui/theme.service';
import { IconComponent } from '../shared/icon.component';
import { ToastsComponent } from './toasts.component';

interface NavItem {
  path: string;
  label: string;
  icon: string;
  adminOnly?: boolean;
}

/** Structure de l'application connectée : navigation latérale, barre mobile, notifications. */
@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, IconComponent, ToastsComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './shell.component.html',
  styleUrl: './shell.component.scss',
})
export class ShellComponent {
  private readonly auth = inject(AuthService);
  private readonly rewards = inject(RewardService);
  private readonly router = inject(Router);
  private readonly theme = inject(ThemeService);

  readonly user = this.auth.user;
  readonly drawerOpen = signal(false);

  readonly nav: NavItem[] = [
    { path: '/accueil', label: 'Accueil', icon: 'home' },
    { path: '/parcours', label: 'Parcours', icon: 'route' },
    { path: '/exercices', label: 'Exercices', icon: 'code' },
    { path: '/laboratoire-sql', label: 'Laboratoire SQL', icon: 'database' },
    { path: '/modelisation', label: 'Modélisation', icon: 'diagram' },
    { path: '/quiz', label: 'Quiz', icon: 'quiz' },
    { path: '/examen-cda', label: 'Examen CDA', icon: 'graduation' },
    { path: '/progression', label: 'Progression', icon: 'chart' },
    { path: '/profil', label: 'Profil', icon: 'user' },
    { path: '/admin', label: 'Administration', icon: 'shield', adminOnly: true },
  ];

  readonly tabs: NavItem[] = [
    { path: '/accueil', label: 'Accueil', icon: 'home' },
    { path: '/parcours', label: 'Parcours', icon: 'route' },
    { path: '/quiz', label: 'Quiz', icon: 'quiz' },
    { path: '/laboratoire-sql', label: 'Labo SQL', icon: 'database' },
  ];

  readonly visibleNav = computed(() => this.nav.filter((i) => !i.adminOnly || this.user()?.role === 'ADMIN'));
  readonly initials = computed(() =>
    (this.user()?.displayName ?? '?')
      .split(/\s+/)
      .map((p) => p[0])
      .join('')
      .slice(0, 2)
      .toUpperCase(),
  );
  readonly levelPercent = computed(() => {
    const u = this.user();
    if (!u) {
      return 0;
    }
    const span = u.nextLevelXp - u.levelStartXp;
    return span > 0 ? Math.min(100, Math.round(((u.xp - u.levelStartXp) / span) * 100)) : 100;
  });

  constructor() {
    effect(() => this.theme.apply(this.user()?.theme));
    // Après une récompense, le profil (XP, niveau, série) est rechargé
    effect(() => {
      if (this.rewards.lastReward()) {
        this.auth.reloadUser().subscribe({ error: () => undefined });
      }
    });
    this.router.events.pipe(filter((e) => e instanceof NavigationEnd)).subscribe(() => this.drawerOpen.set(false));
  }

  logout(): void {
    this.auth.logout();
  }
}
