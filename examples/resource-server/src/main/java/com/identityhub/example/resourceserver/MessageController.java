package com.identityhub.example.resourceserver;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/messages")
public class MessageController {

    @GetMapping
    MessageResponse message(@AuthenticationPrincipal Jwt jwt) {
        return new MessageResponse(
                "Token validado pelo resource server independente",
                jwt.getSubject(),
                jwt.getAudience());
    }

    record MessageResponse(String message, String subject, List<String> audience) {
    }
}
