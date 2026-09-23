import { Injectable } from '@angular/core';

/** Applique le thème choisi (clair, sombre ou celui du système) sur l'élément racine. */
@Injectable({ providedIn: 'root' })
export class ThemeService {
  apply(theme: string | undefined): void {
    const root = document.documentElement;
    if (theme === 'LIGHT') {
      root.dataset['theme'] = 'light';
    } else if (theme === 'DARK') {
      root.dataset['theme'] = 'dark';
    } else {
      delete root.dataset['theme'];
    }
  }
}
