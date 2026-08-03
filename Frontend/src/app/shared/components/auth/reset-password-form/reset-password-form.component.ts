import { Component } from '@angular/core';
import { LabelComponent } from '../../form/label/label.component';
import { InputFieldComponent } from '../../form/input/input-field.component';
import { RouterModule } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { switchMap } from 'rxjs';
import { PasswordRecoveryApiService } from '../../../../core/services/password-recovery-api.service';

@Component({
  selector: 'app-reset-password-form',
  imports: [
    LabelComponent,
    InputFieldComponent,
    RouterModule,
    FormsModule,
  ],
  templateUrl: './reset-password-form.component.html',
  styles: ``
})
export class ResetPasswordFormComponent {
  email = '';
  loading = false;
  errorMessage = '';
  successMessage = '';

  constructor(private readonly recovery: PasswordRecoveryApiService) {}

  onSubmit() {
    if (!this.email.trim() || this.loading) {
      return;
    }
    this.loading = true;
    this.errorMessage = '';
    this.successMessage = '';
    this.recovery.prepareCsrf().pipe(
      switchMap(() => this.recovery.request(this.email)),
    ).subscribe({
      next: result => {
        this.loading = false;
        this.successMessage = result.message;
      },
      error: (error: HttpErrorResponse) => {
        this.loading = false;
        this.errorMessage = error.status === 429
          ? (error.error?.detail ?? 'Muitas tentativas. Aguarde antes de tentar novamente.')
          : error.status === 400
            ? (error.error?.detail ?? 'Informe um e-mail válido.')
            : 'Não foi possível iniciar a recuperação. Tente novamente.';
      },
    });
  }
}
