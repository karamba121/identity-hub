import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { AdminOAuthSessionService } from '../../../core/services/admin-oauth-session.service';
import {
  AdminTenantContext,
  OAuthClientAdminApiService,
  OAuthClientCommand,
  OAuthClientView,
  SecurityAuditEventView,
} from '../../../core/services/oauth-client-admin-api.service';
import { PageBreadcrumbComponent } from '../../../shared/components/common/page-breadcrumb/page-breadcrumb.component';

interface ClientForm {
  clientId: string;
  clientName: string;
  redirectUris: string;
  postLogoutRedirectUris: string;
  clientType: 'PUBLIC' | 'CONFIDENTIAL' | 'DEVICE';
}

@Component({
  selector: 'app-oauth-clients-admin',
  imports: [DatePipe, FormsModule, PageBreadcrumbComponent],
  templateUrl: './oauth-clients-admin.component.html',
})
export class OAuthClientsAdminComponent implements OnInit {
  loading = true;
  saving = false;
  authenticated = false;
  errorMessage = '';
  successMessage = '';
  tenants: AdminTenantContext[] = [];
  clients: OAuthClientView[] = [];
  auditEvents: SecurityAuditEventView[] = [];
  auditTotal = 0;
  selectedTenantId = '';
  editingClientId: string | null = null;
  selectedScopes = new Set<string>(['openid', 'profile']);
  createdSecret = '';
  form: ClientForm = this.emptyForm();

  constructor(
    private readonly route: ActivatedRoute,
    private readonly session: AdminOAuthSessionService,
    private readonly api: OAuthClientAdminApiService,
  ) {}

  ngOnInit(): void {
    void this.initialize();
  }

  async signIn(): Promise<void> {
    this.errorMessage = '';
    await this.session.startAuthorization();
  }

  async signOut(): Promise<void> {
    this.loading = true;
    try {
      await this.session.end();
    } catch {
      this.session.clear();
    }
    this.authenticated = false;
    this.tenants = [];
    this.clients = [];
    this.auditEvents = [];
    this.auditTotal = 0;
    this.selectedTenantId = '';
    this.loading = false;
  }

  async tenantChanged(): Promise<void> {
    this.resetForm();
    this.loading = true;
    await Promise.all([this.loadClients(false), this.loadAudit(false)]);
    this.loading = false;
  }

  edit(client: OAuthClientView): void {
    this.createdSecret = '';
    this.editingClientId = client.clientId;
    this.form = {
      clientId: client.clientId,
      clientName: client.clientName,
      redirectUris: client.redirectUris.join('\n'),
      postLogoutRedirectUris: client.postLogoutRedirectUris.join('\n'),
      clientType: client.clientType,
    };
    this.selectedScopes = new Set(client.scopes);
    this.successMessage = '';
    this.errorMessage = '';
  }

  resetForm(): void {
    this.editingClientId = null;
    this.form = this.emptyForm();
    this.selectedScopes = new Set(['openid', 'profile']);
    this.createdSecret = '';
    this.errorMessage = '';
  }

  toggleScope(scope: string, event: Event): void {
    const checked = (event.target as HTMLInputElement).checked;
    const updated = new Set(this.selectedScopes);
    checked ? updated.add(scope) : updated.delete(scope);
    this.selectedScopes = updated;
  }

  clientTypeChanged(): void {
    this.form.redirectUris = '';
    this.form.postLogoutRedirectUris = '';
    this.selectedScopes = this.form.clientType === 'CONFIDENTIAL'
      ? new Set(['scim.read'])
      : this.form.clientType === 'DEVICE'
        ? new Set(['profile', 'demo.read'])
        : new Set(['openid', 'profile']);
    this.createdSecret = '';
  }

  async save(): Promise<void> {
    if (!this.selectedTenantId || !this.canManage) {
      return;
    }
    this.saving = true;
    this.errorMessage = '';
    this.successMessage = '';
    const command: OAuthClientCommand = {
      clientName: this.form.clientName,
      redirectUris: this.lines(this.form.redirectUris),
      postLogoutRedirectUris: this.lines(this.form.postLogoutRedirectUris),
      scopes: [...this.selectedScopes],
      clientType: this.form.clientType,
    };
    try {
      if (this.editingClientId) {
        await this.api.update(this.selectedTenantId, this.editingClientId, command);
        this.successMessage = 'Cliente OAuth atualizado com sucesso.';
      } else {
        const created = await this.api.create(this.selectedTenantId, { ...command, clientId: this.form.clientId });
        this.createdSecret = created.clientSecret ?? '';
        this.successMessage = 'Cliente OAuth criado com sucesso.';
      }
      this.resetFormPreservingMessage();
      await Promise.all([this.loadClients(false), this.loadAudit(false)]);
    } catch (error) {
      this.errorMessage = this.describeError(error, 'Não foi possível salvar o cliente OAuth.');
    } finally {
      this.saving = false;
    }
  }

