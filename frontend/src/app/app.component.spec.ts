import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { AppComponent } from './app.component';

describe('AppComponent', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AppComponent],
      providers: [provideRouter([])],
    }).compileComponents();
  });

  it('crée l\'application', () => {
    const fixture = TestBed.createComponent(AppComponent);
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('propose un lien d\'évitement vers le contenu', () => {
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();
    const link = (fixture.nativeElement as HTMLElement).querySelector('a.skip-link');
    expect(link?.getAttribute('href')).toBe('#contenu');
  });
});
