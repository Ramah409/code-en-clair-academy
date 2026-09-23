import { Routes } from '@angular/router';

import { authGuard, guestGuard } from './core/auth/auth.guards';
import { ShellComponent } from './layout/shell.component';

export const routes: Routes = [
  {
    path: 'connexion',
    title: 'Connexion · Code en Clair Academy',
    canActivate: [guestGuard],
    loadComponent: () => import('./features/auth/login.component').then((m) => m.LoginComponent),
  },
  {
    path: 'inscription',
    title: 'Inscription · Code en Clair Academy',
    canActivate: [guestGuard],
    loadComponent: () => import('./features/auth/register.component').then((m) => m.RegisterComponent),
  },
  {
    path: 'verification',
    title: 'Vérifier une attestation · Code en Clair Academy',
    loadComponent: () =>
      import('./features/certificates/certificate-verify.component').then((m) => m.CertificateVerifyComponent),
  },
  {
    path: 'verification/:code',
    title: 'Vérifier une attestation · Code en Clair Academy',
    loadComponent: () =>
      import('./features/certificates/certificate-verify.component').then((m) => m.CertificateVerifyComponent),
  },
  {
    path: '',
    component: ShellComponent,
    canActivate: [authGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'accueil' },
      {
        path: 'accueil',
        title: 'Accueil · Code en Clair Academy',
        loadComponent: () => import('./features/home/home.component').then((m) => m.HomeComponent),
      },
      {
        path: 'parcours',
        title: 'Parcours · Code en Clair Academy',
        loadComponent: () => import('./features/courses/course-list.component').then((m) => m.CourseListComponent),
      },
      {
        path: 'parcours/:slug',
        title: 'Parcours · Code en Clair Academy',
        loadComponent: () => import('./features/courses/course-detail.component').then((m) => m.CourseDetailComponent),
      },
      {
        path: 'lecon/:slug',
        title: 'Leçon · Code en Clair Academy',
        loadComponent: () => import('./features/lesson/lesson-player.component').then((m) => m.LessonPlayerComponent),
      },
      {
        path: 'exercices',
        title: 'Exercices · Code en Clair Academy',
        loadComponent: () => import('./features/exercises/exercise-list.component').then((m) => m.ExerciseListComponent),
      },
      {
        path: 'exercices/:slug',
        title: 'Exercice · Code en Clair Academy',
        loadComponent: () => import('./features/exercises/exercise-page.component').then((m) => m.ExercisePageComponent),
      },
      {
        path: 'laboratoire-sql',
        title: 'Laboratoire SQL · Code en Clair Academy',
        loadComponent: () => import('./features/lab/lab.component').then((m) => m.LabComponent),
      },
      {
        path: 'modelisation',
        title: 'Modélisation · Code en Clair Academy',
        loadComponent: () => import('./features/modeling/modeling.component').then((m) => m.ModelingComponent),
      },
      {
        path: 'quiz',
        title: 'Quiz · Code en Clair Academy',
        loadComponent: () => import('./features/quiz/quiz-hub.component').then((m) => m.QuizHubComponent),
      },
      {
        path: 'quiz/:id',
        title: 'QCM en cours · Code en Clair Academy',
        loadComponent: () => import('./features/quiz/quiz-runner.component').then((m) => m.QuizRunnerComponent),
      },
      {
        path: 'quiz/:id/resultat',
        title: 'Résultat du QCM · Code en Clair Academy',
        loadComponent: () => import('./features/quiz/quiz-result.component').then((m) => m.QuizResultComponent),
      },
      {
        path: 'projets/:slug',
        title: 'Projet · Code en Clair Academy',
        loadComponent: () => import('./features/project/project-page.component').then((m) => m.ProjectPageComponent),
      },
      {
        path: 'examen-cda',
        title: 'Examen CDA · Code en Clair Academy',
        loadComponent: () => import('./features/exam/exam-hub.component').then((m) => m.ExamHubComponent),
      },
      {
        path: 'examen-cda/cas/:slug',
        title: 'Étude de cas · Code en Clair Academy',
        loadComponent: () => import('./features/exam/case-study.component').then((m) => m.CaseStudyComponent),
      },
      {
        path: 'examen-cda/jury',
        title: 'Questions du jury · Code en Clair Academy',
        loadComponent: () => import('./features/exam/jury-trainer.component').then((m) => m.JuryTrainerComponent),
      },
      {
        path: 'attestations',
        title: 'Mes attestations · Code en Clair Academy',
        loadComponent: () =>
          import('./features/certificates/certificates.component').then((m) => m.CertificatesComponent),
      },
      {
        path: 'attestations/:code',
        title: 'Attestation · Code en Clair Academy',
        loadComponent: () =>
          import('./features/certificates/certificate-print.component').then((m) => m.CertificatePrintComponent),
      },
      {
        path: 'progression',
        title: 'Progression · Code en Clair Academy',
        loadComponent: () => import('./features/progress/progress.component').then((m) => m.ProgressComponent),
      },
      {
        path: 'profil',
        title: 'Profil · Code en Clair Academy',
        loadComponent: () => import('./features/profile/profile.component').then((m) => m.ProfileComponent),
      },
    ],
  },
  { path: '**', redirectTo: 'accueil' },
];
