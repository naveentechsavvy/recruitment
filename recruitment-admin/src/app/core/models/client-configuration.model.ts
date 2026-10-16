export type ClientStatus = 'Draft' | 'Ready' | 'Active';
/** A preset name ("corporate") or a custom colour code ("#e11d48"). */
export type ThemeId = string;

export type StageId =
  | 'candidate-application'
  | 'resume-screening'
  | 'hiring-manager-review'
  | 'interview-scheduling'
  | 'technical-round'
  | 'technical-round-l1'
  | 'technical-round-l2'
  | 'technical-functional-assessment'
  | 'interview-feedback'
  | 'manager-round'
  | 'hr-round'
  | 'compensation-discussion'
  | 'final-selection'
  | 'offer-approval'
  | 'offer-released'
  | 'offer-accepted'
  | 'background-verification'
  | 'pre-boarding'
  | 'onboarding-joined'
  | 'rejected';

export type StageClass = 'Standard' | 'Recommended' | 'Optional' | 'Legacy';

export interface StageOption {
  id: StageId;
  label: string;
  classification: StageClass;
  description: string;
  /** Older stages that are no longer offered. They only show up for a client that already uses them. */
  legacy?: boolean;
}

/**
 * Predefined order. Selected stages are always stored in this order.
 * The 18 stages of the recruitment process, plus two older ones (flagged legacy) so existing clients keep working.
 * Keep the names and descriptions in step with DataSeeder.java in the backend.
 */
export const STAGE_OPTIONS: readonly StageOption[] = [
  { id: 'candidate-application', label: 'Candidate Application', classification: 'Standard', description: 'Candidate submits an application for a job opening.' },
  { id: 'resume-screening', label: 'Resume Screening', classification: 'Standard', description: 'Recruiter reviews resumes and checks eligibility against job requirements.' },
  { id: 'hiring-manager-review', label: 'Hiring Manager Review', classification: 'Standard', description: 'Hiring manager reviews shortlisted candidate profiles.' },
  { id: 'interview-scheduling', label: 'Interview Scheduling', classification: 'Recommended', description: 'Recruiter schedules interviews and sends invitations to candidates and interviewers.' },
  { id: 'technical-round', label: 'Technical Round', classification: 'Legacy', description: 'Technical interview / assessment', legacy: true },
  { id: 'technical-round-l1', label: 'Technical Round L1', classification: 'Optional', description: 'First-level technical interview or assessment.' },
  { id: 'technical-round-l2', label: 'Technical Round L2', classification: 'Optional', description: 'Second-level technical interview or assessment.' },
  { id: 'technical-functional-assessment', label: 'Technical / Functional Assessment', classification: 'Optional', description: 'Candidate completes a coding test, assignment, skill test, or functional assessment.' },
  { id: 'interview-feedback', label: 'Interview Feedback', classification: 'Standard', description: 'Interviewers submit ratings, comments, and recommendations.' },
  { id: 'manager-round', label: 'Manager Round', classification: 'Optional', description: 'Hiring manager or project manager evaluates experience, delivery skills, and team fit.' },
  { id: 'hr-round', label: 'HR Round', classification: 'Standard', description: 'HR evaluates communication, culture fit, availability, and employment details.' },
  { id: 'compensation-discussion', label: 'Compensation Discussion', classification: 'Standard', description: 'Recruiter discusses salary expectations, budget, notice period, and benefits.' },
  { id: 'final-selection', label: 'Final Selection', classification: 'Standard', description: 'Hiring team confirms the final hiring decision.' },
  { id: 'offer-approval', label: 'Offer Approval', classification: 'Recommended', description: 'Authorized stakeholders approve compensation and employment terms.' },
  { id: 'offer-released', label: 'Offer Released', classification: 'Standard', description: 'Official offer letter is issued to the candidate.' },
  { id: 'offer-accepted', label: 'Offer Accepted', classification: 'Standard', description: 'Candidate formally accepts the offer.' },
  { id: 'background-verification', label: 'Background Verification', classification: 'Optional', description: 'Employment, education, identity, and reference checks are performed as applicable.' },
  { id: 'pre-boarding', label: 'Pre-boarding', classification: 'Optional', description: 'Candidate completes pre-joining documentation and joining formalities.' },
  { id: 'onboarding-joined', label: 'Onboarding / Joined', classification: 'Standard', description: 'Candidate joins the organization and completes onboarding.' },
  { id: 'rejected', label: 'Rejected', classification: 'Legacy', description: 'Candidate exits the process', legacy: true },
];

