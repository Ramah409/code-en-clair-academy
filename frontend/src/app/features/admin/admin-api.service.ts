import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { Level } from '../../core/api/models';

export interface AdminCourse {
  id: number;
  slug: string;
  title: string;
  summary: string;
  description: string;
  category: string;
  icon: string;
  published: boolean;
  mandatory: boolean;
  position: number;
  contentVersion: number;
  examQuestionCount: number;
  examTimeLimitMinutes?: number;
  chapters: number;
  lessons: number;
  questions: number;
  exercises: number;
  learners: number;
}

export interface AdminLessonItem {
  id: number;
  slug: string;
  title: string;
  position: number;
  duration: number;
  published: boolean;
}

export interface AdminChapter {
  id: number;
  slug: string;
  title: string;
  summary: string;
  level: Level;
  position: number;
  quizQuestionCount: number;
  bankSize: number;
  lessons: AdminLessonItem[];
}

export interface AdminLesson {
  id: number;
  chapterId: number;
  courseId: number;
  courseSlug: string;
  slug: string;
  title: string;
  objective: string;
  prerequisites?: string;
  duration: number;
  xp: number;
  memo?: string;
  commonMistakes?: string;
  published: boolean;
  blocks: unknown[];
  questions: { code: string; kind: string; prompt: string }[];
  exercises: { slug: string; kind: string; title: string }[];
}

export interface AdminChoice {
  label: string;
  correct: boolean;
  why: string;
}

export interface AdminQuestionRow {
  id: number;
  code: string;
  kind: string;
  prompt: string;
  difficulty: number;
  courseTitle?: string;
  chapterTitle?: string;
  lessonTitle?: string;
  published: boolean;
}

export interface AdminQuestion {
  id?: number;
  code: string;
  kind: string;
  difficulty: number;
  prompt: string;
  snippet?: string;
  language?: string;
  explanation: string;
  answers: string[];
  choices: AdminChoice[];
  courseId?: number | null;
  chapterId?: number | null;
  lessonId?: number | null;
  published: boolean;
  usage?: number;
}

export interface AdminExerciseRow {
  id: number;
  slug: string;
  kind: string;
  title: string;
  difficulty: string;
  courseTitle?: string;
  lessonTitle?: string;
  attempts: number;
}

export interface AdminExercise {
  id?: number;
  slug: string;
  kind: string;
  title: string;
  difficulty: string;
  statement: string;
  criteria: string;
  hints: string[];
  solution: string;
  explanation: string;
  xp: number;
  payload: unknown;
  courseId?: number | null;
  lessonId?: number | null;
  attempts?: number;
}

export interface CheckResult {
  success: boolean;
  message: string;
  problems: string[];
}

export interface AdminUser {
  id: number;
  email: string;
  displayName: string;
  role: 'USER' | 'ADMIN';
  enabled: boolean;
  xp: number;
  level: number;
  createdAt: string;
}

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

const BASE = '/api/admin/content';

/** Appels de l'espace d'administration (routes /api/admin/**, réservées au rôle ADMIN). */
@Injectable({ providedIn: 'root' })
export class AdminApiService {
  private readonly http = inject(HttpClient);

  courses(): Observable<AdminCourse[]> {
    return this.http.get<AdminCourse[]>(`${BASE}/courses`);
  }

  saveCourse(id: number | null, body: Partial<AdminCourse>): Observable<AdminCourse> {
    return id ? this.http.put<AdminCourse>(`${BASE}/courses/${id}`, body) : this.http.post<AdminCourse>(`${BASE}/courses`, body);
  }

  deleteCourse(id: number): Observable<void> {
    return this.http.delete<void>(`${BASE}/courses/${id}`);
  }

  chapters(courseId: number): Observable<AdminChapter[]> {
    return this.http.get<AdminChapter[]>(`${BASE}/courses/${courseId}/chapters`);
  }

  createChapter(courseId: number, body: Partial<AdminChapter>): Observable<AdminChapter[]> {
    return this.http.post<AdminChapter[]>(`${BASE}/courses/${courseId}/chapters`, body);
  }

