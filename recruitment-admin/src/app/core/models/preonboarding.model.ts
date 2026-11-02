/** Where a pre-onboarding case stands. Background Verification is separate and never follows from this. */
export type PreonboardingStatus =
  | 'CREATED' | 'INVITED' | 'IN_PROGRESS' | 'SUBMITTED' | 'CORRECTION_REQUIRED' | 'COMPLETED' | 'EXPIRED';

/** One thing a client can ask a new joiner for: a detail to fill in, or a document to upload. */
export interface PreonboardingItem {
  code: string;
  kind: 'DETAIL' | 'DOCUMENT';
  label: string;
  enabled: boolean;
  mandatory: boolean;
}

export interface PreonboardingSettings {
  submissionDays: number;
  items: PreonboardingItem[];
}

/** The case as the hiring team sees it on the candidate page. */
export interface PreonboardingCaseView {
  id: number;
  status: PreonboardingStatus;
  createdAt: string;
  dueAt: string;
}