export interface ThemeOption { id: ThemeId; label: string; color: string; secondary: string; }

/** The themes offered on the client form. "color" is the header (primary) colour, "secondary" the footer colour. */
export const THEME_OPTIONS: readonly ThemeOption[] = [
  { id: 'default', label: 'Professional Blue', color: '#2563eb', secondary: '#1e3a8a' },
  { id: 'navy', label: 'Navy', color: '#0f172a', secondary: '#2563eb' },
  { id: 'slate', label: 'Slate', color: '#334155', secondary: '#0f766e' },
  { id: 'indigo', label: 'Indigo', color: '#4f46e5', secondary: '#312e81' },
  { id: 'teal', label: 'Teal', color: '#0f766e', secondary: '#134e4a' },
  { id: 'charcoal', label: 'Charcoal', color: '#18181b', secondary: '#3f3f46' },
  { id: 'royal-blue', label: 'Royal Blue', color: '#1d4ed8', secondary: '#172554' },
  { id: 'blue-cyan', label: 'Blue + Cyan', color: '#0369a1', secondary: '#0891b2' },
];

/** Older theme names that clients may already have saved. Not offered any more, but they keep working. */
const LEGACY_THEMES: readonly ThemeOption[] = [
  { id: 'corporate', label: 'Corporate', color: '#0f766e', secondary: '#134e4a' },
  { id: 'modern', label: 'Modern', color: '#7c3aed', secondary: '#4c1d95' },
  { id: 'red', label: 'Red', color: '#dc2626', secondary: '#7f1d1d' },
  { id: 'orange', label: 'Orange', color: '#ea580c', secondary: '#9a3412' },
  { id: 'green', label: 'Green', color: '#16a34a', secondary: '#14532d' },
  { id: 'pink', label: 'Pink', color: '#db2777', secondary: '#831843' },
];

const HEX = /^#[0-9a-fA-F]{6}$/;

const findTheme = (id: string | null | undefined): ThemeOption | undefined =>
  THEME_OPTIONS.find((t) => t.id === id) ?? LEGACY_THEMES.find((t) => t.id === id);

/** Darkens a #rrggbb colour. Used for the second header colour of a custom colour. */
const darken = (hex: string, amount: number): string => {
  const n = parseInt(hex.slice(1), 16);
  const part = (shift: number) => Math.round(((n >> shift) & 255) * (1 - amount)).toString(16).padStart(2, '0');
  return `#${part(16)}${part(8)}${part(0)}`;
};

/** The header (primary) colour for a saved theme: a preset's colour, or the custom colour code itself. */
export const themeColor = (theme: string | null | undefined): string => {
  if (theme && HEX.test(theme)) return theme;
  return (findTheme(theme) ?? THEME_OPTIONS[0]).color;
};

/** The footer colour for a saved theme. A custom colour with no saved footer colour gets a darker shade of itself. */
export const themeSecondary = (theme: string | null | undefined): string => {
  if (theme && HEX.test(theme)) return darken(theme, 0.25);
  return (findTheme(theme) ?? THEME_OPTIONS[0]).secondary;
};

/** True for a full 6-digit HEX colour such as #2563EB. */
export const isHex = (v: string | null | undefined): v is string => typeof v === 'string' && HEX.test(v);

