import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';

export interface MfaStatus {
  enabled: boolean;
  recoveryCodesRemaining: number;
}

export interface MfaEnrollment {
  secret: string;
  otpauthUri: string;
}

@Injectable({ providedIn: 'root' })
export class MfaApiService {
  constructor(private readonly http: HttpClient) {}

  prepareCsrf(): Observable<unknown> {
    return this.http.get('/api/v1/registrations/csrf');
  }

  status(): Observable<MfaStatus> {
    return this.http.get<MfaStatus>('/api/v1/mfa');
  }

  enroll(): Observable<MfaEnrollment> {
    return this.http.post<MfaEnrollment>('/api/v1/mfa/enrollment', {});
  }

  confirm(code: string): Observable<{ recoveryCodes: string[] }> {
    return this.http.post<{ recoveryCodes: string[] }>('/api/v1/mfa/enrollment/confirm', { code });
  }

  regenerate(code: string): Observable<{ recoveryCodes: string[] }> {
    return this.http.post<{ recoveryCodes: string[] }>('/api/v1/mfa/recovery-codes', { code });
  }

  disable(code: string): Observable<void> {
    return this.http.delete<void>('/api/v1/mfa', { body: { code } });
  }
}
