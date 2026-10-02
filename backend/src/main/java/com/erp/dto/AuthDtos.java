package com.erp.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class AuthDtos {

    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank String password) {
    }

    public record RegisterRequest(
            @NotBlank @Size(min = 2, max = 120) String fullName,
            @NotBlank @Email String email,
            @NotBlank @Size(min = 6, max = 100) String password) {
    }

    public record CreateUserRequest(
            @NotBlank @Size(min = 2, max = 120) String fullName,
            @NotBlank @Email String email,
            @NotBlank @Size(min = 6, max = 100) String password,
            @NotNull String role) {
    }

    public record AuthResponse(
            String accessToken,
            String refreshToken,
            String tokenType,
            Long id,
            String fullName,
            String email,
            String role) {
    }

    public record UserResponse(
            Long id,
            String fullName,
            String email,
            String role) {
    }
}
