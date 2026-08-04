import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { firstValueFrom, Observable, switchMap } from 'rxjs';
import { LoginResult } from './interaction-api.service';

export interface PasskeyView {
  id: string;
  label: string;
  createdAt: string;
  lastUsedAt: string;
  backupEligible: boolean;
  backedUp: boolean;
}

@Injectable({ providedIn: 'root' })
export class PasskeyApiService {
  constructor(private readonly http: HttpClient) {}

  list(): Observable<PasskeyView[]> {
    return this.http.get<PasskeyView[]>('/api/v1/passkeys');
  }

  remove(id: string): Observable<void> {
    return this.http.get<void>('/api/v1/passkeys/csrf').pipe(
      switchMap(() => this.http.delete<void>(`/api/v1/passkeys/${encodeURIComponent(id)}`)),
    );
  }

  async register(label: string): Promise<void> {
    this.requireWebAuthn();
    await firstValueFrom(this.http.get<void>('/api/v1/passkeys/csrf'));
    const options = await firstValueFrom(this.http.post<Record<string, unknown>>(
      '/webauthn/register/options', {},
    ));
    const credential = await navigator.credentials.create({
      publicKey: this.creationOptions(options),
    });
    if (!(credential instanceof PublicKeyCredential)
        || !(credential.response instanceof AuthenticatorAttestationResponse)) {
      throw new Error('O autenticador não criou uma passkey válida.');
    }
    await firstValueFrom(this.http.post('/webauthn/register', {
      publicKey: {
        credential: this.registrationCredential(credential),
        label: label.trim() || 'Minha passkey',
      },
    }));
  }

  async authenticate(interactionId: string): Promise<LoginResult> {
    this.requireWebAuthn();
    const options = await firstValueFrom(this.http.post<Record<string, unknown>>(
      '/webauthn/authenticate/options', {},
    ));
    const credential = await navigator.credentials.get({
      publicKey: this.requestOptions(options),
    });
    if (!(credential instanceof PublicKeyCredential)
        || !(credential.response instanceof AuthenticatorAssertionResponse)) {
      throw new Error('O autenticador não devolveu uma passkey válida.');
    }
    await firstValueFrom(this.http.post('/login/webauthn', this.authenticationCredential(credential)));
    return firstValueFrom(this.http.post<LoginResult>(
      `/api/v1/interactions/${encodeURIComponent(interactionId)}/passkey`, {},
    ));
  }

  private creationOptions(json: Record<string, unknown>): PublicKeyCredentialCreationOptions {
    const options = structuredClone(json) as Record<string, any>;
    options['challenge'] = this.decode(options['challenge']);
    options['user']['id'] = this.decode(options['user']['id']);
    options['excludeCredentials'] = (options['excludeCredentials'] ?? []).map(
      (credential: Record<string, any>) => ({ ...credential, id: this.decode(credential['id']) }),
    );
    return options as PublicKeyCredentialCreationOptions;
  }

  private requestOptions(json: Record<string, unknown>): PublicKeyCredentialRequestOptions {
    const options = structuredClone(json) as Record<string, any>;
    options['challenge'] = this.decode(options['challenge']);
    options['allowCredentials'] = (options['allowCredentials'] ?? []).map(
      (credential: Record<string, any>) => ({ ...credential, id: this.decode(credential['id']) }),
    );
    return options as PublicKeyCredentialRequestOptions;
  }

  private registrationCredential(credential: PublicKeyCredential): Record<string, unknown> {
    const response = credential.response as AuthenticatorAttestationResponse;
    return {
      id: credential.id,
      rawId: this.encode(credential.rawId),
      type: credential.type,
      response: {
        clientDataJSON: this.encode(response.clientDataJSON),
        attestationObject: this.encode(response.attestationObject),
        transports: response.getTransports?.() ?? [],
      },
      clientExtensionResults: credential.getClientExtensionResults(),
      authenticatorAttachment: credential.authenticatorAttachment,
    };
  }

  private authenticationCredential(credential: PublicKeyCredential): Record<string, unknown> {
    const response = credential.response as AuthenticatorAssertionResponse;
    return {
      id: credential.id,
      rawId: this.encode(credential.rawId),
      type: credential.type,
      response: {
        clientDataJSON: this.encode(response.clientDataJSON),
        authenticatorData: this.encode(response.authenticatorData),
        signature: this.encode(response.signature),
        userHandle: response.userHandle ? this.encode(response.userHandle) : null,
      },
      clientExtensionResults: credential.getClientExtensionResults(),
      authenticatorAttachment: credential.authenticatorAttachment,
    };
  }

  private requireWebAuthn(): void {
    if (!window.isSecureContext || !('PublicKeyCredential' in window) || !navigator.credentials) {
      throw new Error('Passkeys exigem navegador compatível e uma conexão segura.');
    }
  }

  private decode(value: string): ArrayBuffer {
    const normalized = value.replace(/-/g, '+').replace(/_/g, '/');
    const binary = atob(normalized.padEnd(Math.ceil(normalized.length / 4) * 4, '='));
    return Uint8Array.from(binary, character => character.charCodeAt(0)).buffer;
  }

  private encode(value: ArrayBuffer): string {
    const bytes = new Uint8Array(value);
    let binary = '';
    bytes.forEach(byte => binary += String.fromCharCode(byte));
    return btoa(binary).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  }
}
