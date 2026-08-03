import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

export interface InteractionView {
  type: 'login' | 'consent';
  clientName: string;
  scopes: string[];
  expiresAt: string;
}

export interface LoginResult {
  continueUrl: string | null;
  mfaRequired: boolean;
}

@Injectable({ providedIn: 'root' })
export class InteractionApiService {
  constructor(private readonly http: HttpClient) {}

  get(interactionId: string): Observable<InteractionView> {
    return this.http.get<InteractionView>(`/api/v1/interactions/${encodeURIComponent(interactionId)}`);
  }

  login(interactionId: string, email: string, password: string): Observable<LoginResult> {
    return this.http.post<LoginResult>(
      `/api/v1/interactions/${encodeURIComponent(interactionId)}/login`,
      { email, password },
    );
  }

  verifyMfa(interactionId: string, code: string): Observable<LoginResult> {
    return this.http.post<LoginResult>(
      `/api/v1/interactions/${encodeURIComponent(interactionId)}/mfa`,
      { code },
    );
  }

  consent(interactionId: string, approved: boolean): Observable<{ continueUrl: string }> {
    return this.http.post<{ continueUrl: string }>(
      `/api/v1/interactions/${encodeURIComponent(interactionId)}/consent`,
      { approved },
    );
  }
}
