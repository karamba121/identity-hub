import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { AdminOAuthSessionService } from './admin-oauth-session.service';

export interface AdminTenantContext {
  tenantId: string;
  slug: string;
  displayName: string;
  roleCode: string;
  roleDisplayName: string;
  permissions: string[];
}

export interface OAuthClientView {
  clientId: string;
  clientName: string;
  redirectUris: string[];
  postLogoutRedirectUris: string[];
  scopes: string[];
  clientType: 'PUBLIC';
  pkceRequired: boolean;
  createdAt: string;
}

export interface OAuthClientCommand {
  clientId?: string;
  clientName: string;
  redirectUris: string[];
  postLogoutRedirectUris: string[];
  scopes: string[];
}

@Injectable({ providedIn: 'root' })
export class OAuthClientAdminApiService {
  constructor(
    private readonly http: HttpClient,
    private readonly session: AdminOAuthSessionService,
  ) {}

  context(): Promise<AdminTenantContext[]> {
    return firstValueFrom(this.http.get<AdminTenantContext[]>('/api/v1/admin/context', {
      headers: this.session.authorizationHeaders(),
    }));
  }

  list(tenantId: string): Promise<OAuthClientView[]> {
    return firstValueFrom(this.http.get<OAuthClientView[]>(this.baseUrl(tenantId), {
      headers: this.session.authorizationHeaders(),
    }));
  }

  create(tenantId: string, command: OAuthClientCommand): Promise<OAuthClientView> {
    return firstValueFrom(this.http.post<OAuthClientView>(this.baseUrl(tenantId), command, {
      headers: this.session.authorizationHeaders(),
    }));
  }

  update(tenantId: string, clientId: string, command: OAuthClientCommand): Promise<OAuthClientView> {
    return firstValueFrom(this.http.put<OAuthClientView>(
      `${this.baseUrl(tenantId)}/${encodeURIComponent(clientId)}`,
      command,
      { headers: this.session.authorizationHeaders() },
    ));
  }

  remove(tenantId: string, clientId: string): Promise<void> {
    return firstValueFrom(this.http.delete<void>(
      `${this.baseUrl(tenantId)}/${encodeURIComponent(clientId)}`,
      { headers: this.session.authorizationHeaders() },
    ));
  }

  private baseUrl(tenantId: string): string {
    return `/api/v1/admin/tenants/${encodeURIComponent(tenantId)}/oauth-clients`;
  }
}
