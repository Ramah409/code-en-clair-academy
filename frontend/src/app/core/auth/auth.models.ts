// Contrats de l'API d'authentification (voir backend : fr.cdaacademy.auth.dto)

export type RoleCode = 'USER' | 'ADMIN';

export interface User {
  id: number;
  email: string;
  displayName: string;
  role: RoleCode;
  xp: number;
  level: number;
  levelStartXp: number;
  nextLevelXp: number;
  currentStreak: number;
  longestStreak: number;
  dailyGoalMinutes: number;
  theme: string;
  createdAt: string;
}

export interface AuthResponse {
  accessToken: string;
  tokenType: string;
  expiresAt: string;
  user: User;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface RegisterRequest {
  email: string;
  displayName: string;
  password: string;
  confirmPassword: string;
  acceptTerms: boolean;
}

/** Format unique des erreurs renvoyées par l'API (fr.cdaacademy.common.ApiError). */
export interface ApiError {
  status: number;
  message: string;
  fieldErrors?: Record<string, string>;
}
