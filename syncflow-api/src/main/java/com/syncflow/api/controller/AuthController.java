package com.syncflow.api.controller;

import com.syncflow.api.security.AuthService;
import com.syncflow.api.user.entity.UserEntity;
import com.syncflow.api.user.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final UserRepository userRepository;

    public AuthController(AuthService authService, UserRepository userRepository) {
        this.authService = authService;
        this.userRepository = userRepository;
    }

    public record LoginRequest(String username, String password) {
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody LoginRequest req) {
        try {
            var token = authService.login(req.username(), req.password());
            return ResponseEntity.ok(Map.of("token", token, "tokenType", "Bearer"));
        } catch (BadCredentialsException | DisabledException | LockedException e) {
            // Credential failures and disabled/locked accounts are all a 401 — do not
            // reveal which; a 500 would be wrong and leak that the account exists.
            return ResponseEntity.status(401).body(Map.of("error", "invalid credentials"));
        }
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me(Authentication auth) {
        var user = userRepository.findByUsername(auth.getName())
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + auth.getName()));
        return ResponseEntity.ok(toMap(user));
    }

    private Map<String, Object> toMap(UserEntity u) {
        return Map.of(
                "id", u.getId(),
                "username", u.getUsername(),
                "email", u.getEmail() != null ? u.getEmail() : "",
                "roles", u.getRoles(),
                "enabled", u.isEnabled());
    }
}
