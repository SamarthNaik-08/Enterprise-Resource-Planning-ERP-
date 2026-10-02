package com.erp.service;

import com.erp.dto.AuthDtos.CreateUserRequest;
import com.erp.dto.AuthDtos.UserResponse;
import com.erp.entity.Role;
import com.erp.entity.User;
import com.erp.exception.BadRequestException;
import com.erp.exception.NotFoundException;
import com.erp.repository.RefreshTokenRepository;
import com.erp.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public List<UserResponse> list() {
        return userRepository.findAll().stream()
                .map(u -> new UserResponse(u.getId(), u.getFullName(), u.getEmail(),
                        u.getRole().name()))
                .toList();
    }

    @Transactional
    public UserResponse create(CreateUserRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new BadRequestException("Email is already registered: " + request.email());
        }
        User user = User.builder()
                .fullName(request.fullName())
                .email(request.email())
                .password(passwordEncoder.encode(request.password()))
                .role(parseRole(request.role()))
                .build();
        User saved = userRepository.save(user);
        auditLogService.record("User", saved.getId(), "CREATE",
                "Created user " + saved.getFullName() + " (" + saved.getEmail() + ") with role " + saved.getRole());
        return new UserResponse(saved.getId(), saved.getFullName(), saved.getEmail(),
                saved.getRole().name());
    }

    @Transactional
    public UserResponse updateRole(Long id, String role) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("User", id));
        Role newRole = parseRole(role);
        if (user.getRole() == Role.ADMIN && newRole != Role.ADMIN && userRepository.countByRole(Role.ADMIN) <= 1) {
            throw new BadRequestException("Cannot demote the last remaining ADMIN user.");
        }
        Role oldRole = user.getRole();
        user.setRole(newRole);
        auditLogService.record("User", user.getId(), "UPDATE",
                "Updated role for user " + user.getEmail() + " from " + oldRole + " to " + newRole);
        return new UserResponse(user.getId(), user.getFullName(), user.getEmail(),
                user.getRole().name());
    }

    @Transactional
    public void delete(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("User", id));
        if (user.getRole() == Role.ADMIN && userRepository.countByRole(Role.ADMIN) <= 1) {
            throw new BadRequestException("Cannot delete the last remaining ADMIN user.");
        }
        // Clean up any issued refresh tokens to prevent foreign key violation
        refreshTokenRepository.deleteByUserId(id);
        userRepository.delete(user);
        auditLogService.record("User", id, "DELETE",
                "Deleted user " + user.getFullName() + " (" + user.getEmail() + ")");
    }

    private Role parseRole(String role) {
        try {
            return Role.valueOf(role.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid role: " + role
                    + " (allowed: ADMIN, MANAGER, EMPLOYEE)");
        }
    }
}
