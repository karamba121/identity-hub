package com.karamba121.backend.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.Signature;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import com.nimbusds.jose.jwk.RSAKey;

final class PemSigningKeyProvider implements SigningKeyProvider {

    private static final int MAX_PEM_BYTES = 32 * 1024;

    private final SigningKeyProperties properties;
    private final ResourceLoader resources;

    PemSigningKeyProvider(SigningKeyProperties properties, ResourceLoader resources) {
        this.properties = properties;
        this.resources = resources;
    }

    @Override
    public RSAKey load() {
        String privateLocation = required(properties.privateKeyLocation(), "private-key-location");
        String publicLocation = required(properties.publicKeyLocation(), "public-key-location");
        try {
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            RSAPrivateKey privateKey = (RSAPrivateKey) keyFactory.generatePrivate(new PKCS8EncodedKeySpec(
                    pem(privateLocation, "PRIVATE KEY")));
            RSAPublicKey publicKey = (RSAPublicKey) keyFactory.generatePublic(new X509EncodedKeySpec(
                    pem(publicLocation, "PUBLIC KEY")));
            validatePair(privateKey, publicKey);
            RSAKey publicJwk = new RSAKey.Builder(publicKey).build();
            String keyId = properties.keyId() == null
                    ? publicJwk.computeThumbprint().toString()
                    : properties.keyId();
            return new RSAKey.Builder(publicKey)
                    .privateKey(privateKey)
                    .keyID(keyId)
                    .build();
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Não foi possível carregar o par RSA configurado em identity-hub.signing-key", exception);
        }
    }

    private byte[] pem(String location, String type) throws IOException {
        if (!location.startsWith("file:")) {
            throw new IllegalArgumentException("A chave PEM deve ser fornecida por recurso externo file:");
        }
        Resource resource = resources.getResource(location);
        if (!resource.exists() || !resource.isReadable()) {
            throw new IllegalArgumentException("Recurso PEM de " + type.toLowerCase() + " não está acessível");
        }
        byte[] bytes;
        try (InputStream input = resource.getInputStream()) {
            bytes = input.readNBytes(MAX_PEM_BYTES + 1);
        }
        if (bytes.length > MAX_PEM_BYTES) {
            throw new IllegalArgumentException("Recurso PEM excede o limite permitido");
        }
        String value = new String(bytes, StandardCharsets.US_ASCII).trim();
        String begin = "-----BEGIN " + type + "-----";
        String end = "-----END " + type + "-----";
        if (!value.startsWith(begin) || !value.endsWith(end)) {
            throw new IllegalArgumentException("Formato PEM esperado: " + type);
        }
        String encoded = value.substring(begin.length(), value.length() - end.length())
                .replaceAll("\\s", "");
        return Base64.getDecoder().decode(encoded);
    }

    private static void validatePair(RSAPrivateKey privateKey, RSAPublicKey publicKey) throws Exception {
        if (publicKey.getModulus().bitLength() < 2048) {
            throw new IllegalArgumentException("A chave RSA deve possuir ao menos 2048 bits");
        }
        if (!publicKey.getModulus().equals(privateKey.getModulus())) {
            throw new IllegalArgumentException("As chaves RSA pública e privada não formam um par");
        }
        byte[] challenge = "identity-hub-signing-key-validation".getBytes(StandardCharsets.US_ASCII);
        Signature signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(privateKey);
        signer.update(challenge);
        byte[] signature = signer.sign();
        Signature verifier = Signature.getInstance("SHA256withRSA");
        verifier.initVerify(publicKey);
        verifier.update(challenge);
        if (!verifier.verify(signature)) {
            throw new IllegalArgumentException("As chaves RSA pública e privada não formam um par válido");
        }
    }

    private static String required(String value, String field) {
        if (value == null) {
            throw new IllegalArgumentException("identity-hub.signing-key." + field + " é obrigatório no modo PEM");
        }
        return value;
    }
}
