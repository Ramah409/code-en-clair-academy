import { DestroyRef, Injectable, computed, inject, signal } from '@angular/core';
import { SwUpdate, VersionReadyEvent } from '@angular/service-worker';
import { filter } from 'rxjs';

/** Événement non standard de Chrome, Edge et Samsung Internet : proposition d'installation. */
interface BeforeInstallPromptEvent extends Event {
  prompt(): Promise<void>;
  readonly userChoice: Promise<{ outcome: 'accepted' | 'dismissed' }>;
}

const SIX_HEURES = 6 * 60 * 60 * 1000;

/**
 * Application installable (PWA) : bouton « Installer », instructions pour iPhone, détection
 * hors ligne et annonce d'une nouvelle version déployée.
 */
@Injectable({ providedIn: 'root' })
export class PwaService {
  private readonly updates = inject(SwUpdate);

  private deferredPrompt: BeforeInstallPromptEvent | null = null;
  private readonly installable = signal(false);
  private readonly installed = signal(isStandalone());
  private readonly onlineState = signal(navigator.onLine);
  private readonly updateState = signal(false);

  /** Chrome, Edge, Android : le navigateur propose l'installation. */
  readonly canInstall = this.installable.asReadonly();
  /** L'application tourne déjà en mode installé (icône d'écran d'accueil). */
  readonly isInstalled = this.installed.asReadonly();
  /** Safari sur iPhone et iPad : installation manuelle via « Partager ». */
  readonly needsIosInstructions = computed(() => isIos() && !this.installed());
  readonly online = this.onlineState.asReadonly();
  readonly updateReady = this.updateState.asReadonly();

  constructor() {
    const destroyRef = inject(DestroyRef);
    const onPrompt = (e: Event) => {
      e.preventDefault(); // on affiche notre propre bouton au lieu de la bannière du navigateur
      this.deferredPrompt = e as BeforeInstallPromptEvent;
      this.installable.set(true);
    };
    const onInstalled = () => {
      this.deferredPrompt = null;
      this.installable.set(false);
      this.installed.set(true);
    };
    const onOnline = () => this.onlineState.set(true);
    const onOffline = () => this.onlineState.set(false);
    window.addEventListener('beforeinstallprompt', onPrompt);
    window.addEventListener('appinstalled', onInstalled);
    window.addEventListener('online', onOnline);
    window.addEventListener('offline', onOffline);
    destroyRef.onDestroy(() => {
      window.removeEventListener('beforeinstallprompt', onPrompt);
      window.removeEventListener('appinstalled', onInstalled);
      window.removeEventListener('online', onOnline);
      window.removeEventListener('offline', onOffline);
    });

    if (this.updates.isEnabled) {
      this.updates.versionUpdates
        .pipe(filter((e): e is VersionReadyEvent => e.type === 'VERSION_READY'))
        .subscribe(() => this.updateState.set(true));
      // Version en cache devenue inutilisable (fichiers supprimés du serveur) : on recharge.
      this.updates.unrecoverable.subscribe(() => location.reload());
      const timer = setInterval(() => void this.updates.checkForUpdate().catch(() => false), SIX_HEURES);
      destroyRef.onDestroy(() => clearInterval(timer));
    }
  }

  /** Ouvre la fenêtre d'installation du navigateur. Renvoie true si l'utilisatrice accepte. */
  async install(): Promise<boolean> {
    const prompt = this.deferredPrompt;
    if (!prompt) {
      return false;
    }
    this.deferredPrompt = null;
    this.installable.set(false);
    await prompt.prompt();
    const { outcome } = await prompt.userChoice;
    return outcome === 'accepted';
  }

  /** Active la nouvelle version téléchargée puis recharge la page. */
  async applyUpdate(): Promise<void> {
    await this.updates.activateUpdate().catch(() => false);
    location.reload();
  }
}

function isStandalone(): boolean {
  const iosStandalone = (navigator as Navigator & { standalone?: boolean }).standalone === true;
  return iosStandalone || window.matchMedia('(display-mode: standalone)').matches;
}

function isIos(): boolean {
  // Les iPad récents se présentent comme un Mac : on les reconnaît à l'écran tactile.
  return /iPhone|iPad|iPod/.test(navigator.userAgent)
    || (navigator.platform === 'MacIntel' && navigator.maxTouchPoints > 1);
}
