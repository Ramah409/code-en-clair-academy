import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { User } from '../auth/auth.models';
import {
  Answer,
  AnswerResult,
  CatalogItem,
  Completion,
  CourseDetail,
  CourseSummary,
  Dashboard,
  ExerciseView,
  Hint,
  LabRun,
  LabTable,
  LessonView,
  ProjectListItem,
  ProjectView,
  ProgressOverview,
  QuizAttempt,
  QuizHistoryItem,
  QuizOptions,
  QuizResult,
  QuizStats,
  Reward,
  StartQuiz,
  SubmitResult,
} from './models';

/** Accès typé à l'API REST de Code en Clair Academy. */
@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);

  // Progression
  dashboard(): Observable<Dashboard> {
    return this.http.get<Dashboard>('/api/dashboard');
  }

  progress(): Observable<ProgressOverview> {
    return this.http.get<ProgressOverview>('/api/progress');
  }

  // Parcours et leçons
  courses(): Observable<CourseSummary[]> {
    return this.http.get<CourseSummary[]>('/api/courses');
  }

  course(slug: string): Observable<CourseDetail> {
    return this.http.get<CourseDetail>(`/api/courses/${slug}`);
  }

  lesson(slug: string): Observable<LessonView> {
    return this.http.get<LessonView>(`/api/lessons/${slug}`);
  }

  answerLessonQuestion(lesson: string, code: string, answer: Answer): Observable<AnswerResult> {
    return this.http.post<AnswerResult>(`/api/lessons/${lesson}/questions/${code}/answer`, answer);
  }

  saveLessonStep(lesson: string, step: number): Observable<void> {
    return this.http.post<void>(`/api/lessons/${lesson}/step`, { step });
  }

  completeLesson(lesson: string, secondsSpent: number): Observable<Completion> {
    return this.http.post<Completion>(`/api/lessons/${lesson}/complete`, { secondsSpent });
  }

  // Exercices
  exercises(filters: { course?: string; kind?: string; difficulty?: string } = {}): Observable<CatalogItem[]> {
    let params = new HttpParams();
    for (const [k, v] of Object.entries(filters)) {
      if (v) {
        params = params.set(k, v);
      }
    }
    return this.http.get<CatalogItem[]>('/api/exercises', { params });
  }

  exercise(slug: string): Observable<ExerciseView> {
    return this.http.get<ExerciseView>(`/api/exercises/${slug}`);
  }

  hint(slug: string): Observable<Hint> {
    return this.http.post<Hint>(`/api/exercises/${slug}/hints`, null);
  }

  solution(slug: string): Observable<ExerciseView> {
    return this.http.post<ExerciseView>(`/api/exercises/${slug}/solution`, null);
  }

  submitExercise(slug: string, answer: unknown, secondsSpent: number): Observable<SubmitResult> {
    return this.http.post<SubmitResult>(`/api/exercises/${slug}/submit`, { answer, secondsSpent });
  }

  // Laboratoire SQL
  labSchema(): Observable<LabTable[]> {
    return this.http.get<LabTable[]>('/api/lab/schema');
  }

  runSql(sql: string): Observable<LabRun> {
    return this.http.post<LabRun>('/api/lab/run', { sql });
  }

  // QCM
  quizOptions(): Observable<QuizOptions> {
    return this.http.get<QuizOptions>('/api/quiz/options');
  }

  quizStats(): Observable<QuizStats> {
    return this.http.get<QuizStats>('/api/quiz/stats');
  }

  quizHistory(scope?: string, course?: string): Observable<QuizHistoryItem[]> {
    let params = new HttpParams();
    if (scope) {
      params = params.set('scope', scope);
    }
    if (course) {
      params = params.set('course', course);
    }
    return this.http.get<QuizHistoryItem[]>('/api/quiz/history', { params });
  }

  startQuiz(req: StartQuiz): Observable<QuizAttempt> {
    return this.http.post<QuizAttempt>('/api/quiz/start', req);
  }

  quizAttempt(id: number): Observable<QuizAttempt> {
    return this.http.get<QuizAttempt>(`/api/quiz/attempts/${id}`);
  }

  answerQuiz(id: number, position: number, answer: Answer): Observable<{ saved: boolean; feedback?: AnswerResult['feedback'] }> {
    return this.http.post<{ saved: boolean; feedback?: AnswerResult['feedback'] }>(
      `/api/quiz/attempts/${id}/answer`,
      { position, answer },
    );
  }

  submitQuiz(id: number): Observable<QuizResult> {
    return this.http.post<QuizResult>(`/api/quiz/attempts/${id}/submit`, null);
  }

  quizResult(id: number): Observable<QuizResult> {
    return this.http.get<QuizResult>(`/api/quiz/attempts/${id}/result`);
  }

  // Projets
  projects(): Observable<ProjectListItem[]> {
    return this.http.get<ProjectListItem[]>('/api/projects');
  }

  project(slug: string): Observable<ProjectView> {
    return this.http.get<ProjectView>(`/api/projects/${slug}`);
  }

  completeProjectStep(slug: string, position: number, checked: number[]): Observable<{ reward: Reward; projectCompleted: boolean }> {
    return this.http.post<{ reward: Reward; projectCompleted: boolean }>(`/api/projects/${slug}/steps/${position}/complete`, { checked });
  }

  // Modélisation : modèles personnels de l'atelier
  diagrams(): Observable<{ id: number; title: string; updatedAt: string }[]> {
    return this.http.get<{ id: number; title: string; updatedAt: string }[]>('/api/diagrams');
  }

  diagram(id: number): Observable<{ id: number; title: string; content: any; updatedAt: string }> {
    return this.http.get<{ id: number; title: string; content: any; updatedAt: string }>(`/api/diagrams/${id}`);
  }

  saveDiagram(id: number | null, title: string, content: unknown): Observable<{ id: number; title: string; content: any; updatedAt: string }> {
    return id
      ? this.http.put<{ id: number; title: string; content: any; updatedAt: string }>(`/api/diagrams/${id}`, { title, content })
      : this.http.post<{ id: number; title: string; content: any; updatedAt: string }>('/api/diagrams', { title, content });
  }

  deleteDiagram(id: number): Observable<void> {
    return this.http.delete<void>(`/api/diagrams/${id}`);
  }

  // Profil
  updateProfile(body: { displayName: string; dailyGoalMinutes: number; theme: string }): Observable<User> {
    return this.http.patch<User>('/api/me', body);
  }

  changePassword(body: { currentPassword: string; newPassword: string }): Observable<void> {
    return this.http.put<void>('/api/me/password', body);
  }

  deleteAccount(password: string): Observable<void> {
    return this.http.delete<void>('/api/me', { body: { password } });
  }
}
