import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { firstValueFrom } from 'rxjs';

export interface PushedAuthorizationParameters {
  responseType: string;
  clientId: string;
  redirectUri: string;
  scope: string;
  state: string;
  nonce: string;
  codeChallenge: string;
}

interface PushedAuthorizationResponse {
  request_uri: string;
  expires_in: number;
}

@Injectable({ providedIn: 'root' })
export class PushedAuthorizationRequestService {
  constructor(private readonly http: HttpClient) {}

  async create(parameters: PushedAuthorizationParameters): Promise<string> {
    const response = await firstValueFrom(this.http.post<PushedAuthorizationResponse>(
      '/oauth2/par',
      new HttpParams()
        .set('response_type', parameters.responseType)
        .set('client_id', parameters.clientId)
        .set('redirect_uri', parameters.redirectUri)
        .set('scope', parameters.scope)
        .set('state', parameters.state)
        .set('nonce', parameters.nonce)
        .set('code_challenge', parameters.codeChallenge)
        .set('code_challenge_method', 'S256')
        .toString(),
      { headers: new HttpHeaders({ 'Content-Type': 'application/x-www-form-urlencoded' }) },
    ));
    if (!response.request_uri || response.expires_in <= 0) {
      throw new Error('O servidor devolveu uma referência PAR inválida.');
    }
    return response.request_uri;
  }

  authorizationUrl(clientId: string, requestUri: string): string {
    const authorizationUrl = new URL('/oauth2/authorize', window.location.origin);
    authorizationUrl.searchParams.set('client_id', clientId);
    authorizationUrl.searchParams.set('request_uri', requestUri);
    return authorizationUrl.toString();
  }
}