// ---- contrast helpers (WCAG relative luminance) ----
const channel = (c: number): number => { const s = c / 255; return s <= 0.03928 ? s / 12.92 : ((s + 0.055) / 1.055) ** 2.4; };
const luminance = (hex: string): number => {
  const n = parseInt(hex.slice(1), 16);
  return 0.2126 * channel((n >> 16) & 255) + 0.7152 * channel((n >> 8) & 255) + 0.0722 * channel(n & 255);
};
export const contrastRatio = (a: string, b: string): number => {
  const [hi, lo] = [luminance(a), luminance(b)].sort((x, y) => y - x);
  return (hi + 0.05) / (lo + 0.05);
};
/** White or black text, whichever is easier to read on this background (always at least 4.5:1). */
export const textOn = (bg: string): string => (contrastRatio(bg, '#ffffff') >= contrastRatio(bg, '#000000') ? '#ffffff' : '#000000');
/** The colour darkened until it can be used as text on a white page (links, headings). */
export const readableOnWhite = (hex: string): string => {
  let c = hex;
  for (let i = 0; i < 20 && contrastRatio(c, '#ffffff') < 4.5; i++) c = darken(c, 0.1);
  return c;
};

/** Everything the portal (and the admin preview) needs to paint the header and footer. */
export interface PortalColors {
  header: string; header2: string; onHeader: string;
  footer: string; onFooter: string;
  /** Header colour made readable as text on white. */
  accentText: string;
}

/**
 * Works out the colours from saved values. Records saved before header/footer colours existed
 * only have a theme, so they fall back to that theme's preset colours.
 */
export const portalColors = (src: { theme?: string | null; headerColor?: string | null; footerColor?: string | null } | null | undefined): PortalColors => {
  const header = isHex(src?.headerColor) ? src!.headerColor! : themeColor(src?.theme);
  const footer = isHex(src?.footerColor) ? src!.footerColor! : themeSecondary(src?.theme);
  const onHeader = textOn(header);
  return {
    header,
    header2: onHeader === '#ffffff' ? darken(header, 0.25) : header, // gradient only where white text gets darker behind it
    onHeader,
    footer,
    onFooter: textOn(footer),
    accentText: readableOnWhite(header),
  };
};

export const themeLabel = (theme: string): string =>
  HEX.test(theme) ? 'Custom colour' : findTheme(theme)?.label ?? theme;

/** Footer content of the generated portal. Every field is optional; an empty string means "not set". */
export interface FooterSettings {
  companyName: string;
  companyAddress: string;
  contactEmail: string;
  phone: string;
  websiteUrl: string;
  copyrightText: string;
  customText: string;
}
export const EMPTY_FOOTER: FooterSettings = {
  companyName: '', companyAddress: '', contactEmail: '', phone: '', websiteUrl: '', copyrightText: '', customText: '',
};

export interface ClientConfiguration {
  id: string;
  slug: string; // public link name, e.g. "samsung" -> /app/samsung
  clientName: string;
  logo: string | null; // data URL for now; becomes a file URL once a backend exists
  description: string;
  applicationName: string;
  theme: ThemeId;
  /** Header colour (#rrggbb). Always filled in: old records get their theme's preset colour. */
  headerColor: string;
  /** Footer colour (#rrggbb). Always filled in: old records get their theme's preset colour. */
  footerColor: string;
  footer: FooterSettings;
  recruitmentStages: StageId[];
  status: ClientStatus;
  /** True once the application is out of Draft: the form then shows "Save" instead of "Generate Application". */
  applicationGenerated: boolean;
  /** A frozen portal keeps its data but takes no new applications or changes. */
  frozen: boolean;
  frozenAt: string | null;
  frozenBy: string | null;
  createdAt: string; // ISO date
  updatedAt: string; // ISO date
}

/** What the form submits; id/status/timestamps are owned by the service. */
export type ClientConfigurationInput = Pick<
  ClientConfiguration,
  'clientName' | 'logo' | 'description' | 'applicationName' | 'theme' | 'headerColor' | 'footerColor' | 'footer' | 'recruitmentStages'
>;

export const stageLabel = (id: StageId): string =>
  STAGE_OPTIONS.find((s) => s.id === id)?.label ?? id;

export const sortStages = (ids: StageId[]): StageId[] =>
  STAGE_OPTIONS.map((s) => s.id).filter((id) => ids.includes(id));
