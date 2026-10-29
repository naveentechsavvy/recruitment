import { HttpClient, HttpContext } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../config/api.config';
import { CLIENT_SLUG } from '../interceptors/auth.interceptor';
import { Candidate, ScreeningAction, ScreeningInput, StageDecision } from '../models/candidate.model';

/**
 * Candidate calls for ONE client website. The methods and their arguments are the same as the Admin Portal's
 * CandidateService, so the website screens can be the same screens. Every call carries that website's own login
 * (CLIENT_SLUG), never the Admin Portal's, and the backend takes the client from that login.
 */
export class SiteCandidateApi {
  private url = `${API_BASE_URL}/api/site`;
  private context: HttpContext;

  constructor(private http: HttpClient, slug: string) {
    this.context = new HttpContext().set(CLIENT_SLUG, slug);
  }

  getByRequirement(requirementId: string): Observable<Candidate[]> {
    return this.http.get<Candidate[]>(`${this.url}/requirements/${requirementId}/candidates`, { context: this.context });
  }

  getById(id: string): Observable<Candidate> {
    return this.http.get<Candidate>(`${this.url}/candidates/${id}`, { context: this.context });
  }

  /** Uploads a candidate's resume for a job. The form is sent as multipart. */
  create(requirementId: string, form: FormData): Observable<Candidate> {
    return this.http.post<Candidate>(`${this.url}/requirements/${requirementId}/candidates`, form, { context: this.context });
  }

  updateScreening(id: number, input: ScreeningInput): Observable<Candidate> {
    return this.http.put<Candidate>(`${this.url}/candidates/${id}/screening`, input, { context: this.context });
  }

  screeningAction(id: number, action: ScreeningAction, remarks: string): Observable<Candidate> {
    return this.http.post<Candidate>(`${this.url}/candidates/${id}/screening/action`, { action, remarks }, { context: this.context });
  }

  addRemark(id: number, text: string): Observable<Candidate> {
    return this.http.post<Candidate>(`${this.url}/candidates/${id}/remarks`, { text }, { context: this.context });
  }

  updateStage(id: number, stageCode: string, decision: StageDecision, notes: string, rating: number | null = null): Observable<Candidate> {
    return this.http.put<Candidate>(`${this.url}/candidates/${id}/stages/${encodeURIComponent(stageCode)}`, { decision, notes, rating }, { context: this.context });
  }

  replaceResume(id: number, file: File): Observable<Candidate> {
    const form = new FormData();
    form.append('resume', file);
    return this.http.put<Candidate>(`${this.url}/candidates/${id}/resume`, form, { context: this.context });
  }

  /** The resume is private, so it is fetched with the login token and then saved from memory. */
  downloadResume(id: number): Observable<Blob> {
    return this.http.get(`${this.url}/candidates/${id}/resume`, { context: this.context, responseType: 'blob' });
  }
}

@Injectable({ providedIn: 'root' })
export class SiteCandidateService {
  private http = inject(HttpClient);

  /** The candidate API of the website with this slug (/app/:slug). */
  forSite(slug: string): SiteCandidateApi {
    return new SiteCandidateApi(this.http, slug);
  }
}
