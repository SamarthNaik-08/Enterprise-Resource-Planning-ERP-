package com.erp.controller;

import com.erp.dto.AuthDtos.*;
import com.erp.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    /** Expects {"refreshToken": "..."} — returns a fresh token pair (rotation). */
    @PostMapping("/refresh")
    public AuthResponse refresh(@RequestBody Map<String, String> body) {
        return authService.refresh(body.get("refreshToken"));
    }

    /** Expects {"refreshToken": "..."} — revokes it server-side (real logout). */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody Map<String, String> body) {
        authService.logout(body.get("refreshToken"));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @GetMapping("/me")
    public UserResponse me(Authentication authentication) {
        return authService.currentUser(authentication.getName());
    }

    @GetMapping("/ping")
    public Map<String, String> ping() {
        return Map.of("status", "ok");
    }
}
