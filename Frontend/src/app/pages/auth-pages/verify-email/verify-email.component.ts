import { Component } from '@angular/core';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { switchMap } from 'rxjs';
import { RegistrationApiService } from '../../../core/services/registration-api.service';
import { AuthPageLayoutComponent } from '../../../shared/layout/auth-page-layout/auth-page-layout.component';

@Component({
  selector: 'app-verify-email',
  imports: [AuthPageLayoutComponent, RouterModule],
  templateUrl: './verify-email.component.html',
})
export class VerifyEmailComponent {
  state: 'verifying' | 'verified' | 'invalid' = 'verifying';

  constructor(
    route: ActivatedRoute,
    registrations: RegistrationApiService,
  ) {
    const fragment = route.snapshot.fragment ?? '';
    const token = new URLSearchParams(fragment).get('token');
    if (!token) {
      this.state = 'invalid';
      return;
    }
    registrations.prepareCsrf().pipe(
      switchMap(() => registrations.verify(token)),
    ).subscribe({
      next: () => this.state = 'verified',
      error: () => this.state = 'invalid',
    });
  }
}
