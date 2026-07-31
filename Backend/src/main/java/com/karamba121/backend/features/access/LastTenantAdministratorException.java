package com.karamba121.backend.features.access;

public class LastTenantAdministratorException extends IllegalStateException {

    public LastTenantAdministratorException() {
        super("O último administrador válido do tenant não pode ser removido ou rebaixado");
    }
}
