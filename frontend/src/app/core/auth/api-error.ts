import { HttpErrorResponse } from '@angular/common/http';

import { ApiError } from './auth.models';

/** Erreur affichable : message général + messages par champ. */
export interface FormError {
  message: string;
  fields: Record<string, string>;
}

/**
 * Traduit une erreur HTTP en messages pour le formulaire.
 * `aliases` rattache les erreurs globales du backend (ex. « passwordConfirmed ») au bon champ.
 */
export function toFormError(err: unknown, aliases: Record<string, string> = {}): FormError {
  if (!(err instanceof HttpErrorResponse)) {
    return { message: 'Une erreur inattendue est survenue.', fields: {} };
  }
  if (err.status === 0) {
    return { message: "Impossible de joindre le serveur. Vérifie que l'API est démarrée.", fields: {} };
  }

  const body = err.error as Partial<ApiError> | null;
  const fields: Record<string, string> = {};
  for (const [key, msg] of Object.entries(body?.fieldErrors ?? {})) {
    fields[aliases[key] ?? key] = msg;
  }
  return {
    message: body?.message ?? 'Une erreur inattendue est survenue. Réessaie dans quelques instants.',
    fields,
  };
}
