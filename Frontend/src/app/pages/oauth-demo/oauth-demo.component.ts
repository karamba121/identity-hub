import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { DatePipe } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

interface TokenResponse {
  access_token: string;
  refresh_token?: string;
  id_token?: string;
  scope: string;
  token_type: string;
  expires_in: number;
}

interface DemoResourceResponse {
  message: string;
  subject: string;
  audience: string[];
  scopes: string[];
}

@Component({
  selector: 'app-oauth-demo',
  imports: [DatePipe],
  templateUrl: './oauth-demo.component.html',
})
export class OauthDemoComponent implements OnInit {
  loading = false;
  errorMessage = '';
  successMessage = '';
  profile: Record<string, unknown> | null = null;
  resource: DemoResourceResponse | null = null;
  sessionExpiresAt: Date | null = null;

  private readonly clientId = 'identity-hub-demo';
  private readonly refreshTokenKey = 'identity-hub.refresh-token';
  private readonly idTokenKey = 'identity-hub.id-token';
  private readonly logoutStateKey = 'identity-hub.logout-state';

  constructor(
    private readonly route: ActivatedRoute,
    private readonly http: HttpClient,
  ) {}

  ngOnInit(): void {
    if (this.route.snapshot.routeConfig?.path === 'demo/logout') {
      this.handleLogoutReturn();
      return;
    }
    const code = this.route.snapshot.queryParamMap.get('code');
    const state = this.route.snapshot.queryParamMap.get('state');
    const error = this.route.snapshot.queryParamMap.get('error');
    if (error) {
      this.errorMessage = error === 'access_denied'
        ? 'Você recusou a autorização.'
        : 'A autorização não pôde ser concluída.';
      return;
    }
    if (code) {
      void this.exchangeCode(code, state ?? '');
    }
  }

  async start(): Promise<void> {
    this.loading = true;
    this.errorMessage = '';
    const verifier = this.randomValue(64);
    const state = this.randomValue(32);
    const nonce = this.randomValue(32);
    const challenge = await this.sha256(verifier);
    sessionStorage.setItem('identity-hub.pkce-verifier', verifier);
    sessionStorage.setItem('identity-hub.oauth-state', state);
    sessionStorage.setItem('identity-hub.oidc-nonce', nonce);

    const callback = `${window.location.origin}/demo/callback`;
    const authorizationUrl = new URL('/oauth2/authorize', window.location.origin);
    authorizationUrl.searchParams.set('response_type', 'code');
    authorizationUrl.searchParams.set('client_id', this.clientId);
    authorizationUrl.searchParams.set('redirect_uri', callback);
    authorizationUrl.searchParams.set('scope', 'openid profile email demo.read');
    authorizationUrl.searchParams.set('state', state);
    authorizationUrl.searchParams.set('nonce', nonce);
    authorizationUrl.searchParams.set('code_challenge', challenge);
    authorizationUrl.searchParams.set('code_challenge_method', 'S256');
    window.location.assign(authorizationUrl.toString());
  }

  private async exchangeCode(code: string, returnedState: string): Promise<void> {
    const expectedState = sessionStorage.getItem('identity-hub.oauth-state');
    const verifier = sessionStorage.getItem('identity-hub.pkce-verifier');
    const expectedNonce = sessionStorage.getItem('identity-hub.oidc-nonce');
    sessionStorage.removeItem('identity-hub.oauth-state');
    sessionStorage.removeItem('identity-hub.pkce-verifier');
    sessionStorage.removeItem('identity-hub.oidc-nonce');
    window.history.replaceState({}, document.title, '/demo/callback');

    if (!expectedState || returnedState !== expectedState || !verifier || !expectedNonce) {
      this.errorMessage = 'O retorno OAuth não corresponde ao fluxo iniciado neste navegador.';
      return;
    }

    this.loading = true;
    const body = new HttpParams()
      .set('grant_type', 'authorization_code')
      .set('client_id', this.clientId)
      .set('redirect_uri', `${window.location.origin}/demo/callback`)
      .set('code', code)
      .set('code_verifier', verifier);
    const headers = new HttpHeaders({ 'Content-Type': 'application/x-www-form-urlencoded' });

    this.http.post<TokenResponse>('/oauth2/token', body.toString(), { headers }).subscribe({
      next: token => {
        const claims = token.id_token ? this.decodeJwt(token.id_token) : null;
        if (!claims || claims['nonce'] !== expectedNonce || !this.hasExpectedAudience(claims['aud'])) {
          this.loading = false;
          this.errorMessage = 'O ID token não corresponde ao fluxo iniciado neste navegador.';
          return;
        }
        if (!token.refresh_token) {
          this.loading = false;
          this.errorMessage = 'O servidor não devolveu o refresh token esperado.';
          return;
        }
        sessionStorage.setItem(this.refreshTokenKey, token.refresh_token);
        sessionStorage.setItem(this.idTokenKey, token.id_token!);
        this.updateExpiration(token.expires_in);
        this.loadProfile(token.access_token);
      },
      error: () => {
        this.loading = false;
        this.errorMessage = 'O código não pôde ser trocado por tokens.';
      },
    });
  }

