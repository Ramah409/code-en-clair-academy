import { ChangeDetectionStrategy, Component, OnInit, inject, input, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';

import { ApiService } from '../../core/api/api.service';
import { ChapterState, CourseDetail, LEVEL_LABELS, LessonState } from '../../core/api/models';
import { toFormError } from '../../core/auth/api-error';
import { IconComponent } from '../../shared/icon.component';
import { MarkdownComponent } from '../../shared/markdown.component';

@Component({
  selector: 'app-course-detail',
  imports: [RouterLink, IconComponent, MarkdownComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './course-detail.component.html',
  styleUrl: './course-detail.component.scss',
})
export class CourseDetailComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);

  readonly slug = input.required<string>();
  readonly course = signal<CourseDetail | null>(null);
  readonly error = signal<string | null>(null);
  readonly starting = signal<string | null>(null);
  readonly showAbout = signal(false);
  readonly levels = LEVEL_LABELS;

  ngOnInit(): void {
    this.api.course(this.slug()).subscribe({
      next: (c) => this.course.set(c),
      error: (e: unknown) => this.error.set(toFormError(e).message),
    });
  }

  quizAvailable(ch: ChapterState): boolean {
    return ch.unlocked && ch.lessonsDone;
  }

  lessonLabel(l: LessonState): string {
    return { LOCKED: 'verrouillée', AVAILABLE: 'à commencer', IN_PROGRESS: 'en cours', DONE: 'terminée' }[l.status];
  }

  startChapterQuiz(ch: ChapterState): void {
    this.starting.set(ch.slug);
    this.api.startQuiz({ scope: 'CHAPITRE', course: this.slug(), chapter: ch.slug }).subscribe({
      next: (a) => void this.router.navigate(['/quiz', a.id]),
      error: (e: unknown) => {
        this.starting.set(null);
        this.error.set(toFormError(e).message);
      },
    });
  }
}
