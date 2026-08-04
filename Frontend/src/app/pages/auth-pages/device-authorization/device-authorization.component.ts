import { HttpClient } from '@angular/common/http';
import { Component } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { AuthPageLayoutComponent } from '../../../shared/layout/auth-page-layout/auth-page-layout.component';

interface DeviceConsentView {
  clientId: string;
  clientName: string;
  scopes: string[];
}

@Component({
  selector: 'app-device-authorization',
  imports: [FormsModule, AuthPageLayoutComponent],
  templateUrl: './device-authorization.component.html',
})
export class DeviceAuthorizationComponent {
  userCode = '';
  clientId = '';
  clientName = '';
  state = '';
  scopes: string[] = [];
  status: 'entry' | 'consent' | 'approved' | 'error' = 'entry';
  errorMessage = '';
  loading = false;

  constructor(route: ActivatedRoute, http: HttpClient) {
    const params = route.snapshot.queryParamMap;
    const result = params.get('status');
    if (result === 'approved' || result === 'error') {
      this.status = result;
      return;
    }
    this.userCode = this.normalizeCode(params.get('user_code') ?? '');
    this.clientId = params.get('client_id') ?? '';
    this.state = params.get('state') ?? '';
    if (!this.clientId || !this.state || !this.userCode) {
      return;
    }
    this.loading = true;
    http.get<DeviceConsentView>('/api/v1/device-authorization/consent', {
      params: { client_id: this.clientId, user_code: this.userCode },
    }).subscribe({
      next: consent => {
        this.clientName = consent.clientName;
        this.scopes = consent.scopes;
        this.status = 'consent';
        this.loading = false;
      },
      error: () => {
        this.errorMessage = 'O código expirou ou não corresponde a este pedido.';
        this.loading = false;
      },
    });
  }

  verify(): void {
    const normalized = this.normalizeCode(this.userCode);
    if (!/^[BCDFGHJKLMNPQRSTVWXZ]{4}-[BCDFGHJKLMNPQRSTVWXZ]{4}$/.test(normalized)) {
      this.errorMessage = 'Informe o código de oito letras exibido no dispositivo.';
      return;
    }
    window.location.assign(`/oauth2/device_verification?user_code=${encodeURIComponent(normalized)}`);
  }

  decide(approved: boolean): void {
    if (this.loading || !this.clientId || !this.state || !this.userCode) {
      return;
    }
    this.loading = true;
    const form = document.createElement('form');
    form.method = 'post';
    form.action = '/oauth2/device_verification';
    this.field(form, 'client_id', this.clientId);
    this.field(form, 'state', this.state);
    this.field(form, 'user_code', this.userCode);
    if (approved) {
      this.scopes.forEach(scope => this.field(form, 'scope', scope));
    }
    document.body.appendChild(form);
    form.submit();
  }

  scopeLabel(scope: string): string {
    const labels: Record<string, string> = {
      openid: 'Confirmar sua identidade',
      profile: 'Ver seu nome e perfil básico',
      email: 'Ver seu endereço de e-mail',
      'demo.read': 'Acessar a API demonstrativa em seu nome',
    };
    return labels[scope] ?? scope;
  }

  private normalizeCode(value: string): string {
    const letters = value.toUpperCase().replace(/[^A-Z]/g, '').slice(0, 8);
    return letters.length > 4 ? `${letters.slice(0, 4)}-${letters.slice(4)}` : letters;
  }

  private field(form: HTMLFormElement, name: string, value: string): void {
    const input = document.createElement('input');
    input.type = 'hidden';
    input.name = name;
    input.value = value;
    form.appendChild(input);
  }
}
