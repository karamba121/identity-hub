package com.karamba121.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.karamba121.backend.features.identity.PasswordPolicy;

class PasswordPolicyTests {

    private final PasswordPolicy policy = new PasswordPolicy();

    @Test
    void acceptsLongPassphrasesWithoutCompositionRulesAndNormalizesUnicode() {
        String decomposed = "uma frase longa com cafe\u0301";

        String accepted = policy.validate(decomposed, "pessoa@example.test", "Pessoa Usuária");

        assertThat(accepted).isEqualTo("uma frase longa com café");
    }

    @Test
    void rejectsPasswordsOutsideTheSupportedLength() {
        assertThatThrownBy(() -> policy.validate("curta demais", "user@example.test", "User"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("15");
        assertThatThrownBy(() -> policy.validate("a".repeat(129), "user@example.test", "User"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("128");
    }

    @Test
    void rejectsCommonServiceSpecificAndRepeatedPasswords() {
        assertRejected("password1234567", "A senha escolhida é muito comum ou previsível");
        assertRejected("111111111111111", "A senha escolhida é muito comum ou previsível");
        assertRejected("identity-hub-access-2026", "A senha escolhida é muito comum ou previsível");
    }

    @Test
    void rejectsPersonalContextAndControlCharacters() {
        assertThatThrownBy(() -> policy.validate(
                        "frase-carlos-segura-2026", "carlos@example.test", "Carlos Silva"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nome ou identificador");
        assertThatThrownBy(() -> policy.validate(
                        "frase longa\ncom controle", "user@example.test", "User"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("controle");
    }

    private void assertRejected(String password, String message) {
        assertThatThrownBy(() -> policy.validate(password, "user@example.test", "User"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(message);
    }
}
