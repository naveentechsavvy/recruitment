import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { API_BASE_URL } from '../config/api.config';
import {
  EmploymentType, Requirement, RequirementInput, RequirementPriority, RequirementStatus,
} from '../models/hiring.model';

/** Shape returned by the Spring Boot API (see RequirementResponse in Java). */
interface RequirementApi {
  id: number;
  clientId: number;
  hiringManagerId: number;
  hiringManagerName: string;
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
  status: RequirementStatus;
  createdAt: string;
  updatedAt: string;
}

const toModel = (r: RequirementApi): Requirement => ({
  ...r,
  id: String(r.id),
  clientId: String(r.clientId),
  hiringManagerId: String(r.hiringManagerId),
  department: r.department ?? '',
});

/** Talks to the requirement endpoints. Every list is scoped to one client. */
@Injectable({ providedIn: 'root' })
export class RequirementService {
  private http = inject(HttpClient);
  private clientsUrl = `${API_BASE_URL}/api/clients`;
  private url = `${API_BASE_URL}/api/requirements`;

  getByClient(clientId: string): Observable<Requirement[]> {
    return this.http.get<RequirementApi[]>(`${this.clientsUrl}/${clientId}/requirements`)
      .pipe(map((list) => list.map(toModel)));
  }

  getById(id: string): Observable<Requirement> {
    return this.http.get<RequirementApi>(`${this.url}/${id}`).pipe(map(toModel));
  }

  create(clientId: string, input: RequirementInput): Observable<Requirement> {
    return this.http.post<RequirementApi>(`${this.clientsUrl}/${clientId}/requirements`, this.body(input))
      .pipe(map(toModel));
  }

  update(id: string, input: RequirementInput): Observable<Requirement> {
    return this.http.put<RequirementApi>(`${this.url}/${id}`, this.body(input)).pipe(map(toModel));
  }

  updateStatus(id: string, status: RequirementStatus): Observable<Requirement> {
    return this.http.patch<RequirementApi>(`${this.url}/${id}/status`, { status }).pipe(map(toModel));
  }

  private body(input: RequirementInput) {
    return { ...input, hiringManagerId: Number(input.hiringManagerId), department: input.department || null };
  }
}
