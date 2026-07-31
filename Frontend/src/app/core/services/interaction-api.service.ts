import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

export interface InteractionView {
  type: 'login' | 'consent';
  clientName: string;
  scopes: string[];
  expiresAt: string;
}

interface ContinueResult {
  continueUrl: string;
}

@Injectable({ providedIn: 'root' })
export class InteractionApiService {
  constructor(private readonly http: HttpClient) {}

  get(interactionId: string): Observable<InteractionView> {
    return this.http.get<InteractionView>(`/api/v1/interactions/${encodeURIComponent(interactionId)}`);
  }

  login(interactionId: string, email: string, password: string): Observable<ContinueResult> {
    return this.http.post<ContinueResult>(
      `/api/v1/interactions/${encodeURIComponent(interactionId)}/login`,
      { email, password },
    );
  }

  consent(interactionId: string, approved: boolean): Observable<ContinueResult> {
    return this.http.post<ContinueResult>(
      `/api/v1/interactions/${encodeURIComponent(interactionId)}/consent`,
      { approved },
    );
  }
}
