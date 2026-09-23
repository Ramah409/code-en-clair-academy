import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { ApiService } from '../../core/api/api.service';
import { toFormError } from '../../core/auth/api-error';
import { AuthService } from '../../core/auth/auth.service';
import { RewardService } from '../../core/ui/reward.service';
import { ThemeService } from '../../core/ui/theme.service';
import { IconComponent } from '../../shared/icon.component';

@Component({
  selector: 'app-profile',
  imports: [FormsModule, IconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './profile.component.html',
  styleUrl: './profile.component.scss',
})
export class ProfileComponent {
  private readonly api = inject(ApiService);
  private readonly auth = inject(AuthService);
  private readonly rewards = inject(RewardService);
  private readonly theme = inject(ThemeService);

  readonly user = this.auth.user;
  readonly goals = [10, 15, 20, 30, 45, 60, 90];

  readonly displayName = signal(this.user()?.displayName ?? '');
  readonly dailyGoal = signal(this.user()?.dailyGoalMinutes ?? 20);
  readonly themeChoice = signal(this.user()?.theme ?? 'SYSTEM');
  readonly profileError = signal<string | null>(null);
  readonly savingProfile = signal(false);

  readonly currentPassword = signal('');
  readonly newPassword = signal('');
  readonly passwordError = signal<string | null>(null);
  readonly savingPassword = signal(false);

  readonly deletePassword = signal('');
  readonly deleteConfirm = signal(false);
  readonly deleteError = signal<string | null>(null);

  saveProfile(): void {
    this.savingProfile.set(true);
    this.profileError.set(null);
    this.api
      .updateProfile({ displayName: this.displayName().trim(), dailyGoalMinutes: this.dailyGoal(), theme: this.themeChoice() })
      .subscribe({
        next: () => {
          this.savingProfile.set(false);
          this.theme.apply(this.themeChoice());
          this.auth.reloadUser().subscribe();
          this.rewards.info('Profil enregistré');
        },
        error: (e: unknown) => {
          this.savingProfile.set(false);
          const err = toFormError(e);
          this.profileError.set(Object.values(err.fields)[0] ?? err.message);
        },
      });
  }

  previewTheme(value: string): void {
    this.themeChoice.set(value);
    this.theme.apply(value);
  }

  changePassword(): void {
    this.savingPassword.set(true);
    this.passwordError.set(null);
    this.api.changePassword({ currentPassword: this.currentPassword(), newPassword: this.newPassword() }).subscribe({
      next: () => {
        this.savingPassword.set(false);
        this.currentPassword.set('');
        this.newPassword.set('');
        this.rewards.info('Mot de passe modifié');
      },
      error: (e: unknown) => {
        this.savingPassword.set(false);
        const err = toFormError(e);
        this.passwordError.set(Object.values(err.fields)[0] ?? err.message);
      },
    });
  }

  deleteAccount(): void {
    this.deleteError.set(null);
    this.api.deleteAccount(this.deletePassword()).subscribe({
      next: () => this.auth.endSession(),
      error: (e: unknown) => this.deleteError.set(toFormError(e).message),
    });
  }
}
