package com.karamba121.backend.config;

import java.util.List;
import java.util.ArrayList;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.ObjectPostProcessor;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.config.annotation.web.configurers.oauth2.server.authorization.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2RefreshTokenAuthenticationProvider;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.JwtGenerator;
import org.springframework.security.oauth2.server.authorization.token.DelegatingOAuth2TokenGenerator;
import org.springframework.security.oauth2.server.authorization.token.OAuth2AccessTokenGenerator;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.RegisterSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.security.web.webauthn.api.AuthenticatorSelectionCriteria;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialRpEntity;
import org.springframework.security.web.webauthn.api.ResidentKeyRequirement;
import org.springframework.security.web.webauthn.api.UserVerificationRequirement;
import org.springframework.security.web.webauthn.authentication.WebAuthnAuthenticationFilter;
import org.springframework.security.web.webauthn.management.JdbcPublicKeyCredentialUserEntityRepository;
import org.springframework.security.web.webauthn.management.JdbcUserCredentialRepository;
import org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository;
import org.springframework.security.web.webauthn.management.UserCredentialRepository;
import org.springframework.security.web.webauthn.management.WebAuthnRelyingPartyOperations;
import org.springframework.security.web.webauthn.management.Webauthn4JRelyingPartyOperations;
import org.springframework.security.web.webauthn.registration.WebAuthnRegistrationFilter;

import com.karamba121.backend.features.identity.IdentityUserDetailsService;
import com.karamba121.backend.features.identity.IdentityUserRepository;
import com.karamba121.backend.features.identity.LoginAttemptService;
import com.karamba121.backend.features.identity.LoginProtectionAuthenticationProvider;
import com.karamba121.backend.features.identity.NormalizingPasswordEncoder;
import com.karamba121.backend.features.identity.ActiveIdentityWebAuthnAuthenticationProvider;
import com.karamba121.backend.features.identity.AuditedUserCredentialRepository;
import com.karamba121.backend.features.identity.IdentitySecurityAuditor;
import com.karamba121.backend.features.identity.PasskeyRateLimitFilter;
import com.karamba121.backend.features.access.RotatingClientSecretPasswordEncoder;
import com.karamba121.backend.features.access.AdminResourceContract;
import com.karamba121.backend.features.interaction.LoginInteractionEntryPoint;
import com.karamba121.backend.features.interaction.PublicParClientAuthenticationConverter;
import com.karamba121.backend.features.interaction.PublicParClientAuthenticationProvider;
import com.karamba121.backend.features.resource.DemoResourceContract;
import com.karamba121.backend.features.session.RefreshTokenFamilyRepository;
import com.karamba121.backend.features.session.RefreshTokenHistoryRepository;
import com.karamba121.backend.features.session.RefreshTokenTrackingAuthorizationService;
import com.karamba121.backend.features.session.TransactionalRefreshTokenAuthenticationProvider;
import com.karamba121.backend.features.session.PublicClientRefreshTokenGenerator;
import com.karamba121.backend.features.session.PublicRefreshClientAuthenticationConverter;
import com.karamba121.backend.features.session.PublicRefreshClientAuthenticationProvider;
import com.karamba121.backend.features.session.SessionMetrics;
import com.karamba121.backend.features.session.CredentialVersionTokenValidator;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;

@Configuration
public class SecurityConfig {

