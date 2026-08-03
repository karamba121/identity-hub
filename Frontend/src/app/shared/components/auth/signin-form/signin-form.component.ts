
import { Component } from '@angular/core';
import { LabelComponent } from '../../form/label/label.component';
import { InputFieldComponent } from '../../form/input/input-field.component';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { InteractionApiService } from '../../../../core/services/interaction-api.service';

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

  constructor(
    private readonly route: ActivatedRoute,
    private readonly interactions: InteractionApiService,
  ) {
    this.interactionId = this.route.snapshot.queryParamMap.get('interaction_id') ?? '';
    if (!this.interactionId) {
      this.errorMessage = 'Inicie o acesso pelo cliente demonstrativo.';
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
      next: result => window.location.assign(result.continueUrl),
      error: (error: HttpErrorResponse) => {
        this.loading = false;
        this.errorMessage = error.status === 429
          ? (error.error?.detail ?? 'Muitas tentativas. Aguarde antes de tentar novamente.')
          : error.status === 401
            ? 'E-mail ou senha inválidos.'
            : 'Não foi possível concluir o login. Reinicie a autorização.';
      },
    });
  }
}
