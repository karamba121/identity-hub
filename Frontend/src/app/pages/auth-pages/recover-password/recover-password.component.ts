import { HttpErrorResponse } from '@angular/common/http';
import { Component } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { switchMap } from 'rxjs';
import { PasswordRecoveryApiService } from '../../../core/services/password-recovery-api.service';
import { InputFieldComponent } from '../../../shared/components/form/input/input-field.component';
import { LabelComponent } from '../../../shared/components/form/label/label.component';
import { AuthPageLayoutComponent } from '../../../shared/layout/auth-page-layout/auth-page-layout.component';

@Component({
  selector: 'app-recover-password',
  imports: [AuthPageLayoutComponent, RouterModule, FormsModule, LabelComponent, InputFieldComponent],
  templateUrl: './recover-password.component.html',
})
export class RecoverPasswordComponent {
  readonly token: string | null;
  newPassword = '';
  confirmation = '';
  loading = false;
  completed = false;
  errorMessage = '';

  constructor(route: ActivatedRoute, private readonly recovery: PasswordRecoveryApiService) {
    this.token = new URLSearchParams(route.snapshot.fragment ?? '').get('token');
  }

  onSubmit() {
    if (!this.token || this.newPassword.length < 15 || this.newPassword !== this.confirmation || this.loading) {
      this.errorMessage = this.newPassword !== this.confirmation
        ? 'As senhas informadas não coincidem.'
        : 'A nova senha deve conter pelo menos 15 caracteres.';
      return;
    }
    this.loading = true;
    this.errorMessage = '';
    this.recovery.prepareCsrf().pipe(
      switchMap(() => this.recovery.complete(this.token!, this.newPassword)),
    ).subscribe({
      next: () => {
        this.loading = false;
        this.completed = true;
        this.newPassword = '';
        this.confirmation = '';
      },
      error: (error: HttpErrorResponse) => {
        this.loading = false;
        this.errorMessage = error.status === 429
          ? (error.error?.detail ?? 'Muitas tentativas. Aguarde antes de tentar novamente.')
          : error.status === 400
            ? (error.error?.detail ?? 'O link é inválido ou expirou.')
            : 'Não foi possível redefinir a senha. Tente novamente.';
      },
    });
  }
}