    @Bean
    @Order(1)
    SecurityFilterChain authorizationServerSecurityFilterChain(
            HttpSecurity http,
            LoginInteractionEntryPoint loginEntryPoint,
            PlatformTransactionManager transactionManager,
            OAuth2TokenGenerator<?> tokenGenerator,
            RegisteredClientRepository registeredClients,
            SessionMetrics sessionMetrics,
            SessionRegistry sessionRegistry) throws Exception {
        http.oauth2AuthorizationServer(authorizationServer -> authorizationServer
                .tokenGenerator(tokenGenerator)
                .clientAuthentication(client -> client
                        .authenticationConverters(converters -> {
                            converters.add(0, new PublicParClientAuthenticationConverter());
                            converters.add(0, new PublicRefreshClientAuthenticationConverter());
                        })
                        .authenticationProviders(providers -> {
                            providers.add(0, new PublicParClientAuthenticationProvider(registeredClients));
                            providers.add(0, new PublicRefreshClientAuthenticationProvider(registeredClients));
                        }))
                .authorizationEndpoint(endpoint -> endpoint.consentPage("/oauth2/consent"))
                .pushedAuthorizationRequestEndpoint(Customizer.withDefaults())
                .tokenEndpoint(endpoint -> endpoint.authenticationProviders(providers -> {
                    for (int index = 0; index < providers.size(); index++) {
                        AuthenticationProvider provider = providers.get(index);
                        if (provider instanceof OAuth2RefreshTokenAuthenticationProvider) {
                            providers.set(index, new TransactionalRefreshTokenAuthenticationProvider(
                                    provider, transactionManager, sessionMetrics));
                            break;
                        }
                    }
                }))
                .oidc(Customizer.withDefaults()));

        OAuth2AuthorizationServerConfigurer authorizationServerConfigurer = http
                .getConfigurer(OAuth2AuthorizationServerConfigurer.class);
        http.securityMatcher(authorizationServerConfigurer.getEndpointsMatcher())
                .authorizeHttpRequests(authorize -> authorize.anyRequest().authenticated())
                .sessionManagement(session -> session
                        .maximumSessions(-1)
                        .sessionRegistry(sessionRegistry))
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(loginEntryPoint));
        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain administrativeResourceSecurityFilterChain(
            HttpSecurity http,
            @Qualifier("adminResourceJwtDecoder") JwtDecoder adminResourceJwtDecoder) throws Exception {
        http.securityMatcher("/api/v1/admin/**")
                .authorizeHttpRequests(authorize -> authorize
                        .anyRequest().hasAuthority("SCOPE_" + AdminResourceContract.SCOPE))
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(AbstractHttpConfigurer::disable)
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(jwt -> jwt.decoder(adminResourceJwtDecoder)));
        return http.build();
    }

    @Bean
    @Order(3)
    SecurityFilterChain demoResourceSecurityFilterChain(
            HttpSecurity http,
            @Qualifier("resourceServerJwtDecoder") JwtDecoder resourceServerJwtDecoder) throws Exception {
        http.securityMatcher("/api/v1/demo/**")
                .authorizeHttpRequests(authorize -> authorize
                        .anyRequest().hasAuthority("SCOPE_" + DemoResourceContract.SCOPE))
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(AbstractHttpConfigurer::disable)
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(jwt -> jwt.decoder(resourceServerJwtDecoder)));
        return http.build();
    }

    @Bean
    @Order(4)
    SecurityFilterChain applicationSecurityFilterChain(
            HttpSecurity http,
            SessionRegistry sessionRegistry,
            MetricsScrapeAuthenticationFilter metricsScrapeAuthenticationFilter,
            PasskeyRateLimitFilter passkeyRateLimitFilter,
            WebAuthnRelyingPartyOperations relyingParty,
            UserCredentialRepository credentials,
            PublicKeyCredentialUserEntityRepository credentialUsers,
            IdentityUserDetailsService userDetailsService,
            SessionAuthenticationStrategy sessionAuthenticationStrategy,
            IdentitySecurityAuditor identityAuditor,
            IdentityHubProperties properties) throws Exception {
        CookieCsrfTokenRepository csrfRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfRepository.setCookiePath("/");
        CsrfTokenRequestAttributeHandler csrfRequestHandler = new CsrfTokenRequestAttributeHandler();
        AuthenticationProvider webAuthnAuthenticationProvider =
                new ActiveIdentityWebAuthnAuthenticationProvider(
                        relyingParty, credentials, credentialUsers, userDetailsService);

        http.authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/error", "/actuator/health/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/interactions/*").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/interactions/*/login").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/interactions/*/mfa").permitAll()
                        .requestMatchers(HttpMethod.POST,
                                "/webauthn/authenticate/options", "/login/webauthn").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/registrations/csrf").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/registrations", "/api/v1/registrations/verify")
                                .permitAll()
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/password-recovery", "/api/v1/password-recovery/complete")
                                .permitAll()
                        .anyRequest().authenticated())
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfRepository)
                        .csrfTokenRequestHandler(csrfRequestHandler))
                .sessionManagement(session -> session
                        .maximumSessions(-1)
                        .sessionRegistry(sessionRegistry))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(restAuthenticationEntryPoint()))
                .webAuthn(webAuthn -> webAuthn
                        .rpId(properties.webauthn().rpId())
                        .rpName(properties.webauthn().rpName())
                        .allowedOrigins(properties.webauthn().allowedOrigins())
                        .disableDefaultRegistrationPage(true)
                        .withObjectPostProcessor(new ObjectPostProcessor<WebAuthnAuthenticationFilter>() {
                            @Override
                            public <O extends WebAuthnAuthenticationFilter> O postProcess(O filter) {
                                filter.setAuthenticationManager(new ProviderManager(webAuthnAuthenticationProvider));
                                filter.setSessionAuthenticationStrategy(sessionAuthenticationStrategy);
                                filter.setAuthenticationSuccessHandler((request, response, authentication) -> {
                                    try {
                                        identityAuditor.execute(
                                                authentication.getName(),
                                                com.karamba121.backend.features.access.SecurityAuditEventType
                                                        .PASSKEY_AUTHENTICATION_SUCCEEDED,
                                                () -> authentication);
                                        response.setStatus(HttpStatus.OK.value());
                                    } catch (RuntimeException exception) {
                                        SecurityContextHolder.clearContext();
                                        if (request.getSession(false) != null) {
                                            request.getSession(false).invalidate();
                                        }
                                        response.sendError(
                                                HttpStatus.SERVICE_UNAVAILABLE.value(),
                                                "Não foi possível concluir a autenticação");
                                    }
                                });
                                return filter;
                            }
                        })
                        .withObjectPostProcessor(new ObjectPostProcessor<WebAuthnRegistrationFilter>() {
                            @Override
                            public <O extends WebAuthnRegistrationFilter> O postProcess(O filter) {
                                filter.setRemoveCredentialMatcher(request -> false);
                                return filter;
                            }
                        }))
                .addFilterBefore(passkeyRateLimitFilter, BasicAuthenticationFilter.class)
                .addFilterBefore(metricsScrapeAuthenticationFilter, AnonymousAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    AuthenticationEntryPoint restAuthenticationEntryPoint() {
        return new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED);
    }

    @Bean
    RegisteredClientRepository registeredClientRepository(JdbcOperations jdbcOperations) {
        return new JdbcRegisteredClientRepository(jdbcOperations);
    }

    @Bean
    PublicKeyCredentialUserEntityRepository publicKeyCredentialUserEntityRepository(
            JdbcOperations jdbcOperations) {
        return new JdbcPublicKeyCredentialUserEntityRepository(jdbcOperations);
    }

    @Bean
    UserCredentialRepository userCredentialRepository(
            JdbcOperations jdbcOperations,
            PublicKeyCredentialUserEntityRepository users,
            IdentitySecurityAuditor auditor) {
        return new AuditedUserCredentialRepository(
                new JdbcUserCredentialRepository(jdbcOperations), users, auditor);
    }

    @Bean
    WebAuthnRelyingPartyOperations webAuthnRelyingPartyOperations(
            PublicKeyCredentialUserEntityRepository users,
            UserCredentialRepository credentials,
            IdentityHubProperties properties) {
        IdentityHubProperties.WebAuthn settings = properties.webauthn();
        if (settings == null || settings.rpId() == null || settings.rpId().isBlank()
                || settings.rpName() == null || settings.rpName().isBlank()
                || settings.allowedOrigins() == null || settings.allowedOrigins().isEmpty()) {
            throw new IllegalStateException("Configuração WebAuthn incompleta");
        }
        Webauthn4JRelyingPartyOperations relyingParty = new Webauthn4JRelyingPartyOperations(
                users,
                credentials,
                PublicKeyCredentialRpEntity.builder()
                        .id(settings.rpId())
                        .name(settings.rpName())
                        .build(),
                settings.allowedOrigins());
        relyingParty.setCustomizeCreationOptions(options -> options.authenticatorSelection(
                AuthenticatorSelectionCriteria.builder()
                        .residentKey(ResidentKeyRequirement.REQUIRED)
                        .userVerification(UserVerificationRequirement.REQUIRED)
                        .build()));
        relyingParty.setCustomizeRequestOptions(options -> options
                .userVerification(UserVerificationRequirement.REQUIRED));
        return relyingParty;
    }

    @Bean
    OAuth2AuthorizationService authorizationService(
            JdbcOperations jdbcOperations,
            RegisteredClientRepository registeredClientRepository,
            RefreshTokenFamilyRepository families,
            RefreshTokenHistoryRepository history,
            SessionMetrics sessionMetrics) {
        JdbcOAuth2AuthorizationService delegate =
                new JdbcOAuth2AuthorizationService(jdbcOperations, registeredClientRepository);
        return new RefreshTokenTrackingAuthorizationService(
                delegate, families, history, sessionMetrics);
    }

    @Bean
    OAuth2AuthorizationConsentService authorizationConsentService(
            JdbcOperations jdbcOperations,
            RegisteredClientRepository registeredClientRepository) {
        return new JdbcOAuth2AuthorizationConsentService(jdbcOperations, registeredClientRepository);
    }

    @Bean
    RotatingClientSecretPasswordEncoder passwordEncoder() {
        Argon2PasswordEncoder argon2 = new Argon2PasswordEncoder(16, 32, 1, 19_456, 2);
        BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder(12);
        DelegatingPasswordEncoder delegate = new DelegatingPasswordEncoder(
                "argon2id",
                java.util.Map.of("argon2id", argon2, "bcrypt", bcrypt));
        delegate.setDefaultPasswordEncoderForMatches(bcrypt);
        return new RotatingClientSecretPasswordEncoder(
                new NormalizingPasswordEncoder(delegate),
                java.time.Clock.systemUTC());
    }

    @Bean
    AuthenticationManager authenticationManager(
            IdentityUserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder,
            LoginAttemptService loginAttempts) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        provider.setUserDetailsPasswordService(userDetailsService);
        return new ProviderManager(List.of(
                new LoginProtectionAuthenticationProvider(provider, loginAttempts)));
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    SessionRegistry sessionRegistry() {
        return new SessionRegistryImpl();
    }

    @Bean
    HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }

    @Bean
    SessionAuthenticationStrategy sessionAuthenticationStrategy(SessionRegistry sessionRegistry) {
        return new CompositeSessionAuthenticationStrategy(List.of(
                new ChangeSessionIdAuthenticationStrategy(),
                new RegisterSessionAuthenticationStrategy(sessionRegistry)));
    }

    @Bean
    AuthorizationServerSettings authorizationServerSettings(IdentityHubProperties properties) {
        return AuthorizationServerSettings.builder().issuer(properties.issuer()).build();
    }

    @Bean
    @Primary
    JwtDecoder jwtDecoder(JWKSource<SecurityContext> jwkSource) {
        return OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource);
    }

    @Bean("resourceServerJwtDecoder")
    JwtDecoder resourceServerJwtDecoder(
            JWKSource<SecurityContext> jwkSource,
            IdentityHubProperties properties,
            IdentityUserRepository users) {
        JwtDecoder delegate = OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource);
        OAuth2TokenValidator<Jwt> issuer = JwtValidators.createDefaultWithIssuer(properties.issuer());
        OAuth2TokenValidator<Jwt> audience = token -> token.getAudience().contains(DemoResourceContract.AUDIENCE)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error(
                        "invalid_token",
                        "Access token não foi emitido para a API demonstrativa",
                        null));
        OAuth2TokenValidator<Jwt> validator = new DelegatingOAuth2TokenValidator<>(
                issuer, audience, new CredentialVersionTokenValidator(users));
        return encodedToken -> {
            Jwt jwt = delegate.decode(encodedToken);
            OAuth2TokenValidatorResult result = validator.validate(jwt);
            if (result.hasErrors()) {
                throw new JwtValidationException("Access token inválido", result.getErrors());
            }
            return jwt;
        };
    }

    @Bean("adminResourceJwtDecoder")
    JwtDecoder adminResourceJwtDecoder(
            JWKSource<SecurityContext> jwkSource,
            IdentityHubProperties properties,
            IdentityUserRepository users) {
        JwtDecoder delegate = OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource);
        OAuth2TokenValidator<Jwt> issuer = JwtValidators.createDefaultWithIssuer(properties.issuer());
        OAuth2TokenValidator<Jwt> audience = token -> token.getAudience().contains(AdminResourceContract.AUDIENCE)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error(
                        "invalid_token",
                        "Access token não foi emitido para a API administrativa",
                        null));
        OAuth2TokenValidator<Jwt> validator = new DelegatingOAuth2TokenValidator<>(
                issuer, audience, new CredentialVersionTokenValidator(users));
        return encodedToken -> {
            Jwt jwt = delegate.decode(encodedToken);
            OAuth2TokenValidatorResult result = validator.validate(jwt);
            if (result.hasErrors()) {
                throw new JwtValidationException("Access token inválido", result.getErrors());
            }
            return jwt;
        };
    }

    @Bean
    OAuth2TokenCustomizer<JwtEncodingContext> identityClaimsCustomizer(IdentityUserRepository users) {
        return context -> {
            if (!OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())
                    && !"id_token".equals(context.getTokenType().getValue())) {
                return;
            }
            if (OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) {
                List<String> audiences = new ArrayList<>();
                if (context.getAuthorizedScopes().contains(DemoResourceContract.SCOPE)) {
                    audiences.add(DemoResourceContract.AUDIENCE);
                }
                if (context.getAuthorizedScopes().contains(AdminResourceContract.SCOPE)) {
                    audiences.add(AdminResourceContract.AUDIENCE);
                }
                if (!audiences.isEmpty()) {
                    context.getClaims().audience(audiences);
                }
                if (AuthorizationGrantType.CLIENT_CREDENTIALS.equals(context.getAuthorizationGrantType())) {
                    String clientId = context.getRegisteredClient().getClientId();
                    context.getClaims()
                            .subject("client:" + clientId)
                            .claim("client_id", clientId);
                }
            }
            users.findByEmailIgnoreCase(context.getPrincipal().getName()).ifPresent(user -> context.getClaims()
                    .subject(user.getId())
                    .claim("name", user.getDisplayName())
                    .claim("email", user.getEmail())
                    .claim("email_verified", user.isEmailVerified())
                    .claim(CredentialVersionTokenValidator.CLAIM, Long.toString(user.getCredentialVersion())));
        };
    }

    @Bean
    OAuth2TokenGenerator<?> tokenGenerator(
            JwtEncoder jwtEncoder,
            OAuth2TokenCustomizer<JwtEncodingContext> jwtCustomizer) {
        JwtGenerator jwtGenerator = new JwtGenerator(jwtEncoder);
        jwtGenerator.setJwtCustomizer(jwtCustomizer);
        return new DelegatingOAuth2TokenGenerator(
                jwtGenerator,
                new OAuth2AccessTokenGenerator(),
                new PublicClientRefreshTokenGenerator());
    }

}