  async remove(client: OAuthClientView): Promise<void> {
    if (!this.selectedTenantId || !this.canManage
        || !window.confirm(`Remover o cliente ${client.clientName}? Autorizações e sessões renováveis serão revogadas.`)) {
      return;
    }
    this.loading = true;
    this.errorMessage = '';
    try {
      await this.api.remove(this.selectedTenantId, client.clientId);
      this.successMessage = 'Cliente OAuth removido e sessões renováveis revogadas.';
      this.resetFormPreservingMessage();
      await Promise.all([this.loadClients(false), this.loadAudit(false)]);
    } catch (error) {
      this.errorMessage = this.describeError(error, 'Não foi possível remover o cliente OAuth.');
    } finally {
      this.loading = false;
    }
  }

  async rotateSecret(client: OAuthClientView): Promise<void> {
    if (!this.selectedTenantId || !this.canManage || client.clientType !== 'CONFIDENTIAL') {
      return;
    }
    const answer = window.prompt(
      'Por quantos minutos o secret anterior deve continuar válido? Informe de 0 a 1440.',
      '15',
    );
    if (answer === null) {
      return;
    }
    const validityMinutes = Number(answer);
    if (!Number.isInteger(validityMinutes) || validityMinutes < 0 || validityMinutes > 1440) {
      this.errorMessage = 'A janela do secret anterior deve ser um número inteiro entre 0 e 1440 minutos.';
      return;
    }
    if (!window.confirm(
      `Rotacionar o secret de ${client.clientName}? O valor atual continuará válido por ${validityMinutes} minuto(s).`,
    )) {
      return;
    }
    this.loading = true;
    this.errorMessage = '';
    this.successMessage = '';
    this.createdSecret = '';
    try {
      const rotated = await this.api.rotateSecret(this.selectedTenantId, client.clientId, validityMinutes);
      this.createdSecret = rotated.clientSecret ?? '';
      this.successMessage = 'Client secret rotacionado. Copie o novo valor antes de continuar.';
      await Promise.all([this.loadClients(false), this.loadAudit(false)]);
    } catch (error) {
      this.errorMessage = this.describeError(error, 'Não foi possível rotacionar o client secret.');
    } finally {
      this.loading = false;
    }
  }

  previousSecretWindowActive(client: OAuthClientView): boolean {
    return !!client.previousSecretExpiresAt
      && new Date(client.previousSecretExpiresAt).getTime() > Date.now();
  }

  get selectedTenant(): AdminTenantContext | undefined {
    return this.tenants.find(tenant => tenant.tenantId === this.selectedTenantId);
  }

  get canRead(): boolean {
    return this.selectedTenant?.permissions.includes('oauth.clients.read') ?? false;
  }

  get canManage(): boolean {
    return this.selectedTenant?.permissions.includes('oauth.clients.manage') ?? false;
  }

  get canReadAudit(): boolean {
    return this.selectedTenant?.permissions.includes('security.audit.read') ?? false;
  }

  get supportedScopes(): string[] {
    return this.form.clientType === 'CONFIDENTIAL'
      ? ['demo.read', 'scim.read', 'scim.write']
      : this.form.clientType === 'DEVICE'
        ? ['openid', 'profile', 'email', 'demo.read']
        : ['openid', 'profile', 'email', 'demo.read', 'identity.admin'];
  }

