import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, from, map, of, switchMap } from 'rxjs';
import { API_BASE_URL } from '../config/api.config';
import {
  ClientConfiguration,
  ClientConfigurationInput,
  ClientStatus,
  StageId,
  ThemeId,
  portalColors,
  sortStages,
} from '../models/client-configuration.model';

/** Shapes returned by the Spring Boot API (see ClientResponse / RecruitmentProcessResponse in Java). */
interface ClientApi {
  id: number;
  name: string;
  slug: string | null;
  description: string | null;
  logoUrl: string | null;
  applicationName: string | null;
  theme: string;
  /** Not present on records saved before header/footer colours existed. */
  headerColor?: string | null;
  footerColor?: string | null;
  footer?: {
    companyName: string | null;
    address: string | null;
    contactEmail: string | null;
    phone: string | null;
    websiteUrl: string | null;
    copyrightText: string | null;
    customText: string | null;
  } | null;
  status: ClientStatus;
  processCodes: string[];
  applicationGenerated?: boolean;
  frozen?: boolean;
  frozenAt?: string | null;
  frozenBy?: string | null;
  createdAt: string;
  updatedAt: string;
}
interface ProcessApi { id: number; code: string; name: string; description: string; }

/** Converts the API shape into the model the Angular pages already use, so no component had to change. */
const toModel = (c: ClientApi): ClientConfiguration => {
  const colors = portalColors(c); // old records without saved colours get their theme's preset colours
  return {
  id: String(c.id),
  slug: c.slug ?? '',
  clientName: c.name,
  logo: c.logoUrl ? API_BASE_URL + c.logoUrl : null,
  description: c.description ?? '',
  applicationName: c.applicationName ?? '',
  theme: c.theme as ThemeId,
  headerColor: colors.header,
  footerColor: colors.footer,
  footer: {
    companyName: c.footer?.companyName ?? '',
    companyAddress: c.footer?.address ?? '',
    contactEmail: c.footer?.contactEmail ?? '',
    phone: c.footer?.phone ?? '',
    websiteUrl: c.footer?.websiteUrl ?? '',
    copyrightText: c.footer?.copyrightText ?? '',
    customText: c.footer?.customText ?? '',
  },
  recruitmentStages: c.processCodes as StageId[],
  status: c.status,
  applicationGenerated: c.applicationGenerated ?? c.status !== 'Draft',
  frozen: c.frozen ?? false,
  frozenAt: c.frozenAt ?? null,
  frozenBy: c.frozenBy ?? null,
  createdAt: c.createdAt,
  updatedAt: c.updatedAt,
  };
};

/**
 * Talks to the Spring Boot REST API. Same public methods as the old localStorage version,
 * so the components are unchanged. No business logic lives here: it only sends and receives data.
 */
@Injectable({ providedIn: 'root' })
export class ClientConfigurationService {
  private http = inject(HttpClient);
  private clientsUrl = `${API_BASE_URL}/api/clients`;
  private processesUrl = `${API_BASE_URL}/api/recruitment-processes`;

  getClients(): Observable<ClientConfiguration[]> {
    return this.http.get<ClientApi[]>(this.clientsUrl).pipe(map((list) => list.map(toModel)));
  }

  getClientById(id: string): Observable<ClientConfiguration> {
    return this.http.get<ClientApi>(`${this.clientsUrl}/${id}`).pipe(map(toModel));
  }

  /** New client saved as Draft (or, if an id is given, an existing one updated as Draft). */
  saveDraft(input: ClientConfigurationInput, id?: string): Observable<ClientConfiguration> {
    return this.save(input, 'Draft', id);
  }

  /** "Generate Application" for now only stores the configuration with status Ready. */
  generateConfiguration(input: ClientConfigurationInput, id?: string): Observable<ClientConfiguration> {
    return this.save(input, 'Ready', id);
  }

  /** Updates a client and keeps its current status. */
  updateClient(id: string, input: ClientConfigurationInput): Observable<ClientConfiguration> {
    return this.save(input, undefined, id);
  }

  deleteClient(id: string): Observable<void> {
    return this.http.delete<void>(`${this.clientsUrl}/${id}`);
  }

  /** Freezes one client portal. The backend then refuses new applications and changes for that client only. */
  freezeClient(id: string): Observable<ClientConfiguration> {
    return this.http.post<ClientApi>(`${this.clientsUrl}/${id}/freeze`, {}).pipe(map(toModel));
  }

  unfreezeClient(id: string): Observable<ClientConfiguration> {
    return this.http.post<ClientApi>(`${this.clientsUrl}/${id}/unfreeze`, {}).pipe(map(toModel));
  }

  // ---- internals: one save = client, then its processes, then its logo ----

  private save(input: ClientConfigurationInput, status: ClientStatus | undefined, id?: string): Observable<ClientConfiguration> {
    const body = {
      name: input.clientName,
      description: input.description,
      applicationName: input.applicationName,
      theme: input.theme,
      headerColor: input.headerColor,
      footerColor: input.footerColor,
      // Blank values clear a saved field (the backend turns "" into null).
      footer: {
        companyName: input.footer.companyName.trim(),
        address: input.footer.companyAddress.trim(),
        contactEmail: input.footer.contactEmail.trim(),
        phone: input.footer.phone.trim(),
        websiteUrl: input.footer.websiteUrl.trim(),
        copyrightText: input.footer.copyrightText.trim(),
        customText: input.footer.customText.trim(),
      },
      status, // undefined is omitted from the JSON, so the backend keeps the current status
    };
    const client$ = id
      ? this.http.put<ClientApi>(`${this.clientsUrl}/${id}`, body)
      : this.http.post<ClientApi>(this.clientsUrl, body);

    return client$.pipe(
      switchMap((c) => this.saveProcesses(c.id, input.recruitmentStages)),
      switchMap((clientId) => this.saveLogo(clientId, input.logo, !!id)),
      switchMap((clientId) => this.http.get<ClientApi>(`${this.clientsUrl}/${clientId}`)),
      map(toModel),
    );
  }

  /** Looks up process ids by their code, then sends the ordered selection to the backend. */
  private saveProcesses(clientId: number, stages: StageId[]): Observable<number> {
    return this.http.get<ProcessApi[]>(this.processesUrl).pipe(
      switchMap((all) => {
        const processes = sortStages(stages).map((code, index) => {
          const match = all.find((p) => p.code === code);
          if (!match) throw new Error(`Unknown recruitment process: ${code}`);
          return { processId: match.id, enabled: true, displayOrder: index + 1 };
        });
        return this.http.post(`${this.clientsUrl}/${clientId}/processes`, { processes });
      }),
      map(() => clientId),
    );
  }

  /** data: URL = a newly chosen image, so upload it. null = logo removed. Anything else = unchanged. */
  private saveLogo(clientId: number, logo: string | null, isUpdate: boolean): Observable<number> {
    if (logo && logo.startsWith('data:')) {
      return from(fetch(logo).then((r) => r.blob())).pipe(
        switchMap((blob) => {
          const form = new FormData();
          form.append('file', blob, 'logo');
          return this.http.post(`${this.clientsUrl}/${clientId}/logo`, form);
        }),
        map(() => clientId),
      );
    }
    if (!logo && isUpdate) {
      return this.http.delete(`${this.clientsUrl}/${clientId}/logo`).pipe(map(() => clientId));
    }
    return of(clientId);
  }
}