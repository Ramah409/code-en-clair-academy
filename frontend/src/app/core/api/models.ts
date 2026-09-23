// Contrats de l'API (voir les records Java des packages course, exercise, quiz, lab, progress)

export type Level = 'DEBUTANT' | 'INTERMEDIAIRE' | 'AVANCE' | 'EXAMEN';
export type LessonStatus = 'LOCKED' | 'AVAILABLE' | 'IN_PROGRESS' | 'DONE';

export const LEVEL_LABELS: Record<Level, string> = {
  DEBUTANT: 'Débutant',
  INTERMEDIAIRE: 'Intermédiaire',
  AVANCE: 'Avancé',
  EXAMEN: 'Préparation examen',
};

export const DIFFICULTY_LABELS: Record<number, string> = {
  1: 'Débutant',
  2: 'Intermédiaire',
  3: 'Avancé',
  4: 'Examen CDA',
};

// ------------------------------------------------------------------ progression

export interface Badge {
  code: string;
  name: string;
  description: string;
  icon: string;
}

export interface Reward {
  xpEarned: number;
  totalXp: number;
  level: number;
  levelUp: boolean;
  streak: number;
  newBadges: Badge[];
}

export interface LevelInfo {
  xp: number;
  level: number;
  levelStartXp: number;
  nextLevelXp: number;
  percent: number;
}

export interface DailyGoal {
  goalMinutes: number;
  minutesToday: number;
  xpToday: number;
  percent: number;
  reached: boolean;
}

export interface Streak {
  current: number;
  longest: number;
  activeToday: boolean;
}

export interface Resume {
  lessonSlug: string;
  lessonTitle: string;
  chapterTitle: string;
  courseSlug: string;
  courseTitle: string;
  currentStep: number;
  started: boolean;
}

export interface DayActivity {
  date: string;
  minutes: number;
  xp: number;
  exercises: number;
}

export interface BadgeView extends Badge {
  criteria: string;
  threshold: number;
  value: number;
  earnedAt?: string;
}

export interface Totals {
  lessonsDone: number;
  exercisesSolved: number;
  quizzesPassed: number;
  chaptersPassed: number;
  questionsAnswered: number;
  minutesStudied: number;
}

export interface Dashboard {
  displayName: string;
  level: LevelInfo;
  dailyGoal: DailyGoal;
  streak: Streak;
  resume?: Resume;
  courses: CourseSummary[];
  mistakesToReview: number;
  recentBadges: BadgeView[];
  lastDays: DayActivity[];
  totals: Totals;
}

export interface ProgressOverview {
  level: LevelInfo;
  dailyGoal: DailyGoal;
  streak: Streak;
  totals: Totals;
  activity: DayActivity[];
  badges: BadgeView[];
  courses: CourseSummary[];
}

// ------------------------------------------------------------------ parcours

export interface NextLesson {
  slug: string;
  title: string;
  chapterTitle: string;
}

export interface CourseSummary {
  slug: string;
  title: string;
  summary: string;
  category: string;
  icon: string;
  chapterCount: number;
  lessonCount: number;
  lessonsDone: number;
  percent: number;
  levels: Level[];
  questionCount: number;
  exerciseCount: number;
  examPassed: boolean;
  nextLesson?: NextLesson;
}

export interface LessonState {
  id: number;
  slug: string;
  title: string;
  position: number;
  duration: number;
  xp: number;
  status: LessonStatus;
}

export interface ChapterState {
  id: number;
  slug: string;
  title: string;
  summary: string;
  level: Level;
  position: number;
  quizQuestionCount: number;
  bankSize: number;
  unlocked: boolean;
  lessonsDone: boolean;
  quizPassed: boolean;
  bestQuizPercent?: number;
  quizAttempts: number;
  lessons: LessonState[];
}

export interface ProjectSummary {
  slug: string;
  title: string;
  difficulty: string;
  stepCount: number;
  stepsDone: number;
  available: boolean;
}

export interface CourseDetail {
  slug: string;
  title: string;
  summary: string;
  description: string;
  category: string;
  icon: string;
  lessonCount: number;
  lessonsDone: number;
  percent: number;
  chapters: ChapterState[];
  passingPercent: number;
  examQuestionCount: number;
  examTimeLimitMinutes?: number;
  examAvailable: boolean;
  examPassed: boolean;
  bestExamPercent?: number;
  examAttempts: number;
  questionCount: number;
  project?: ProjectSummary;
  nextLesson?: NextLesson;
}

