import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { map, Observable } from 'rxjs';

export interface PasswordRecoveryAccepted {
  message: string;
}

@Injectable({ providedIn: 'root' })
export class PasswordRecoveryApiService {
  constructor(private readonly http: HttpClient) {}

  prepareCsrf(): Observable<void> {
    return this.http.get('/api/v1/registrations/csrf').pipe(map(() => undefined));
  }

  request(email: string): Observable<PasswordRecoveryAccepted> {
    return this.http.post<PasswordRecoveryAccepted>('/api/v1/password-recovery', { email });
  }

  complete(token: string, newPassword: string): Observable<void> {
    return this.http.post<void>('/api/v1/password-recovery/complete', { token, newPassword });
  }
}
