import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { Router, RouterLink } from '@angular/router';

import { ApiService } from '../../core/api/api.service';
import { ExamOverview, LEVEL_LABELS, MockExam } from '../../core/api/models';
import { toFormError } from '../../core/auth/api-error';
import { IconComponent } from '../../shared/icon.component';
import { MarkdownComponent } from '../../shared/markdown.component';

/** Espace « Examen CDA » : examens blancs chronométrés, études de cas et entraînement aux questions du jury. */
@Component({
  selector: 'app-exam-hub',
  imports: [DatePipe, RouterLink, IconComponent, MarkdownComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './exam-hub.component.html',
  styleUrl: './exam-hub.component.scss',
})
export class ExamHubComponent implements OnInit {
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);

  readonly overview = signal<ExamOverview | null>(null);
  readonly error = signal<string | null>(null);
  readonly starting = signal<string | null>(null);
  readonly confirm = signal<string | null>(null);
  readonly levels = LEVEL_LABELS;

  ngOnInit(): void {
    this.api.examOverview().subscribe({
      next: (o) => this.overview.set(o),
      error: (e: unknown) => this.error.set(toFormError(e).message),
    });
  }

  start(exam: MockExam): void {
    this.starting.set(exam.slug);
    this.api.startQuiz({ scope: 'EXAMEN_BLANC', exam: exam.slug }).subscribe({
      next: (a) => void this.router.navigate(['/quiz', a.id]),
      error: (e: unknown) => {
        this.starting.set(null);
        this.error.set(toFormError(e).message);
      },
    });
  }

  attemptLink(id: number, submitted?: string): unknown[] {
    return submitted ? ['/quiz', id, 'resultat'] : ['/quiz', id];
  }
}
