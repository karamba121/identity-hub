
import { Component } from '@angular/core';
import { LabelComponent } from '../../form/label/label.component';
import { InputFieldComponent } from '../../form/input/input-field.component';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { InteractionApiService } from '../../../../core/services/interaction-api.service';
import { PasskeyApiService } from '../../../../core/services/passkey-api.service';
import { FederationApiService, FederationProvider } from '../../../../core/services/federation-api.service';

@Component({
  selector: 'app-signin-form',
  imports: [
    LabelComponent,
    InputFieldComponent,
    RouterModule,
    FormsModule
],
  templateUrl: './signin-form.component.html',
  styles: ``
})
export class SigninFormComponent {
  showPassword = false;
  email = '';
  password = '';
  clientName = '';
  interactionId = '';
  errorMessage = '';
  loading = false;
  awaitingMfa = false;
  mfaCode = '';
  federationProviders: FederationProvider[] = [];

  constructor(
    private readonly route: ActivatedRoute,
    private readonly interactions: InteractionApiService,
    private readonly passkeys: PasskeyApiService,
    private readonly federation: FederationApiService,
  ) {
    this.interactionId = this.route.snapshot.queryParamMap.get('interaction_id') ?? '';
    this.federation.providers().subscribe({
      next: providers => this.federationProviders = providers,
    });
    if (this.route.snapshot.queryParamMap.get('federation_error')) {
      this.errorMessage = 'O provedor externo recusou o acesso ou não devolveu uma identidade válida.';
    }
    this.awaitingMfa = this.route.snapshot.queryParamMap.get('federated_mfa') === '1';
    if (!this.interactionId) {
      if (!this.errorMessage) this.errorMessage = 'Inicie o acesso pelo cliente demonstrativo.';
      return;
    }
    this.interactions.get(this.interactionId).subscribe({
      next: interaction => {
        if (interaction.type !== 'login') {
          this.errorMessage = 'Esta interação não é uma solicitação de login.';
          return;
        }
        this.clientName = interaction.clientName;
      },
      error: () => this.errorMessage = 'A solicitação expirou ou não pertence a esta sessão.',
    });
  }

  onFederated(provider: FederationProvider): void {
    if (!this.interactionId || this.loading) return;
    this.loading = true;
    window.location.assign(this.federation.loginUrl(this.interactionId, provider.id));
  }

  togglePasswordVisibility() {
    this.showPassword = !this.showPassword;
  }

  onSignIn() {
    if (!this.interactionId || !this.email || !this.password || this.loading) {
      return;
    }
    this.loading = true;
    this.errorMessage = '';
    this.interactions.login(this.interactionId, this.email, this.password).subscribe({
      next: result => {
        if (result.mfaRequired) {
          this.loading = false;
          this.awaitingMfa = true;
          this.password = '';
          return;
        }
        if (result.continueUrl) window.location.assign(result.continueUrl);
      },
      error: (error: HttpErrorResponse) => {
        this.loading = false;
        this.errorMessage = error.status === 429
          ? (error.error?.detail ?? 'Muitas tentativas. Aguarde antes de tentar novamente.')
          : error.status === 403
            ? 'A política de segurança exige MFA ou uma passkey para este acesso.'
          : error.status === 401
            ? 'E-mail ou senha inválidos.'
            : 'Não foi possível concluir o login. Reinicie a autorização.';
      },
    });
  }

  onVerifyMfa() {
    if (!this.interactionId || !this.mfaCode || this.loading) return;
    this.loading = true;
    this.errorMessage = '';
    this.interactions.verifyMfa(this.interactionId, this.mfaCode).subscribe({
      next: result => {
        if (result.continueUrl) window.location.assign(result.continueUrl);
      },
      error: (error: HttpErrorResponse) => {
        this.loading = false;
        this.errorMessage = error.status === 429
          ? (error.error?.detail ?? 'Muitas tentativas. Aguarde antes de tentar novamente.')
          : 'Código do autenticador ou de recuperação inválido.';
      },
    });
  }

  async onPasskey(): Promise<void> {
    if (!this.interactionId || this.loading) return;
    this.loading = true;
    this.errorMessage = '';
    try {
      const result = await this.passkeys.authenticate(this.interactionId);
      if (result.continueUrl) window.location.assign(result.continueUrl);
    } catch (error) {
      this.loading = false;
      this.errorMessage = error instanceof Error && error.message.includes('conexão segura')
        ? error.message
        : 'A passkey foi recusada, cancelada ou não pertence a uma conta ativa.';
    }
  }
}
