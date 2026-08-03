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

export interface MfaAuditEvent {
  id: string;
  occurredAt: string;
  eventType: string;
  result: 'SUCCEEDED' | 'FAILED' | 'DENIED';
  reasonCode: string | null;
  correlationId: string;
}

export interface MfaAuditPage {
  items: MfaAuditEvent[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
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

  auditEvents(): Observable<MfaAuditPage> {
    return this.http.get<MfaAuditPage>('/api/v1/mfa/audit-events', { params: { size: 10 } });
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
