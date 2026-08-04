package com.karamba121.backend.features.scim;

public final class ScimResourceContract {

    public static final String AUDIENCE = "identity-hub-scim-api";
    public static final String READ_SCOPE = "scim.read";
    public static final String WRITE_SCOPE = "scim.write";
    public static final String USER_SCHEMA = "urn:ietf:params:scim:schemas:core:2.0:User";
    public static final String LIST_SCHEMA = "urn:ietf:params:scim:api:messages:2.0:ListResponse";
    public static final String PATCH_SCHEMA = "urn:ietf:params:scim:api:messages:2.0:PatchOp";
    public static final String ERROR_SCHEMA = "urn:ietf:params:scim:api:messages:2.0:Error";

    private ScimResourceContract() {
    }
}
