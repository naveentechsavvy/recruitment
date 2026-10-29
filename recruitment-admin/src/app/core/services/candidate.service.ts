import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../config/api.config';
import { Candidate, ScreeningAction, ScreeningInput, StageDecision } from '../models/candidate.model';

/** Talks to the candidate endpoints. The server decides who may do what. */
@Injectable({ providedIn: 'root' })
export class CandidateService {
  private http = inject(HttpClient);
  private url = `${API_BASE_URL}/api`;

  getByRequirement(requirementId: string): Observable<Candidate[]> {
    return this.http.get<Candidate[]>(`${this.url}/requirements/${requirementId}/candidates`);
  }

  getById(id: string): Observable<Candidate> {
    return this.http.get<Candidate>(`${this.url}/candidates/${id}`);
  }

  /** Recruiter or admin uploads a candidate's resume for a job. The form is sent as multipart. */
  create(requirementId: string, form: FormData): Observable<Candidate> {
    return this.http.post<Candidate>(`${this.url}/requirements/${requirementId}/candidates`, form);
  }

  updateScreening(id: number, input: ScreeningInput): Observable<Candidate> {
    return this.http.put<Candidate>(`${this.url}/candidates/${id}/screening`, input);
  }

  screeningAction(id: number, action: ScreeningAction, remarks: string): Observable<Candidate> {
    return this.http.post<Candidate>(`${this.url}/candidates/${id}/screening/action`, { action, remarks });
  }

  addRemark(id: number, text: string): Observable<Candidate> {
    return this.http.post<Candidate>(`${this.url}/candidates/${id}/remarks`, { text });
  }

  updateStage(id: number, stageCode: string, decision: StageDecision, notes: string, rating: number | null = null): Observable<Candidate> {
    return this.http.put<Candidate>(`${this.url}/candidates/${id}/stages/${encodeURIComponent(stageCode)}`, { decision, notes, rating });
  }

  /** Replaces the resume of an existing candidate. The form carries one file in the field "resume". */
  replaceResume(id: number, file: File): Observable<Candidate> {
    const form = new FormData();
    form.append('resume', file);
    return this.http.put<Candidate>(`${this.url}/candidates/${id}/resume`, form);
  }

  /** The resume is private, so it is fetched with the login token and then saved from memory. */
  downloadResume(id: number): Observable<Blob> {
    return this.http.get(`${this.url}/candidates/${id}/resume`, { responseType: 'blob' });
  }
}
