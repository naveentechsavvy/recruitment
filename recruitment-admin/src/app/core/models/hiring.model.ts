export type HiringManagerStatus = 'Active' | 'Inactive';
export type RequirementStatus = 'Open' | 'On Hold' | 'Closed';
export type RequirementPriority = 'Low' | 'Medium' | 'High' | 'Critical';
export type EmploymentType = 'Full Time' | 'Part Time' | 'Contract' | 'Internship';

export const HIRING_MANAGER_STATUSES: readonly HiringManagerStatus[] = ['Active', 'Inactive'];
export const REQUIREMENT_STATUSES: readonly RequirementStatus[] = ['Open', 'On Hold', 'Closed'];
export const PRIORITIES: readonly RequirementPriority[] = ['Low', 'Medium', 'High', 'Critical'];
export const EMPLOYMENT_TYPES: readonly EmploymentType[] = ['Full Time', 'Part Time', 'Contract', 'Internship'];

export interface HiringManager {
  id: string;
  clientId: string;
  name: string;
  email: string;
  phone: string;
  department: string;
  status: HiringManagerStatus;
  createdAt: string;
  updatedAt: string;
}

/** What the hiring manager form submits. */
export type HiringManagerInput = Pick<HiringManager, 'name' | 'email' | 'phone' | 'department' | 'status'>;

export interface Requirement {
  id: string;
  clientId: string;
  hiringManagerId: string;
  hiringManagerName: string;
  jobId: string;
  jobTitle: string;
  numberOfPositions: number;
  jobDescription: string;
  requiredSkills: string; // comma-separated
  minExperience: number;
  maxExperience: number;
  location: string;
  employmentType: EmploymentType;
  department: string;
  priority: RequirementPriority;
  status: RequirementStatus;
  createdAt: string;
  updatedAt: string;
}

/** What the requirement form submits; client comes from the route, timestamps from the backend. */
export type RequirementInput = Pick<
  Requirement,
  | 'jobTitle' | 'jobId' | 'numberOfPositions' | 'jobDescription' | 'requiredSkills'
  | 'minExperience' | 'maxExperience' | 'location' | 'employmentType' | 'department'
  | 'priority' | 'status' | 'hiringManagerId'
>;

export const experienceLabel = (r: Pick<Requirement, 'minExperience' | 'maxExperience'>): string =>
  `${r.minExperience}–${r.maxExperience} Years`;

export const skillList = (skills: string): string[] =>
  skills.split(',').map((s) => s.trim()).filter(Boolean);
