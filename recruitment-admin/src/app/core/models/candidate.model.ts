export type CandidateStatus = 'ACTIVE' | 'ON_HOLD' | 'REJECTED' | 'COMPLETED';
export type ScreeningStatus = 'PENDING' | 'PROCEEDED' | 'ON_HOLD' | 'REJECTED';
export type StageDecision = 'PENDING' | 'APPROVED' | 'REJECTED' | 'HOLD';
export type ScreeningAction = 'PROCEED' | 'REJECT' | 'ON_HOLD';
export type EventType =
  | 'APPLIED' | 'RESUME_UPLOADED' | 'SCREENING_UPDATED' | 'SCREENING_PROCEEDED' | 'SCREENING_ON_HOLD'
  | 'SCREENING_REJECTED' | 'STAGE_DECISION' | 'STAGE_MOVED' | 'COMPLETED' | 'REMARK';

/** One stage of one candidate. canEdit is decided by the server for the logged-in user. */
export interface CandidateStage {
  code: string;
  name: string;
  description: string | null;
  decision: StageDecision;
  notes: string | null;
  /** 1 to 5. Only the technical rounds use it. */
  rating: number | null;
  updatedBy: string | null;
  updatedAt: string | null;
  current: boolean;
  canEdit: boolean;
}

/** One line of the candidate's history. */
export interface TimelineEvent {
  id: number;
  type: EventType;
  title: string;
  details: string | null;
  actor: string;
  at: string;
}

export interface Candidate {
  id: number;
  clientId: number;
  requirementId: number;
  jobId: string;
  jobTitle: string;
  fullName: string;
  email: string;
  phone: string | null;
  status: CandidateStatus;
  screeningStatus: ScreeningStatus;
  currentStageCode: string | null;
  currentStageName: string | null;
  hasResume: boolean;
  resumeName: string | null;
  appliedAt: string;
  totalExperience: number | null;
  relevantExperience: number | null;
  currentCtc: number | null;
  expectedCtc: number | null;
  noticePeriodDays: number | null;
  skills: string | null;
  /** Details still needed before the candidate can Proceed. */
  missingScreeningFields: string[];
  /** The server says whether this user may edit the screening details. */
  canScreen: boolean;
  canRemark: boolean;
  stages: CandidateStage[]; // empty in list responses
  timeline: TimelineEvent[]; // empty in list responses
}

/** What the screening form submits. */
export interface ScreeningInput {
  totalExperience: number | null;
  relevantExperience: number | null;
  currentCtc: number | null;
  expectedCtc: number | null;
  noticePeriodDays: number | null;
  skills: string;
  remarks: string;
}

/** Label shown in pills ("Active", "On Hold", "Approved", ...). */
export const statusLabel = (s: string): string =>
  s.split('_').map((w) => w.charAt(0) + w.slice(1).toLowerCase()).join(' ');