// ------------------------------------------------------------------ questions

export type QuestionKind =
  | 'CHOIX_UNIQUE'
  | 'CHOIX_MULTIPLE'
  | 'VRAI_FAUX'
  | 'TEXTE'
  | 'COMPLETER_CODE'
  | 'RESULTAT_CODE';

export interface PublicChoice {
  position: number;
  label: string;
}

export interface PublicQuestion {
  id: number;
  code?: string;
  kind: QuestionKind;
  prompt: string;
  snippet?: string;
  language?: string;
  difficulty: number;
  theme?: string;
  choices: PublicChoice[];
}

export interface Answer {
  choices?: number[];
  text?: string;
}

export interface ChoiceFeedback {
  position: number;
  label: string;
  correct: boolean;
  selected: boolean;
  why: string;
}

export interface Feedback {
  correct: boolean;
  explanation: string;
  choices: ChoiceFeedback[];
  acceptedAnswers: string[];
  givenText?: string;
}

// ------------------------------------------------------------------ leçons

export interface Ref {
  slug: string;
  title: string;
}

export interface CodeLine {
  n: number;
  note: string;
}

export type LessonBlock =
  | { type: 'text'; md: string }
  | { type: 'definition'; term: string; md: string }
  | { type: 'code'; language: string; title?: string; code: string; lines?: CodeLine[]; output?: string }
  | { type: 'demo'; title?: string; md?: string; language: string; code: string; output?: string }
  | { type: 'callout'; variant: 'info' | 'tip' | 'warning' | 'exam'; title?: string; md: string }
  | { type: 'steps'; title?: string; items: string[] }
  | { type: 'compare'; left: CompareSide; right: CompareSide }
  | { type: 'question'; ref: string; question: PublicQuestion; answered: boolean }
  | { type: 'exercise'; ref: string; exercise: ExerciseView };

export interface CompareSide {
  title: string;
  md?: string;
  code?: string;
  language?: string;
  good?: boolean;
}

export interface LessonView {
  slug: string;
  title: string;
  objective: string;
  prerequisites?: string;
  duration: number;
  xp: number;
  memo?: string;
  commonMistakes?: string;
  course: Ref;
  chapter: { slug: string; title: string; level: Level; position: number };
  position: number;
  lessonCount: number;
  blocks: LessonBlock[];
  status: 'EN_COURS' | 'TERMINEE';
  currentStep: number;
  previous?: Ref;
  next?: Ref;
  chapterQuizNext: boolean;
}

export interface AnswerResult {
  feedback: Feedback;
  xpEarned: number;
  reward?: Reward;
}

export interface Completion {
  reward: Reward;
  alreadyDone: boolean;
  next?: Ref;
  chapterQuizAvailable: boolean;
  chapterSlug: string;
  courseSlug: string;
}

// ------------------------------------------------------------------ exercices

export type ExerciseKind =
  | 'SQL'
  | 'CODE_LIBRE'
  | 'CORRIGER_ERREUR'
  | 'COMPLETER'
  | 'REMETTRE_ORDRE'
  | 'MCD'
  | 'MCD_VERS_MLD'
  | 'MLD_VERS_SQL';

export const EXERCISE_KIND_LABELS: Record<string, string> = {
  SQL: 'Requête SQL',
  CODE_LIBRE: 'Code',
  CORRIGER_ERREUR: 'Corriger un bug',
  COMPLETER: 'Code à trous',
  REMETTRE_ORDRE: 'Remettre dans l’ordre',
  MCD: 'MCD',
  MCD_VERS_MLD: 'MCD → MLD',
  MLD_VERS_SQL: 'MLD → SQL',
};

export const DIFFICULTY_NAMES: Record<string, string> = {
  FACILE: 'Facile',
  INTERMEDIAIRE: 'Intermédiaire',
  DIFFICILE: 'Difficile',
};

export interface ExerciseView {
  slug: string;
  title: string;
  kind: ExerciseKind;
  difficulty: string;
  statement: string;
  criteria: string;
  xp: number;
  payload: any;
  context?: { courseSlug?: string; courseTitle?: string; lessonSlug?: string; lessonTitle?: string };
  hintsUsed: number;
  hints: string[];
  solutionViewed: boolean;
  solutionAvailable: boolean;
  solution?: string;
  explanation?: string;
  lastAnswer?: string;
  solved: boolean;
  submissions: number;
}