  renewSession(): void {
    const currentRefreshToken = sessionStorage.getItem(this.refreshTokenKey);
    if (!currentRefreshToken) {
      this.errorMessage = 'Não há uma sessão renovável neste navegador.';
      return;
    }

    this.loading = true;
    this.errorMessage = '';
    const body = new HttpParams()
      .set('grant_type', 'refresh_token')
      .set('client_id', this.clientId)
      .set('refresh_token', currentRefreshToken);
    const headers = new HttpHeaders({ 'Content-Type': 'application/x-www-form-urlencoded' });

    this.http.post<TokenResponse>('/oauth2/token', body.toString(), { headers }).subscribe({
      next: token => {
        if (!token.refresh_token || token.refresh_token === currentRefreshToken) {
          this.loading = false;
          this.errorMessage = 'A sessão não foi rotacionada de forma segura.';
          return;
        }
        sessionStorage.setItem(this.refreshTokenKey, token.refresh_token);
        this.updateExpiration(token.expires_in);
        this.loadProfile(token.access_token);
      },
      error: () => {
        this.clearLocalSession();
        this.errorMessage = 'A sessão expirou, foi revogada ou houve reutilização do refresh token.';
      },
    });
  }

  revokeSession(): void {
    const refreshToken = sessionStorage.getItem(this.refreshTokenKey);
    if (!refreshToken) {
      this.clearLocalSession();
      return;
    }

    this.loading = true;
    this.errorMessage = '';
    const body = new HttpParams()
      .set('token', refreshToken)
      .set('token_type_hint', 'refresh_token')
      .set('client_id', this.clientId);
    const headers = new HttpHeaders({ 'Content-Type': 'application/x-www-form-urlencoded' });
    this.http.post('/oauth2/revoke', body.toString(), { headers, responseType: 'text' }).subscribe({
      next: () => this.clearLocalSession(),
      error: () => {
        this.loading = false;
        this.errorMessage = 'Não foi possível revogar a sessão no servidor.';
      },
    });
  }

  logoutSession(): void {
    const refreshToken = sessionStorage.getItem(this.refreshTokenKey);
    const idToken = sessionStorage.getItem(this.idTokenKey);
    if (!refreshToken || !idToken) {
      this.errorMessage = 'Não há uma sessão OIDC completa para encerrar.';
      return;
    }

    this.loading = true;
    this.errorMessage = '';
    this.successMessage = '';
    const logoutState = this.randomValue(32);
    const postLogoutRedirectUri = `${window.location.origin}/demo/logout`;
    const body = new HttpParams()
      .set('token', refreshToken)
      .set('token_type_hint', 'refresh_token')
      .set('client_id', this.clientId);
    const headers = new HttpHeaders({ 'Content-Type': 'application/x-www-form-urlencoded' });

    this.http.post('/oauth2/revoke', body.toString(), { headers, responseType: 'text' }).subscribe({
      next: () => {
        const logoutUrl = new URL('/connect/logout', window.location.origin);
        logoutUrl.searchParams.set('id_token_hint', idToken);
        logoutUrl.searchParams.set('post_logout_redirect_uri', postLogoutRedirectUri);
        logoutUrl.searchParams.set('state', logoutState);
        sessionStorage.setItem(this.logoutStateKey, logoutState);
        this.clearLocalSession(true);
        window.location.assign(logoutUrl.toString());
      },
      error: () => {
        this.loading = false;
        this.errorMessage = 'Não foi possível revogar os tokens; a sessão SSO não foi encerrada.';
      },
    });
  }

  hasRenewableSession(): boolean {
    return sessionStorage.getItem(this.refreshTokenKey) !== null;
  }

  private loadProfile(accessToken: string): void {
    this.http.get<Record<string, unknown>>('/userinfo', {
      headers: new HttpHeaders({ Authorization: `Bearer ${accessToken}` }),
    }).subscribe({
      next: profile => {
        this.profile = profile;
        this.loadProtectedResource(accessToken);
      },
      error: () => {
        this.loading = false;
        this.errorMessage = 'O token foi emitido, mas o UserInfo não pôde ser consultado.';
      },
    });
  }

  private loadProtectedResource(accessToken: string): void {
    this.http.get<DemoResourceResponse>('/api/v1/demo/resource', {
      headers: new HttpHeaders({ Authorization: `Bearer ${accessToken}` }),
    }).subscribe({
      next: resource => {
        this.loading = false;
        this.resource = resource;
      },
      error: () => {
        this.loading = false;
        this.errorMessage = 'O perfil foi carregado, mas a API protegida recusou o access token.';
      },
    });
  }

  private randomValue(bytes: number): string {
    const value = crypto.getRandomValues(new Uint8Array(bytes));
    return this.base64Url(value);
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

  private updateExpiration(expiresIn: number): void {
    this.sessionExpiresAt = new Date(Date.now() + expiresIn * 1000);
  }

  private handleLogoutReturn(): void {
    const expectedState = sessionStorage.getItem(this.logoutStateKey);
    const returnedState = this.route.snapshot.queryParamMap.get('state');
    this.clearLocalSession();
    window.history.replaceState({}, document.title, '/demo/logout');
    if (expectedState && returnedState === expectedState) {
      this.successMessage = 'Sessão OAuth e sessão do Identity Hub encerradas com sucesso.';
      return;
    }
    this.errorMessage = 'O retorno do logout não corresponde ao fluxo iniciado neste navegador.';
  }

  private clearLocalSession(preserveLogoutState = false): void {
    sessionStorage.removeItem(this.refreshTokenKey);
    sessionStorage.removeItem(this.idTokenKey);
    sessionStorage.removeItem('identity-hub.pkce-verifier');
    sessionStorage.removeItem('identity-hub.oauth-state');
    sessionStorage.removeItem('identity-hub.oidc-nonce');
    if (!preserveLogoutState) {
      sessionStorage.removeItem(this.logoutStateKey);
    }
    this.loading = false;
    this.profile = null;
    this.resource = null;
    this.sessionExpiresAt = null;
  }
}
