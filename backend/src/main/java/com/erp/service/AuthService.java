package com.erp.service;

import com.erp.dto.AuthDtos.*;
import com.erp.entity.Role;
import com.erp.entity.User;
import com.erp.exception.BadRequestException;
import com.erp.exception.NotFoundException;
import com.erp.repository.UserRepository;
import com.erp.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new BadRequestException("Email is already registered: " + request.email());
        }
        User user = User.builder()
                .fullName(request.fullName())
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .role(Role.EMPLOYEE) // self-registration always starts as EMPLOYEE
                .build();
        userRepository.save(user);
        return buildAuthResponse(user);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password()));
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new NotFoundException("User", 0L));
        return buildAuthResponse(user);
    }

    /** Exchanges a valid refresh token for a new token pair (rotation). */
    @Transactional(noRollbackFor = BadRequestException.class)
    public AuthResponse refresh(String rawRefreshToken) {
        User user = refreshTokenService.rotate(rawRefreshToken);
        return buildAuthResponse(user);
    }

    /** Revokes the refresh token server-side: real logout. */
    @Transactional
    public void logout(String rawRefreshToken) {
        refreshTokenService.revoke(rawRefreshToken);
    }

    public UserResponse currentUser(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new NotFoundException("User", 0L));
        return new UserResponse(user.getId(), user.getFullName(), user.getEmail(),
                user.getRole().name());
    }

    private AuthResponse buildAuthResponse(User user) {
        String accessToken = jwtService.generateToken(user.getEmail(), user.getRole().name());
        String refreshToken = refreshTokenService.issue(user);
        return new AuthResponse(accessToken, refreshToken, "Bearer", user.getId(),
                user.getFullName(), user.getEmail(), user.getRole().name());
    }
}
