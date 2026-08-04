import { CommonModule } from '@angular/common';
import { Component } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { switchMap } from 'rxjs';
import { MfaApiService, MfaAuditEvent, MfaEnrollment, MfaStatus } from '../../core/services/mfa-api.service';
import { PageBreadcrumbComponent } from '../../shared/components/common/page-breadcrumb/page-breadcrumb.component';
import { PasskeyApiService, PasskeyView } from '../../core/services/passkey-api.service';

@Component({
  selector: 'app-profile',
  imports: [
    CommonModule,
    PageBreadcrumbComponent,
    FormsModule,
  ],
  templateUrl: './profile.component.html',
  styles: ``
})
export class ProfileComponent {
  status: MfaStatus | null = null;
  enrollment: MfaEnrollment | null = null;
  recoveryCodes: string[] = [];
  code = '';
  loading = false;
  errorMessage = '';
  successMessage = '';
  auditEvents: MfaAuditEvent[] = [];
  passkeys: PasskeyView[] = [];
  passkeyLabel = '';

  constructor(
    private readonly mfa: MfaApiService,
    private readonly passkeyApi: PasskeyApiService,
  ) {
    this.load();
    this.loadAudit();
    this.loadPasskeys();
  }

  loadAudit() {
    this.mfa.auditEvents().subscribe({
      next: page => this.auditEvents = page.items,
    });
  }

  load() {
    this.mfa.status().subscribe({
      next: status => this.status = status,
      error: () => this.errorMessage = 'Autentique-se no Identity Hub para gerenciar a segurança da conta.',
    });
  }

  loadPasskeys() {
    this.passkeyApi.list().subscribe({
      next: passkeys => this.passkeys = passkeys,
      error: () => this.errorMessage = 'Autentique-se no Identity Hub para gerenciar as passkeys.',
    });
  }

  async registerPasskey(): Promise<void> {
    if (this.loading || this.passkeyLabel.trim().length > 80) return;
    this.loading = true;
    this.errorMessage = '';
    this.successMessage = '';
    try {
      await this.passkeyApi.register(this.passkeyLabel);
      this.loading = false;
      this.passkeyLabel = '';
      this.successMessage = 'Passkey cadastrada. Ela já pode ser usada no próximo login.';
      this.loadPasskeys();
      this.loadAudit();
    } catch (error) {
      this.loading = false;
      this.errorMessage = error instanceof Error && error.message.includes('conexão segura')
        ? error.message
        : 'O cadastro da passkey foi cancelado ou recusado pelo autenticador.';
    }
  }

  removePasskey(passkey: PasskeyView): void {
    if (this.loading || !window.confirm(`Remover a passkey “${passkey.label}”?`)) return;
    this.run(() => this.passkeyApi.remove(passkey.id), () => {
      this.successMessage = 'Passkey removida.';
      this.loadPasskeys();
      this.loadAudit();
    });
  }

  startEnrollment() {
    this.run(() => this.mfa.prepareCsrf().pipe(switchMap(() => this.mfa.enroll())), enrollment => {
      this.enrollment = enrollment;
      this.recoveryCodes = [];
      this.successMessage = 'Adicione a chave ao autenticador e confirme o código atual.';
      this.loadAudit();
    });
  }

  confirmEnrollment() {
    this.run(() => this.mfa.prepareCsrf().pipe(switchMap(() => this.mfa.confirm(this.code))), result => {
      this.recoveryCodes = result.recoveryCodes;
      this.enrollment = null;
      this.code = '';
      this.successMessage = 'MFA habilitado. Guarde os códigos de recuperação em local seguro.';
      this.status = { enabled: true, recoveryCodesRemaining: result.recoveryCodes.length };
      this.loadAudit();
    });
  }

  regenerate() {
    this.run(() => this.mfa.prepareCsrf().pipe(switchMap(() => this.mfa.regenerate(this.code))), result => {
      this.recoveryCodes = result.recoveryCodes;
      this.code = '';
      this.successMessage = 'Novos códigos gerados; os anteriores foram invalidados.';
      this.load();
      this.loadAudit();
    });
  }

  disable() {
    this.run(() => this.mfa.prepareCsrf().pipe(switchMap(() => this.mfa.disable(this.code))), () => {
      this.status = { enabled: false, recoveryCodesRemaining: 0 };
      this.code = '';
      this.recoveryCodes = [];
      this.successMessage = 'MFA desabilitado. As sessões anteriores foram encerradas.';
      this.loadAudit();
    });
  }

  private run<T>(operation: () => import('rxjs').Observable<T>, success: (value: T) => void) {
    if (this.loading) return;
    this.loading = true;
    this.errorMessage = '';
    this.successMessage = '';
    operation().subscribe({
      next: value => {
        this.loading = false;
        success(value);
      },
      error: (error: HttpErrorResponse) => {
        this.loading = false;
        this.errorMessage = error.error?.detail ?? 'Não foi possível concluir a operação de MFA.';
      },
    });
  }

  eventLabel(eventType: string): string {
    const labels: Record<string, string> = {
      MFA_ENROLLMENT_STARTED: 'Configuração iniciada',
      MFA_ENABLED: 'MFA habilitado',
      MFA_RECOVERY_CODES_REGENERATED: 'Códigos regenerados',
      MFA_DISABLED: 'MFA desabilitado',
      MFA_CHALLENGE_SUCCEEDED: 'Desafio aceito',
      MFA_CHALLENGE_FAILED: 'Desafio recusado',
      PASSKEY_REGISTERED: 'Passkey cadastrada',
      PASSKEY_REMOVED: 'Passkey removida',
      PASSKEY_AUTHENTICATION_SUCCEEDED: 'Login com passkey',
    };
    return labels[eventType] ?? eventType;
  }
}