export interface Hint {
  index: number;
  text: string;
  remaining: number;
}

export interface ResultData {
  columns: string[];
  rows: (string | null)[][];
  truncated: boolean;
}

export interface SubmitResult {
  success: boolean;
  message: string;
  feedback: string[];
  details?: any;
  firstSuccess: boolean;
  reward?: Reward;
  solution?: string;
  explanation?: string;
}

export interface CatalogItem {
  slug: string;
  title: string;
  kind: ExerciseKind;
  difficulty: string;
  xp: number;
  courseSlug?: string;
  courseTitle?: string;
  lessonSlug?: string;
  lessonTitle?: string;
  solved: boolean;
  attempted: boolean;
}

// ------------------------------------------------------------------ laboratoire

export interface StatementResult {
  statement: string;
  command: string;
  data?: ResultData;
  updateCount?: number;
  durationMs: number;
}

export interface LabError {
  statementIndex: number;
  statement: string;
  message: string;
  hint?: string;
  sqlState?: string;
}

export interface LabRun {
  results: StatementResult[];
  error?: LabError;
}

export interface LabColumn {
  name: string;
  type: string;
  nullable: boolean;
  primaryKey: boolean;
  references?: string;
}

export interface LabTable {
  name: string;
  rowCount: number;
  columns: LabColumn[];
}

// ------------------------------------------------------------------ QCM

export type QuizScope = 'CHAPITRE' | 'PARCOURS' | 'ENTRAINEMENT' | 'ERREURS' | 'ALEATOIRE' | 'EXAMEN_BLANC';
export type QuizMode = 'ENTRAINEMENT' | 'EXAMEN';

export interface StartQuiz {
  scope: QuizScope;
  mode?: QuizMode;
  course?: string;
  chapter?: string;
  courses?: string[];
  difficulty?: number;
  count?: number;
  timeLimitMinutes?: number;
  exam?: string;
}

export interface QuizItem {
  position: number;
  question: PublicQuestion;
  answered: boolean;
  given?: Answer;
  feedback?: Feedback;
}

export interface QuizAttempt {
  id: number;
  scope: QuizScope;
  mode: QuizMode;
  title: string;
  questionCount: number;
  startedAt: string;
  deadlineAt?: string;
  remainingSeconds?: number;
  submitted: boolean;
  passingPercent: number;
  courseSlug?: string;
  chapterSlug?: string;
  items: QuizItem[];
}

export interface ReviewTopic {
  theme: string;
  lessonSlug?: string;
  lessonTitle?: string;
  chapterTitle?: string;
  wrong: number;
  total: number;
}

export interface QuizResultItem {
  position: number;
  question: PublicQuestion;
  given?: Answer;
  correct: boolean;
  feedback: Feedback;
}

export interface QuizResult {
  id: number;
  scope: QuizScope;
  mode: QuizMode;
  title: string;
  correct: number;
  total: number;
  percent: number;
  passed: boolean;
  passingPercent: number;
  xpEarned: number;
  reward?: Reward;
  durationSeconds: number;
  submittedAt: string;
  courseSlug?: string;
  chapterSlug?: string;
  nextChapterUnlocked: boolean;
  review: ReviewTopic[];
  items: QuizResultItem[];
}

export interface QuizHistoryItem {
  id: number;
  scope: QuizScope;
  mode: QuizMode;
  title: string;
  total: number;
  percent?: number;
  passed?: boolean;
  startedAt: string;
  submittedAt?: string;
}

export interface QuizOptions {
  courses: {
    slug: string;
    title: string;
    icon: string;
    questionCount: number;
    difficulties: number[];
    chapters: { slug: string; title: string; level: Level; questionCount: number }[];
  }[];
  exams: {
    slug: string;
    title: string;
    description: string;
    durationMinutes: number;
    questionCount: number;
    passingScore: number;
    bestPercent?: number;
    attempts: number;
  }[];
  mistakesToReview: number;
}

export interface QuizStats {
  answered: number;
  correct: number;
  rate: number;
  quizzesDone: number;
  quizzesPassed: number;
  mistakesToReview: number;
  themes: { theme: string; course: string; answered: number; correct: number; rate: number }[];
  recurringErrors: {
    code: string;
    prompt: string;
    course?: string;
    theme: string;
    timesWrong: number;
    timesCorrect: number;
    lastWrongAt?: string;
    fixed: boolean;
  }[];
}