  updateChapter(id: number, body: Partial<AdminChapter>): Observable<AdminChapter[]> {
    return this.http.put<AdminChapter[]>(`${BASE}/chapters/${id}`, body);
  }

  moveChapter(id: number, delta: number): Observable<AdminChapter[]> {
    return this.http.post<AdminChapter[]>(`${BASE}/chapters/${id}/move`, {}, { params: { delta } });
  }

  deleteChapter(id: number): Observable<AdminChapter[]> {
    return this.http.delete<AdminChapter[]>(`${BASE}/chapters/${id}`);
  }

  lesson(id: number): Observable<AdminLesson> {
    return this.http.get<AdminLesson>(`${BASE}/lessons/${id}`);
  }

  createLesson(chapterId: number, body: Partial<AdminLesson>): Observable<AdminLesson> {
    return this.http.post<AdminLesson>(`${BASE}/chapters/${chapterId}/lessons`, body);
  }

  updateLesson(id: number, body: Partial<AdminLesson>): Observable<AdminLesson> {
    return this.http.put<AdminLesson>(`${BASE}/lessons/${id}`, body);
  }

  moveLesson(id: number, delta: number): Observable<AdminLesson> {
    return this.http.post<AdminLesson>(`${BASE}/lessons/${id}/move`, {}, { params: { delta } });
  }

  deleteLesson(id: number): Observable<void> {
    return this.http.delete<void>(`${BASE}/lessons/${id}`);
  }

  questions(filters: { course?: number; chapter?: number; lesson?: number; search?: string; page?: number }): Observable<Page<AdminQuestionRow>> {
    let params = new HttpParams();
    Object.entries(filters).forEach(([k, v]) => {
      if (v !== undefined && v !== null && v !== '') {
        params = params.set(k, String(v));
      }
    });
    return this.http.get<Page<AdminQuestionRow>>(`${BASE}/questions`, { params });
  }

  question(id: number): Observable<AdminQuestion> {
    return this.http.get<AdminQuestion>(`${BASE}/questions/${id}`);
  }

  saveQuestion(id: number | null | undefined, body: AdminQuestion): Observable<AdminQuestion> {
    return id ? this.http.put<AdminQuestion>(`${BASE}/questions/${id}`, body) : this.http.post<AdminQuestion>(`${BASE}/questions`, body);
  }

  deleteQuestion(id: number): Observable<void> {
    return this.http.delete<void>(`${BASE}/questions/${id}`);
  }

  exercises(course?: number, search?: string): Observable<AdminExerciseRow[]> {
    let params = new HttpParams();
    if (course) {
      params = params.set('course', course);
    }
    if (search) {
      params = params.set('search', search);
    }
    return this.http.get<AdminExerciseRow[]>(`${BASE}/exercises`, { params });
  }

  exercise(id: number): Observable<AdminExercise> {
    return this.http.get<AdminExercise>(`${BASE}/exercises/${id}`);
  }

  saveExercise(id: number | null | undefined, body: AdminExercise): Observable<{ exercise: AdminExercise; check: CheckResult }> {
    return id
      ? this.http.put<{ exercise: AdminExercise; check: CheckResult }>(`${BASE}/exercises/${id}`, body)
      : this.http.post<{ exercise: AdminExercise; check: CheckResult }>(`${BASE}/exercises`, body);
  }

  deleteExercise(id: number): Observable<void> {
    return this.http.delete<void>(`${BASE}/exercises/${id}`);
  }

  users(search: string, page: number): Observable<Page<AdminUser>> {
    let params = new HttpParams().set('page', page).set('size', 20);
    if (search) {
      params = params.set('search', search);
    }
    return this.http.get<Page<AdminUser>>('/api/admin/users', { params });
  }

  changeRole(id: number, role: 'USER' | 'ADMIN'): Observable<AdminUser> {
    return this.http.patch<AdminUser>(`/api/admin/users/${id}/role`, { role });
  }

  changeEnabled(id: number, enabled: boolean): Observable<AdminUser> {
    return this.http.patch<AdminUser>(`/api/admin/users/${id}/enabled`, { enabled });
  }

  deleteUser(id: number): Observable<void> {
    return this.http.delete<void>(`/api/admin/users/${id}`);
  }
}
