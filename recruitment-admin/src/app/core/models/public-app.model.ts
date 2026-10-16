import { EmploymentType, RequirementPriority } from './hiring.model';

/** Footer content of one client website. Empty or null fields are simply not shown. */
export interface PublicFooter {
  companyName: string | null;
  address: string | null;
  contactEmail: string | null;
  phone: string | null;
  websiteUrl: string | null;
  copyrightText: string | null;
  customText: string | null;
}

/** What the public application page receives for one client (see PublicClientResponse in Java). */
export interface PublicApp {
  slug: string;
  name: string;
  applicationName: string | null;
  description: string | null;
  logoUrl: string | null;
  theme: string;
  /** Missing on records saved before header/footer colours existed; the theme is used then. */
  headerColor?: string | null;
  footerColor?: string | null;
  footer?: PublicFooter | null;
  stages: { code: string; name: string; description: string | null }[];
}

/** An open job on the public page (see PublicJobResponse in Java). No hiring manager details. */
export interface PublicJob {
  id: number;
  jobId: string;
  jobTitle: string;
  numberOfPositions: number;
  jobDescription: string;
  requiredSkills: string;
  minExperience: number;
  maxExperience: number;
  location: string;
  employmentType: EmploymentType;
  department: string | null;
  priority: RequirementPriority;
  createdAt: string;
}