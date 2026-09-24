import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

import { PwaBannersComponent } from './layout/pwa-banners.component';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, PwaBannersComponent],
  templateUrl: './app.component.html',
  styleUrl: './app.component.scss'
})
export class AppComponent {}
