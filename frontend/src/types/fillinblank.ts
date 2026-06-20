// User-facing types (correct field NOT exposed)
export interface FillInBlankOptionResponse {
  id: string;
  optionText: string;
  orderIndex: number;
}

export interface FillInBlankBlankResponse {
  id: string;
  key: string;
  orderIndex: number;
  options: FillInBlankOptionResponse[];
}

export interface FillInBlankUserResponse {
  missionId: string;
  templateText: string;
  passThreshold: number | null;
  blanks: FillInBlankBlankResponse[];
}

// Admin-facing types (correct field visible)
export interface FillInBlankOptionAdmin {
  optionText: string;
  correct: boolean;
  orderIndex: number;
}

export interface FillInBlankBlankAdmin {
  key: string;
  orderIndex: number;
  options: FillInBlankOptionAdmin[];
}

export interface FillInBlankBlankAdminResponse {
  id: string;
  key: string;
  orderIndex: number;
  options: Array<FillInBlankOptionAdmin & { id: string }>;
}

export interface FillInBlankAdminResponse {
  missionId: string;
  templateText: string;
  passThreshold: number | null;
  blanks: FillInBlankBlankAdminResponse[];
}

export interface SaveFillInBlankRequest {
  templateText: string;
  passThreshold: number | null;
  blanks: FillInBlankBlankAdmin[];
}

// Submit and results
export interface SubmitFillInBlankRequest {
  answers: Record<string, string>; // blankKey → optionId
}

export interface BlankResultDetail {
  blankKey: string;
  selectedOptionId: string | null;
  correct: boolean;
  correctOptionTexts: string[];
}

export interface FillInBlankResultResponse {
  score: number;
  maxScore: number;
  percentage: number;
  passed: boolean;
  submittedAt: string;
  details: BlankResultDetail[];
}

export interface LastAttemptResponse {
  passed: boolean;
  percentage: number;
  submittedAt: string;
}
