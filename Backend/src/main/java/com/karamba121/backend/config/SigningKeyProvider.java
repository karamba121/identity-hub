package com.karamba121.backend.config;

import com.nimbusds.jose.jwk.RSAKey;

public interface SigningKeyProvider {

    RSAKey load();
}
