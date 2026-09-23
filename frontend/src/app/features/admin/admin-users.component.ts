import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';

import { AuthService } from '../../core/auth/auth.service';
import { toFormError } from '../../core/auth/api-error';
import { IconComponent } from '../../shared/icon.component';
import { AdminApiService, AdminUser, Page } from './admin-api.service';

/** Comptes : recherche, rôle, activation et suppression. */
@Component({
  selector: 'app-admin-users',
  imports: [DatePipe, FormsModule, IconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (error()) { <div class="alert alert--error" role="alert">{{ error() }}</div> }
    <section class="adm-card" aria-labelledby="u-title">
      <h2 id="u-title">Comptes</h2>
      <form class="adm-row" (ngSubmit)="load(0)">
        <input class="input" type="search" name="s" placeholder="Nom ou e-mail" aria-label="Rechercher un compte"
               [ngModel]="search()" (ngModelChange)="search.set($event)" />
        <button type="submit" class="btn btn--secondary"><app-icon name="search" [size]="16" /> Rechercher</button>
      </form>
      @if (page(); as p) {
        <div class="result__scroll">
          <table class="adm-table">
            <thead><tr><th>Nom</th><th>E-mail</th><th>Rôle</th><th>XP</th><th>Inscription</th><th>État</th><th></th></tr></thead>
            <tbody>
              @for (u of p.content; track u.id) {
                <tr [class.is-off]="!u.enabled">
                  <td>{{ u.displayName }}</td>
                  <td>{{ u.email }}</td>
                  <td>
                    <select class="select select--sm" [attr.aria-label]="'Rôle de ' + u.displayName" [ngModel]="u.role"
                            (ngModelChange)="role(u, $event)" [disabled]="u.id === me()">
                      <option value="USER">Apprenante</option><option value="ADMIN">Administratrice</option>
                    </select>
                  </td>
                  <td>{{ u.xp }} (niv. {{ u.level }})</td>
                  <td>{{ u.createdAt | date: 'd MMM y' }}</td>
                  <td>
                    <button type="button" class="btn btn--ghost btn--sm" (click)="toggle(u)" [disabled]="u.id === me()">
                      {{ u.enabled ? 'Actif — désactiver' : 'Désactivé — activer' }}
                    </button>
                  </td>
                  <td>
                    <button type="button" class="icon-btn" (click)="remove(u)" [disabled]="u.id === me()" [attr.aria-label]="'Supprimer ' + u.displayName">
                      <app-icon name="trash" [size]="16" />
                    </button>
                  </td>
                </tr>
              }
            </tbody>
          </table>
        </div>
        <div class="row-actions">
          <button type="button" class="btn btn--ghost btn--sm" [disabled]="p.page === 0" (click)="load(p.page - 1)">Précédent</button>
          <span class="muted">Page {{ p.page + 1 }} / {{ p.totalPages || 1 }} — {{ p.totalElements }} comptes</span>
          <button type="button" class="btn btn--ghost btn--sm" [disabled]="p.page + 1 >= p.totalPages" (click)="load(p.page + 1)">Suivant</button>
        </div>
      }
    </section>
  `,
})
export class AdminUsersComponent implements OnInit {
  private readonly api = inject(AdminApiService);
  private readonly auth = inject(AuthService);

  readonly search = signal('');
  readonly page = signal<Page<AdminUser> | null>(null);
  readonly error = signal<string | null>(null);
  readonly me = () => this.auth.user()?.id;

  ngOnInit(): void {
    this.load(0);
  }

  load(page: number): void {
    this.api.users(this.search(), page).subscribe({
      next: (p) => this.page.set(p),
      error: (e: unknown) => this.error.set(toFormError(e).message),
    });
  }

  private replace(u: AdminUser): void {
    this.page.update((p) => (p ? { ...p, content: p.content.map((x) => (x.id === u.id ? u : x)) } : p));
  }

  role(u: AdminUser, role: 'USER' | 'ADMIN'): void {
    this.api.changeRole(u.id, role).subscribe({ next: (x) => this.replace(x), error: (e: unknown) => this.error.set(toFormError(e).message) });
  }

  toggle(u: AdminUser): void {
    this.api.changeEnabled(u.id, !u.enabled).subscribe({ next: (x) => this.replace(x), error: (e: unknown) => this.error.set(toFormError(e).message) });
  }

  remove(u: AdminUser): void {
    if (!confirm(`Supprimer définitivement le compte de ${u.displayName} et toute sa progression ?`)) {
      return;
    }
    this.api.deleteUser(u.id).subscribe({ next: () => this.load(this.page()?.page ?? 0), error: (e: unknown) => this.error.set(toFormError(e).message) });
  }
}
