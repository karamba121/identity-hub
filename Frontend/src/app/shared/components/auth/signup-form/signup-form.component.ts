
import { Component } from '@angular/core';
import { LabelComponent } from '../../form/label/label.component';
import { InputFieldComponent } from '../../form/input/input-field.component';
import { RouterModule } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { switchMap } from 'rxjs';
import { RegistrationApiService } from '../../../../core/services/registration-api.service';

@Component({
  selector: 'app-signup-form',
  imports: [
    LabelComponent,
    InputFieldComponent,
    RouterModule,
    FormsModule
],
  templateUrl: './signup-form.component.html',
  styles: ``
})
export class SignupFormComponent {
  showPassword = false;
  displayName = '';
  email = '';
  password = '';
  loading = false;
  errorMessage = '';
  successMessage = '';

  constructor(private readonly registrations: RegistrationApiService) {}

  togglePasswordVisibility() {
    this.showPassword = !this.showPassword;
  }

  onSubmit() {
    if (!this.displayName.trim() || !this.email.trim() || this.password.length < 15 || this.loading) {
      return;
    }
    this.loading = true;
    this.errorMessage = '';
    this.successMessage = '';
    this.registrations.prepareCsrf().pipe(
      switchMap(() => this.registrations.register({
        displayName: this.displayName,
        email: this.email,
        password: this.password,
      })),
    ).subscribe({
      next: result => {
        this.loading = false;
        this.password = '';
        this.successMessage = result.message;
      },
      error: (error: HttpErrorResponse) => {
        this.loading = false;
        this.errorMessage = error.status === 400
          ? (error.error?.detail ?? 'Revise os dados informados.')
          : 'Não foi possível iniciar o cadastro. Tente novamente.';
      },
    });
  }
}
