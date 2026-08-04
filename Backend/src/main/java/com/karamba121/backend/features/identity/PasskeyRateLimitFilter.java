package com.karamba121.backend.features.identity;

import java.io.IOException;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.karamba121.backend.features.abuse.RateLimitExceededException;
import com.karamba121.backend.features.abuse.RateLimitService;
import com.karamba121.backend.features.abuse.RateLimitedOperation;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class PasskeyRateLimitFilter extends OncePerRequestFilter {

    private final RateLimitService rateLimits;

    public PasskeyRateLimitFilter(RateLimitService rateLimits) {
        this.rateLimits = rateLimits;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!"POST".equals(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI();
        return !"/webauthn/authenticate/options".equals(path)
                && !"/login/webauthn".equals(path);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        try {
            rateLimits.check(
                    RateLimitedOperation.LOGIN,
                    request,
                    "passkey@" + request.getRemoteAddr());
            filterChain.doFilter(request, response);
        } catch (RateLimitExceededException exception) {
            response.setHeader("Retry-After", Long.toString(exception.getRetryAfterSeconds()));
            response.sendError(HttpStatus.TOO_MANY_REQUESTS.value());
        }
    }
}
