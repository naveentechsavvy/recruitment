import { HttpClient, HttpContext } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { Observable, map } from 'rxjs';
import { API_BASE_URL } from '../config/api.config';
import { CLIENT_SLUG } from '../interceptors/auth.interceptor';
import {
  EmploymentType, HiringManager, HiringManagerStatus, Requirement, RequirementInput, RequirementPriority, RequirementStatus,
} from '../models/hiring.model';

/** Shape returned by the Spring Boot API (RequirementResponse). */
interface RequirementApi {
  id: number; clientId: number; hiringManagerId: number; hiringManagerName: string;
  jobId: string; jobTitle: string; numberOfPositions: number; jobDescription: string; requiredSkills: string;
  minExperience: number; maxExperience: number; location: string; employmentType: EmploymentType;
  department: string | null; priority: RequirementPriority; status: RequirementStatus; createdAt: string; updatedAt: string;
}

/** Shape returned by the Spring Boot API (HiringManagerResponse). */
interface HiringManagerApi {
  id: number; clientId: number; name: string; email: string; phone: string | null; department: string | null;
  status: HiringManagerStatus; createdAt: string; updatedAt: string;
}

const toRequirement = (r: RequirementApi): Requirement => ({
  ...r, id: String(r.id), clientId: String(r.clientId), hiringManagerId: String(r.hiringManagerId), department: r.department ?? '',
});

const toManager = (h: HiringManagerApi): HiringManager => ({
  id: String(h.id), clientId: String(h.clientId), name: h.name, email: h.email, phone: h.phone ?? '',
  department: h.department ?? '', status: h.status, createdAt: h.createdAt, updatedAt: h.updatedAt,
});

/** The slug of the client website (/app/:slug/...) that the current route belongs to. */
export function siteSlug(route: ActivatedRoute): string {
  for (const r of route.snapshot.pathFromRoot) {
    const s = r.paramMap.get('slug');
    if (s) return s;
  }
  return '';
}

/**
 * Hiring features used INSIDE a client's website. Every call carries that website's own login (CLIENT_SLUG), never the
 * Admin Portal's, and the backend takes the client from that login. The admin services are not touched.
 */
@Injectable({ providedIn: 'root' })
export class SiteHiringService {
  private http = inject(HttpClient);
  private url = `${API_BASE_URL}/api/site`;

  private opts(slug: string) {
    return { context: new HttpContext().set(CLIENT_SLUG, slug) };
  }

  getRequirements(slug: string): Observable<Requirement[]> {
    return this.http.get<RequirementApi[]>(`${this.url}/requirements`, this.opts(slug)).pipe(map((l) => l.map(toRequirement)));
  }

  getRequirement(slug: string, id: string): Observable<Requirement> {
    return this.http.get<RequirementApi>(`${this.url}/requirements/${id}`, this.opts(slug)).pipe(map(toRequirement));
  }

  createRequirement(slug: string, input: RequirementInput): Observable<Requirement> {
    return this.http.post<RequirementApi>(`${this.url}/requirements`, this.body(input), this.opts(slug)).pipe(map(toRequirement));
  }

  updateRequirement(slug: string, id: string, input: RequirementInput): Observable<Requirement> {
    return this.http.put<RequirementApi>(`${this.url}/requirements/${id}`, this.body(input), this.opts(slug)).pipe(map(toRequirement));
  }

  updateRequirementStatus(slug: string, id: string, status: RequirementStatus): Observable<Requirement> {
    return this.http.patch<RequirementApi>(`${this.url}/requirements/${id}/status`, { status }, this.opts(slug)).pipe(map(toRequirement));
  }

  getHiringManagers(slug: string): Observable<HiringManager[]> {
    return this.http.get<HiringManagerApi[]>(`${this.url}/hiring-managers`, this.opts(slug)).pipe(map((l) => l.map(toManager)));
  }

  private body(input: RequirementInput) {
    return { ...input, hiringManagerId: Number(input.hiringManagerId), department: input.department || null };
  }
}
