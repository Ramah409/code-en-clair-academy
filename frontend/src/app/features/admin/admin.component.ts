import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

/** Espace d'administration : onglets vers la gestion des contenus et des comptes. */
@Component({
  selector: 'app-admin',
  imports: [RouterLink, RouterLinkActive, RouterOutlet],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <header class="page-header">
      <h1>Administration</h1>
      <p class="lead">Gérer les parcours, les leçons, les exercices, les QCM et les comptes.</p>
    </header>
    <nav class="adm-tabs" aria-label="Sections de l'administration">
      <a routerLink="parcours" routerLinkActive="active" ariaCurrentWhenActive="page">Parcours et leçons</a>
      <a routerLink="questions" routerLinkActive="active" ariaCurrentWhenActive="page">Questions (QCM)</a>
      <a routerLink="exercices" routerLinkActive="active" ariaCurrentWhenActive="page">Exercices</a>
      <a routerLink="comptes" routerLinkActive="active" ariaCurrentWhenActive="page">Comptes</a>
    </nav>
    <router-outlet />
  `,
})
export class AdminComponent {}
