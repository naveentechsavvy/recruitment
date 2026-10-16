import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import { API_BASE_URL } from '../config/api.config';
import { HiringManager, HiringManagerInput, HiringManagerStatus } from '../models/hiring.model';

/** Shape returned by the Spring Boot API (see HiringManagerResponse in Java). */
interface HiringManagerApi {
  id: number;
  clientId: number;
  name: string;
  email: string;
  phone: string | null;
  department: string | null;
  status: HiringManagerStatus;
  createdAt: string;
  updatedAt: string;
}

const toModel = (h: HiringManagerApi): HiringManager => ({
  id: String(h.id),
  clientId: String(h.clientId),
  name: h.name,
  email: h.email,
  phone: h.phone ?? '',
  department: h.department ?? '',
  status: h.status,
  createdAt: h.createdAt,
  updatedAt: h.updatedAt,
});

/** Talks to the hiring manager endpoints. No business logic here: it only sends and receives data. */
@Injectable({ providedIn: 'root' })
export class HiringManagerService {
  private http = inject(HttpClient);
  private clientsUrl = `${API_BASE_URL}/api/clients`;
  private url = `${API_BASE_URL}/api/hiring-managers`;

  getByClient(clientId: string): Observable<HiringManager[]> {
    return this.http.get<HiringManagerApi[]>(`${this.clientsUrl}/${clientId}/hiring-managers`)
      .pipe(map((list) => list.map(toModel)));
  }

  getById(id: string): Observable<HiringManager> {
    return this.http.get<HiringManagerApi>(`${this.url}/${id}`).pipe(map(toModel));
  }

  create(clientId: string, input: HiringManagerInput): Observable<HiringManager> {
    return this.http.post<HiringManagerApi>(`${this.clientsUrl}/${clientId}/hiring-managers`, this.body(input))
      .pipe(map(toModel));
  }

  update(id: string, input: HiringManagerInput): Observable<HiringManager> {
    return this.http.put<HiringManagerApi>(`${this.url}/${id}`, this.body(input)).pipe(map(toModel));
  }

  private body(input: HiringManagerInput) {
    return { ...input, phone: input.phone || null, department: input.department || null };
  }
}
