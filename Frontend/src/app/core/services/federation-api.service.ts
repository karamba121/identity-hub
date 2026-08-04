import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable, switchMap } from 'rxjs';

export interface FederationProvider {
  id: string;
  displayName: string;
}

export interface FederatedIdentityLink {
  id: string;
  provider: string;
  emailAtLink: string;
  createdAt: string;
  lastLoginAt: string | null;
}

@Injectable({ providedIn: 'root' })
export class FederationApiService {
  constructor(private readonly http: HttpClient) {}

  providers(): Observable<FederationProvider[]> {
    return this.http.get<FederationProvider[]>('/api/v1/federation/providers');
  }

  links(): Observable<FederatedIdentityLink[]> {
    return this.http.get<FederatedIdentityLink[]>('/api/v1/federation/links');
  }

  loginUrl(interactionId: string, providerId: string): string {
    return `/api/v1/interactions/${encodeURIComponent(interactionId)}/federation/${encodeURIComponent(providerId)}`;
  }

  linkUrl(providerId: string): string {
    return `/api/v1/federation/${encodeURIComponent(providerId)}/link`;
  }

  unlink(id: string): Observable<void> {
    return this.http.get<void>('/api/v1/federation/csrf').pipe(
      switchMap(() => this.http.delete<void>(`/api/v1/federation/links/${encodeURIComponent(id)}`)),
    );
  }
}
