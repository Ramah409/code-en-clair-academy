import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

import { LEVEL_LABELS, Level } from '../../core/api/models';
import { toFormError } from '../../core/auth/api-error';
import { RewardService } from '../../core/ui/reward.service';
import { IconComponent } from '../../shared/icon.component';
import { AdminApiService, AdminChapter, AdminCourse } from './admin-api.service';

type CourseDraft = Pick<AdminCourse, 'slug' | 'title' | 'summary' | 'description' | 'category' | 'icon' | 'published' | 'mandatory' | 'examQuestionCount'> & { examTimeLimitMinutes?: number | null };

const EMPTY: CourseDraft = {
  slug: '',
  title: '',
  summary: '',
  description: '',
  category: 'PROJET',
  icon: 'book',
  published: false,
  mandatory: true,
  examQuestionCount: 40,
  examTimeLimitMinutes: null,
};

/** Parcours : liste, fiche du parcours, chapitres et leçons (création, ordre, suppression). */
@Component({
  selector: 'app-admin-courses',
  imports: [FormsModule, RouterLink, IconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './admin-courses.component.html',
})
export class AdminCoursesComponent implements OnInit {
  private readonly api = inject(AdminApiService);
  private readonly rewards = inject(RewardService);
  private readonly router = inject(Router);

  readonly courses = signal<AdminCourse[]>([]);
  readonly selectedId = signal<number | null>(null);
  readonly draft = signal<CourseDraft>({ ...EMPTY });
  readonly chapters = signal<AdminChapter[]>([]);
  readonly error = signal<string | null>(null);
  readonly busy = signal(false);
  readonly newChapter = signal({ slug: '', title: '', summary: '', level: 'DEBUTANT' as Level, quizQuestionCount: 10 });
  readonly newLesson = signal<Record<number, { slug: string; title: string; objective: string }>>({});
  readonly editChapter = signal<number | null>(null);
  readonly levels = LEVEL_LABELS;
  readonly levelKeys = Object.keys(LEVEL_LABELS) as Level[];

  readonly selected = computed(() => this.courses().find((c) => c.id === this.selectedId()) ?? null);

  ngOnInit(): void {
    this.reload();
  }

  reload(select?: number): void {
    this.api.courses().subscribe({
      next: (list) => {
        this.courses.set(list);
        const id = select ?? this.selectedId() ?? list[0]?.id ?? null;
        if (id) {
          this.select(id);
        }
      },
      error: (e: unknown) => this.error.set(toFormError(e).message),
    });
  }

  select(id: number | null): void {
    this.error.set(null);
    this.selectedId.set(id);
    const c = this.courses().find((x) => x.id === id);
    this.draft.set(c ? { ...c } : { ...EMPTY });
    this.chapters.set([]);
    if (id) {
      this.api.chapters(id).subscribe({ next: (ch) => this.chapters.set(ch), error: (e: unknown) => this.fail(e) });
    }
  }

  patch<K extends keyof CourseDraft>(key: K, value: CourseDraft[K]): void {
    this.draft.update((d) => ({ ...d, [key]: value }));
  }

  saveCourse(): void {
    this.busy.set(true);
    const id = this.selectedId();
    this.api.saveCourse(id, { ...this.draft(), description: this.draft().description || this.draft().summary } as Partial<AdminCourse>).subscribe({
      next: (c) => {
        this.busy.set(false);
        this.rewards.info(id ? 'Parcours enregistré' : 'Parcours créé', c.title);
        this.reload(c.id);
      },
      error: (e: unknown) => this.fail(e),
    });
  }

  deleteCourse(c: AdminCourse): void {
    if (!confirm(`Supprimer définitivement le parcours « ${c.title} » et tout son contenu ?`)) {
      return;
    }
    this.api.deleteCourse(c.id).subscribe({
      next: () => {
        this.selectedId.set(null);
        this.reload();
      },
      error: (e: unknown) => this.fail(e),
    });
  }

  patchNewChapter(key: 'slug' | 'title' | 'summary' | 'level', value: string): void {
    this.newChapter.update((c) => ({ ...c, [key]: value }));
  }

  addChapter(): void {
    const id = this.selectedId();
    if (!id) {
      return;
    }
    this.api.createChapter(id, this.newChapter()).subscribe({
      next: (ch) => {
        this.chapters.set(ch);
        this.newChapter.set({ slug: '', title: '', summary: '', level: 'DEBUTANT', quizQuestionCount: 10 });
      },
      error: (e: unknown) => this.fail(e),
    });
  }

  saveChapter(ch: AdminChapter): void {
    this.api.updateChapter(ch.id, ch).subscribe({
      next: (list) => {
        this.chapters.set(list);
        this.editChapter.set(null);
      },
      error: (e: unknown) => this.fail(e),
    });
  }

  patchChapter(id: number, patch: Partial<AdminChapter>): void {
    this.chapters.update((list) => list.map((c) => (c.id === id ? { ...c, ...patch } : c)));
  }

  moveChapter(ch: AdminChapter, delta: number): void {
    this.api.moveChapter(ch.id, delta).subscribe({ next: (list) => this.chapters.set(list), error: (e: unknown) => this.fail(e) });
  }

  deleteChapter(ch: AdminChapter): void {
    if (!confirm(`Supprimer le chapitre « ${ch.title} » et ses leçons ?`)) {
      return;
    }
    this.api.deleteChapter(ch.id).subscribe({ next: (list) => this.chapters.set(list), error: (e: unknown) => this.fail(e) });
  }

  lessonDraft(chapterId: number): { slug: string; title: string; objective: string } {
    return this.newLesson()[chapterId] ?? { slug: '', title: '', objective: '' };
  }

  patchLessonDraft(chapterId: number, key: 'slug' | 'title' | 'objective', value: string): void {
    this.newLesson.update((m) => ({ ...m, [chapterId]: { ...this.lessonDraft(chapterId), [key]: value } }));
  }

  addLesson(chapterId: number): void {
    this.api.createLesson(chapterId, { ...this.lessonDraft(chapterId), duration: 10 }).subscribe({
      next: (l) => void this.router.navigate(['/admin/lecons', l.id]),
      error: (e: unknown) => this.fail(e),
    });
  }

  moveLesson(id: number, delta: number): void {
    this.api.moveLesson(id, delta).subscribe({
      next: () => this.select(this.selectedId()),
      error: (e: unknown) => this.fail(e),
    });
  }

  private fail(e: unknown): void {
    this.busy.set(false);
    this.error.set(toFormError(e).message);
    window.scrollTo({ top: 0, behavior: 'smooth' });
  }
}
