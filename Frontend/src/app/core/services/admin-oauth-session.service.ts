import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { firstValueFrom } from 'rxjs';

interface TokenResponse {
  access_token: string;
  refresh_token?: string;
  id_token?: string;
  expires_in: number;
}

@Injectable({ providedIn: 'root' })
export class AdminOAuthSessionService {
  private readonly clientId = 'identity-hub-demo';
  private readonly callbackPath = '/admin/oauth-clients/callback';
  private readonly refreshTokenKey = 'identity-hub.admin.refresh-token';
  private readonly verifierKey = 'identity-hub.admin.pkce-verifier';
  private readonly stateKey = 'identity-hub.admin.oauth-state';
  private readonly nonceKey = 'identity-hub.admin.oidc-nonce';

  private accessToken: string | null = null;
  private accessTokenExpiresAt = 0;

  constructor(private readonly http: HttpClient) {}

  async startAuthorization(): Promise<void> {
    const verifier = this.randomValue(64);
    const state = this.randomValue(32);
    const nonce = this.randomValue(32);
    const challenge = await this.sha256(verifier);
    sessionStorage.setItem(this.verifierKey, verifier);
    sessionStorage.setItem(this.stateKey, state);
    sessionStorage.setItem(this.nonceKey, nonce);

    const authorizationUrl = new URL('/oauth2/authorize', window.location.origin);
    authorizationUrl.searchParams.set('response_type', 'code');
    authorizationUrl.searchParams.set('client_id', this.clientId);
    authorizationUrl.searchParams.set('redirect_uri', this.callbackUri());
    authorizationUrl.searchParams.set('scope', 'openid profile identity.admin');
    authorizationUrl.searchParams.set('state', state);
    authorizationUrl.searchParams.set('nonce', nonce);
    authorizationUrl.searchParams.set('code_challenge', challenge);
    authorizationUrl.searchParams.set('code_challenge_method', 'S256');
    window.location.assign(authorizationUrl.toString());
  }

  async completeAuthorization(code: string, returnedState: string): Promise<void> {
    const expectedState = sessionStorage.getItem(this.stateKey);
    const verifier = sessionStorage.getItem(this.verifierKey);
    const expectedNonce = sessionStorage.getItem(this.nonceKey);
    this.clearInteraction();
    if (!expectedState || returnedState !== expectedState || !verifier || !expectedNonce) {
      throw new Error('O retorno OAuth não corresponde ao fluxo administrativo iniciado.');
    }

    const token = await firstValueFrom(this.http.post<TokenResponse>(
      '/oauth2/token',
      new HttpParams()
        .set('grant_type', 'authorization_code')
        .set('client_id', this.clientId)
        .set('redirect_uri', this.callbackUri())
        .set('code', code)
        .set('code_verifier', verifier)
        .toString(),
      { headers: this.formHeaders() },
    ));
    const claims = token.id_token ? this.decodeJwt(token.id_token) : null;
    if (!claims || claims['nonce'] !== expectedNonce || !this.hasExpectedAudience(claims['aud'])) {
      throw new Error('O ID token administrativo não corresponde ao fluxo iniciado.');
    }
    this.acceptToken(token, true);
  }

  async restore(): Promise<boolean> {
    if (this.accessToken && Date.now() < this.accessTokenExpiresAt) {
      return true;
    }
    const refreshToken = sessionStorage.getItem(this.refreshTokenKey);
    if (!refreshToken) {
      return false;
    }
    try {
      const token = await firstValueFrom(this.http.post<TokenResponse>(
        '/oauth2/token',
        new HttpParams()
          .set('grant_type', 'refresh_token')
          .set('client_id', this.clientId)
          .set('refresh_token', refreshToken)
          .toString(),
        { headers: this.formHeaders() },
      ));
      this.acceptToken(token, true);
      return true;
    } catch {
      this.clear();
      return false;
    }
  }

  authorizationHeaders(): HttpHeaders {
    if (!this.accessToken || Date.now() >= this.accessTokenExpiresAt) {
      throw new Error('A sessão administrativa expirou.');
    }
    return new HttpHeaders({ Authorization: `Bearer ${this.accessToken}` });
  }

  async end(): Promise<void> {
    const refreshToken = sessionStorage.getItem(this.refreshTokenKey);
    if (refreshToken) {
      try {
        await firstValueFrom(this.http.post(
          '/oauth2/revoke',
          new HttpParams()
            .set('token', refreshToken)
            .set('token_type_hint', 'refresh_token')
            .set('client_id', this.clientId)
            .toString(),
          { headers: this.formHeaders(), responseType: 'text' },
        ));
      } finally {
        this.clear();
      }
      return;
    }
    this.clear();
  }

  clear(): void {
    this.accessToken = null;
    this.accessTokenExpiresAt = 0;
    sessionStorage.removeItem(this.refreshTokenKey);
    this.clearInteraction();
  }

  private acceptToken(token: TokenResponse, requireRefreshToken: boolean): void {
    if (!token.access_token || (requireRefreshToken && !token.refresh_token)) {
      throw new Error('O servidor não devolveu a sessão administrativa renovável esperada.');
    }
    this.accessToken = token.access_token;
    this.accessTokenExpiresAt = Date.now() + Math.max(token.expires_in - 15, 1) * 1000;
    if (token.refresh_token) {
      sessionStorage.setItem(this.refreshTokenKey, token.refresh_token);
    }
  }

  private callbackUri(): string {
    return `${window.location.origin}${this.callbackPath}`;
  }

  private formHeaders(): HttpHeaders {
    return new HttpHeaders({ 'Content-Type': 'application/x-www-form-urlencoded' });
  }

  private clearInteraction(): void {
    sessionStorage.removeItem(this.verifierKey);
    sessionStorage.removeItem(this.stateKey);
    sessionStorage.removeItem(this.nonceKey);
  }

  private randomValue(bytes: number): string {
    return this.base64Url(crypto.getRandomValues(new Uint8Array(bytes)));
  }

  private async sha256(value: string): Promise<string> {
    const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(value));
    return this.base64Url(new Uint8Array(digest));
  }

  private base64Url(value: Uint8Array): string {
    let binary = '';
    value.forEach(byte => binary += String.fromCharCode(byte));
    return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  }

  private decodeJwt(token: string): Record<string, unknown> | null {
    try {
      const payload = token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
      const paddedPayload = payload.padEnd(Math.ceil(payload.length / 4) * 4, '=');
      const bytes = Uint8Array.from(atob(paddedPayload), character => character.charCodeAt(0));
      return JSON.parse(new TextDecoder().decode(bytes)) as Record<string, unknown>;
    } catch {
      return null;
    }
  }

  private hasExpectedAudience(audience: unknown): boolean {
    return audience === this.clientId
      || (Array.isArray(audience) && audience.includes(this.clientId));
  }
}