  private async initialize(): Promise<void> {
    const oauthError = this.route.snapshot.queryParamMap.get('error');
    if (oauthError) {
      this.loading = false;
      this.errorMessage = oauthError === 'access_denied'
        ? 'O consentimento administrativo foi recusado.'
        : 'A autorização administrativa não pôde ser concluída.';
      window.history.replaceState({}, document.title, '/admin/oauth-clients');
      return;
    }
    try {
      const code = this.route.snapshot.queryParamMap.get('code');
      if (code) {
        await this.session.completeAuthorization(
          code,
          this.route.snapshot.queryParamMap.get('state') ?? '',
        );
        window.history.replaceState({}, document.title, '/admin/oauth-clients');
      } else if (!(await this.session.restore())) {
        this.loading = false;
        return;
      }
      this.authenticated = true;
      await this.loadContext();
    } catch (error) {
      this.session.clear();
      this.authenticated = false;
      this.errorMessage = this.describeError(error, 'Não foi possível iniciar a área administrativa.');
      this.loading = false;
    }
  }

  private async loadContext(): Promise<void> {
    this.loading = true;
    this.tenants = await this.api.context();
    const readableTenant = this.tenants.find(tenant => tenant.permissions.includes('oauth.clients.read'));
    this.selectedTenantId = readableTenant?.tenantId ?? this.tenants[0]?.tenantId ?? '';
    await Promise.all([this.loadClients(false), this.loadAudit(false)]);
    this.loading = false;
  }

  async loadClients(showLoading = true): Promise<void> {
    if (showLoading) {
      this.loading = true;
    }
    this.clients = [];
    if (!this.selectedTenantId || !this.canRead) {
      this.loading = false;
      return;
    }
    try {
      this.clients = await this.api.list(this.selectedTenantId);
    } catch (error) {
      this.errorMessage = this.describeError(error, 'Não foi possível carregar os clientes OAuth.');
    } finally {
      this.loading = false;
    }
  }

  async loadAudit(showLoading = true): Promise<void> {
    if (showLoading) {
      this.loading = true;
    }
    this.auditEvents = [];
    this.auditTotal = 0;
    if (!this.selectedTenantId || !this.canReadAudit) {
      if (showLoading) {
        this.loading = false;
      }
      return;
    }
    try {
      const audit = await this.api.audit(this.selectedTenantId);
      this.auditEvents = audit.items;
      this.auditTotal = audit.totalElements;
    } catch (error) {
      this.errorMessage = this.describeError(error, 'Não foi possível carregar a auditoria de segurança.');
    } finally {
      if (showLoading) {
        this.loading = false;
      }
    }
  }

  auditEventLabel(eventType: string): string {
    const labels: Record<string, string> = {
      OAUTH_CLIENT_CREATED: 'Cliente OAuth criado',
      OAUTH_CLIENT_UPDATED: 'Cliente OAuth atualizado',
      OAUTH_CLIENT_DELETED: 'Cliente OAuth removido',
      OAUTH_CLIENT_SECRET_ROTATED: 'Client secret rotacionado',
      TENANT_MEMBERSHIP_ROLE_ASSIGNED: 'Papel de membership atribuído',
      TENANT_MEMBERSHIP_SUSPENDED: 'Membership suspensa',
      TENANT_MEMBERSHIP_REMOVED: 'Membership removida',
      SCIM_USER_CREATED: 'Usuário provisionado via SCIM',
      SCIM_USER_UPDATED: 'Usuário SCIM atualizado',
      SCIM_USER_DELETED: 'Usuário SCIM removido',
    };
    return labels[eventType] ?? eventType;
  }

  auditResultLabel(result: SecurityAuditEventView['result']): string {
    return { SUCCEEDED: 'Concluída', DENIED: 'Negada', FAILED: 'Falhou' }[result];
  }

  private lines(value: string): string[] {
    return [...new Set(value.split(/\r?\n/).map(item => item.trim()).filter(Boolean))];
  }

  private emptyForm(): ClientForm {
    return {
      clientId: '',
      clientName: '',
      redirectUris: '',
      postLogoutRedirectUris: '',
      clientType: 'PUBLIC',
    };
  }

  private resetFormPreservingMessage(): void {
    this.editingClientId = null;
    this.form = this.emptyForm();
    this.selectedScopes = new Set(['openid', 'profile']);
  }

  private describeError(error: unknown, fallback: string): string {
    if (error instanceof HttpErrorResponse) {
      if (error.status === 401) {
        this.session.clear();
        this.authenticated = false;
        return 'A sessão administrativa expirou. Entre novamente.';
      }
      if (typeof error.error?.detail === 'string') {
        return error.error.detail;
      }
    }
    return error instanceof Error && error.message ? error.message : fallback;
  }
}
