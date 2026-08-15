export interface QuizConfig {
  timeLimitSeconds: number;
  allowNavigation: boolean;
  showSolutions: boolean;
}

export interface QuizOption {
  id: string;
  text: string;
  isCorrect?: boolean;
}

export interface QuizQuestion {
  id: string;
  text: string;
  points: number;
  isMulti?: boolean;
  options: QuizOption[];
}

export interface QuizDefinition {
  config: QuizConfig;
  questions: QuizQuestion[];
}

export interface MissionResult {
  id: string;
  score: number;
  maxScore: number;
  percentage: number;
  detailedAnswers: string;
  isLate: boolean;
  completedAt: string;
}

export interface QuizConflictError {
  error: string;
  message: string;
  data: MissionResult;
}
