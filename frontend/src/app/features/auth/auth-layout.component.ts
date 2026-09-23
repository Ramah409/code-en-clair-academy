import { Component, input } from '@angular/core';

/** Mise en page partagée des écrans de connexion et d'inscription. */
@Component({
  selector: 'app-auth-layout',
  templateUrl: './auth-layout.component.html',
  styleUrl: './auth-layout.component.scss',
})
export class AuthLayoutComponent {
  readonly heading = input.required<string>();
  readonly intro = input<string>('');
}
