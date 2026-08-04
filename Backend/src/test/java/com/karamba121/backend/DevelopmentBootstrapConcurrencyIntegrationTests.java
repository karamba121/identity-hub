package com.karamba121.backend;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.test.context.ActiveProfiles;

import com.karamba121.backend.config.DevelopmentBootstrap;

@SpringBootTest
@ActiveProfiles("test")
class DevelopmentBootstrapConcurrencyIntegrationTests {

    @Autowired DevelopmentBootstrap bootstrap;
    @Autowired JdbcOperations jdbc;

    @Test
    void serializesTheCompleteBootstrapAcrossReplicas() throws Exception {
        jdbc.update("delete from tenant_oauth_client where client_id = ?", "identity-hub-demo");
        jdbc.update("delete from oauth2_authorization_consent where registered_client_id in "
                + "(select id from oauth2_registered_client where client_id = ?)", "identity-hub-demo");
        jdbc.update("delete from oauth2_authorization where registered_client_id in "
                + "(select id from oauth2_registered_client where client_id = ?)", "identity-hub-demo");
        jdbc.update("delete from oauth2_registered_client where client_id = ?", "identity-hub-demo");

        Callable<Void> provision = () -> {
            bootstrap.run(null);
            return null;
        };
        var executor = Executors.newFixedThreadPool(2);
        try {
            List<java.util.concurrent.Future<Void>> results = executor.invokeAll(List.of(provision, provision));
            for (var result : results) {
                result.get();
            }
        } finally {
            executor.shutdownNow();
        }

        Integer clients = jdbc.queryForObject(
                "select count(*) from oauth2_registered_client where client_id = ?",
                Integer.class,
                "identity-hub-demo");
        Integer ownerships = jdbc.queryForObject(
                "select count(*) from tenant_oauth_client where client_id = ?",
                Integer.class,
                "identity-hub-demo");
        assertThat(clients).isEqualTo(1);
        assertThat(ownerships).isEqualTo(1);
    }
}
