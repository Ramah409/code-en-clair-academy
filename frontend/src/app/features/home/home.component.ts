import { Component, OnInit, computed, inject } from '@angular/core';

import { AuthService } from '../../core/auth/auth.service';

@Component({
  selector: 'app-home',
  templateUrl: './home.component.html',
  styleUrl: './home.component.scss',
})
export class HomeComponent implements OnInit {
  private readonly auth = inject(AuthService);

  readonly user = this.auth.user;

  /** Progression dans le niveau en cours, en pourcentage. */
  readonly levelProgress = computed(() => {
    const u = this.user();
    if (!u) {
      return 0;
    }
    const span = u.nextLevelXp - u.levelStartXp;
    return span > 0 ? Math.min(100, Math.round(((u.xp - u.levelStartXp) / span) * 100)) : 100;
  });

  ngOnInit(): void {
    this.auth.reloadUser().subscribe({ error: () => undefined });
  }

  logout(): void {
    this.auth.logout();
  }
}
