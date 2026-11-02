import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { API_BASE_URL } from '../config/api.config';
import { OfferStatus } from '../models/candidate.model';

/** What the candidate sees on the offer page. */
export interface OfferPublicView {
  candidateName: string;
  jobTitle: string | null;
  companyName: string | null;
  status: OfferStatus;
  expiresAt: string;
  respondedAt: string | null;
  canRespond: boolean;
}

/**
 * The candidate's side of the offer (no login). The secret in the link is the only proof of who is asking.
 * Opening the page only reads the offer. The answer is a separate call the candidate confirms on the page.
 */
@Injectable({ providedIn: 'root' })
export class PublicOfferService {
  private http = inject(HttpClient);
  private url = `${API_BASE_URL}/api/public/offers`;

  view(token: string): Observable<OfferPublicView> {
    return this.http.get<OfferPublicView>(`${this.url}/${encodeURIComponent(token)}`);
  }

  respond(token: string, decision: 'ACCEPT' | 'DECLINE', reason: string): Observable<OfferPublicView> {
    return this.http.post<OfferPublicView>(`${this.url}/${encodeURIComponent(token)}/respond`, { decision, reason });
  }
}
