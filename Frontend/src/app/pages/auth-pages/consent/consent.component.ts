import { Component } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { AuthPageLayoutComponent } from '../../../shared/layout/auth-page-layout/auth-page-layout.component';
import { InteractionApiService } from '../../../core/services/interaction-api.service';

@Component({
  selector: 'app-consent',
  imports: [AuthPageLayoutComponent],
  templateUrl: './consent.component.html',
})
export class ConsentComponent {
  interactionId = '';
  clientName = '';
  scopes: string[] = [];
  errorMessage = '';
  loading = false;

  constructor(
    route: ActivatedRoute,
    private readonly interactions: InteractionApiService,
  ) {
    this.interactionId = route.snapshot.queryParamMap.get('interaction_id') ?? '';
    if (!this.interactionId) {
      this.errorMessage = 'Solicitação de consentimento ausente.';
      return;
    }
    this.interactions.get(this.interactionId).subscribe({
      next: interaction => {
        if (interaction.type !== 'consent') {
          this.errorMessage = 'Esta interação não é uma solicitação de consentimento.';
          return;
        }
        this.clientName = interaction.clientName;
        this.scopes = interaction.scopes;
      },
      error: () => this.errorMessage = 'A solicitação expirou ou não pertence a esta sessão.',
    });
  }

  decide(approved: boolean): void {
    if (!this.interactionId || this.loading) {
      return;
    }
    this.loading = true;
    this.errorMessage = '';
    this.interactions.consent(this.interactionId, approved).subscribe({
      next: result => window.location.assign(result.continueUrl),
      error: () => {
        this.loading = false;
        this.errorMessage = 'Não foi possível registrar sua decisão. Reinicie a autorização.';
      },
    });
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
}
