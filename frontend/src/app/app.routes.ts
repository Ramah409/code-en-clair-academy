import { Routes } from '@angular/router';

import { authGuard, guestGuard } from './core/auth/auth.guards';
import { ShellComponent } from './layout/shell.component';

export const routes: Routes = [
  {
    path: 'connexion',
    title: 'Connexion · CDA Academy',
    canActivate: [guestGuard],
    loadComponent: () => import('./features/auth/login.component').then((m) => m.LoginComponent),
  },
  {
    path: 'inscription',
    title: 'Inscription · CDA Academy',
    canActivate: [guestGuard],
    loadComponent: () => import('./features/auth/register.component').then((m) => m.RegisterComponent),
  },
  {
    path: '',
    component: ShellComponent,
    canActivate: [authGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'accueil' },
      {
        path: 'accueil',
        title: 'Accueil · CDA Academy',
        loadComponent: () => import('./features/home/home.component').then((m) => m.HomeComponent),
      },
      {
        path: 'parcours',
        title: 'Parcours · CDA Academy',
        loadComponent: () => import('./features/courses/course-list.component').then((m) => m.CourseListComponent),
      },
      {
        path: 'parcours/:slug',
        title: 'Parcours · CDA Academy',
        loadComponent: () => import('./features/courses/course-detail.component').then((m) => m.CourseDetailComponent),
      },
      {
        path: 'lecon/:slug',
        title: 'Leçon · CDA Academy',
        loadComponent: () => import('./features/lesson/lesson-player.component').then((m) => m.LessonPlayerComponent),
      },
      {
        path: 'exercices',
        title: 'Exercices · CDA Academy',
        loadComponent: () => import('./features/exercises/exercise-list.component').then((m) => m.ExerciseListComponent),
      },
      {
        path: 'exercices/:slug',
        title: 'Exercice · CDA Academy',
        loadComponent: () => import('./features/exercises/exercise-page.component').then((m) => m.ExercisePageComponent),
      },
      {
        path: 'laboratoire-sql',
        title: 'Laboratoire SQL · CDA Academy',
        loadComponent: () => import('./features/lab/lab.component').then((m) => m.LabComponent),
      },
      {
        path: 'quiz',
        title: 'Quiz · CDA Academy',
        loadComponent: () => import('./features/quiz/quiz-hub.component').then((m) => m.QuizHubComponent),
      },
      {
        path: 'quiz/:id',
        title: 'QCM en cours · CDA Academy',
        loadComponent: () => import('./features/quiz/quiz-runner.component').then((m) => m.QuizRunnerComponent),
      },
      {
        path: 'quiz/:id/resultat',
        title: 'Résultat du QCM · CDA Academy',
        loadComponent: () => import('./features/quiz/quiz-result.component').then((m) => m.QuizResultComponent),
      },
      {
        path: 'progression',
        title: 'Progression · CDA Academy',
        loadComponent: () => import('./features/progress/progress.component').then((m) => m.ProgressComponent),
      },
      {
        path: 'profil',
        title: 'Profil · CDA Academy',
        loadComponent: () => import('./features/profile/profile.component').then((m) => m.ProfileComponent),
      },
    ],
  },
  { path: '**', redirectTo: 'accueil' },
];
