import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Component, OnInit } from '@angular/core';
import { ActivatedRoute } from '@angular/router';

interface TokenResponse {
  access_token: string;
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
  templateUrl: './oauth-demo.component.html',
})
export class OauthDemoComponent implements OnInit {
  loading = false;
  errorMessage = '';
  profile: Record<string, unknown> | null = null;
  resource: DemoResourceResponse | null = null;

  private readonly clientId = 'identity-hub-demo';

  constructor(
    private readonly route: ActivatedRoute,
    private readonly http: HttpClient,
  ) {}

  ngOnInit(): void {
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
        this.loadProfile(token.access_token);
      },
      error: () => {
        this.loading = false;
        this.errorMessage = 'O código não pôde ser trocado por tokens.';
      },
    });
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
}
