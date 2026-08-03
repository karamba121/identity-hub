package com.karamba121.backend.features.interaction;

import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.Comparator;
import java.util.List;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.util.HtmlUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Controller
@RequestMapping("/api/v1/interactions")
public class InteractionController {

    private final SecurityContextHolderStrategy securityContextHolderStrategy = SecurityContextHolder
            .getContextHolderStrategy();
    private final ChangeSessionIdAuthenticationStrategy sessionStrategy = new ChangeSessionIdAuthenticationStrategy();

    private final AuthorizationInteractionService interactions;
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;

    public InteractionController(
            AuthorizationInteractionService interactions,
            AuthenticationManager authenticationManager,
            SecurityContextRepository securityContextRepository) {
        this.interactions = interactions;
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
    }

    @GetMapping("/{interactionId}")
    @ResponseBody
    @Transactional(readOnly = true)
    public InteractionView get(
            @PathVariable String interactionId,
            HttpServletRequest request,
            CsrfToken csrfToken) {
        csrfToken.getToken();
        AuthorizationInteraction interaction = interactions.resolvePending(
                interactionId, request);
        if (interaction.getType() == InteractionType.CONSENT) {
            if (request.getUserPrincipal() == null) {
                throw new InteractionException(HttpStatus.UNAUTHORIZED, "Sessão não autenticada");
            }
            interactions.requirePrincipal(interaction, request.getUserPrincipal().getName());
        }
        RegisteredClient client = interactions.requireClient(interaction.getClientId());
        List<String> scopes = interactions.scopes(interaction).stream().sorted().toList();
        return new InteractionView(
                interaction.getType().name().toLowerCase(),
                client.getClientName(),
                scopes,
                interaction.getExpiresAt().toString());
    }

    @PostMapping("/{interactionId}/login")
    @ResponseBody
    public LoginResult login(
            @PathVariable String interactionId,
            @RequestBody LoginRequest body,
            HttpServletRequest request,
            HttpServletResponse response) {
        AuthorizationInteraction interaction = interactions.resolvePending(
                interactionId, request, InteractionType.LOGIN);
        if (body == null || !StringUtils.hasText(body.email()) || !StringUtils.hasText(body.password())) {
            throw new InteractionException(HttpStatus.BAD_REQUEST, "E-mail e senha são obrigatórios");
        }

        try {
            Authentication authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(
                            body.email().trim().toLowerCase(), body.password()));
            sessionStrategy.onAuthentication(authentication, request, response);
            SecurityContext context = securityContextHolderStrategy.createEmptyContext();
            context.setAuthentication(authentication);
            securityContextHolderStrategy.setContext(context);
            securityContextRepository.saveContext(context, request, response);
            interactions.completeLogin(interaction, request.getSession(false).getId());
            return new LoginResult(interaction.getResumeUri());
        } catch (AuthenticationException exception) {
            throw new InteractionException(HttpStatus.UNAUTHORIZED, "Credenciais inválidas");
        }
    }

    @PostMapping("/{interactionId}/consent")
    @ResponseBody
    public ConsentResult consent(
            @PathVariable String interactionId,
            @RequestBody ConsentRequest body,
            Principal principal,
            HttpServletRequest request) {
        if (principal == null) {
            throw new InteractionException(HttpStatus.UNAUTHORIZED, "Sessão não autenticada");
        }
        AuthorizationInteraction interaction = interactions.resolvePending(
                interactionId, request, InteractionType.CONSENT);
        interactions.requirePrincipal(interaction, principal.getName());
        if (body != null && body.approved()) {
            interactions.approve(interaction);
            return new ConsentResult("/api/v1/interactions/" + interactionId + "/consent/continue");
        }
        return new ConsentResult(interactions.deny(interaction));
    }

    @GetMapping(value = "/{interactionId}/consent/continue", produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    public ResponseEntity<String> continueConsent(
            @PathVariable String interactionId,
            Principal principal,
            HttpServletRequest request) {
        if (principal == null) {
            throw new InteractionException(HttpStatus.UNAUTHORIZED, "Sessão não autenticada");
        }
        AuthorizationInteraction interaction = interactions.resolveApproved(interactionId, request);
        interactions.requirePrincipal(interaction, principal.getName());
        RegisteredClient client = interactions.requireClient(interaction.getClientId());
        List<String> scopes = interactions.scopes(interaction).stream()
                .sorted(Comparator.naturalOrder())
                .toList();

        StringBuilder inputs = new StringBuilder();
        inputs.append(hidden("client_id", client.getClientId()));
        inputs.append(hidden("state", interaction.getOauthState()));
        for (String scope : scopes) {
            inputs.append(hidden("scope", scope));
        }
        interactions.complete(interaction);

        String html = "<!doctype html><html lang=\"pt-BR\"><head><meta charset=\"utf-8\">"
                + "<title>Continuando autorização</title></head><body>"
                + "<form id=\"consent\" method=\"post\" action=\"/oauth2/authorize\">"
                + inputs + "</form><script>document.getElementById('consent').submit();</script>"
                + "</body></html>";

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header("Content-Security-Policy", "default-src 'none'; script-src 'unsafe-inline'; form-action 'self'")
                .contentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8))
                .body(html);
    }

    private String hidden(String name, String value) {
        return "<input type=\"hidden\" name=\"" + HtmlUtils.htmlEscape(name) + "\" value=\""
                + HtmlUtils.htmlEscape(value) + "\">";
    }

    public record InteractionView(String type, String clientName, List<String> scopes, String expiresAt) {
    }

    public record LoginRequest(String email, String password) {
    }

    public record LoginResult(String continueUrl) {
    }

    public record ConsentRequest(boolean approved) {
    }

    public record ConsentResult(String continueUrl) {
    }
}
