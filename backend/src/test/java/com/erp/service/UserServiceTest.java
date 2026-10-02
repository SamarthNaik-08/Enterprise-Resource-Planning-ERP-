package com.erp.service;

import com.erp.dto.AuthDtos.*;
import com.erp.entity.Role;
import com.erp.entity.User;
import com.erp.exception.BadRequestException;
import com.erp.repository.RefreshTokenRepository;
import com.erp.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private UserService userService;

    @Test
    void create_hashesPassword_savesUser_andRecordsAudit() {
        CreateUserRequest req = new CreateUserRequest("Bob Builder", "bob@erp.com", "secret123", "MANAGER");
        when(userRepository.existsByEmail("bob@erp.com")).thenReturn(false);
        when(passwordEncoder.encode("secret123")).thenReturn("hashed_secret");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(10L);
            return u;
        });

        UserResponse res = userService.create(req);

        assertEquals(10L, res.id());
        assertEquals("Bob Builder", res.fullName());
        assertEquals("MANAGER", res.role());
        verify(auditLogService).record(eq("User"), eq(10L), eq("CREATE"), contains("bob@erp.com"));
    }

    @Test
    void delete_preventsDeletingLastAdmin() {
        User admin = User.builder().id(1L).email("admin@erp.com").role(Role.ADMIN).build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(userRepository.countByRole(Role.ADMIN)).thenReturn(1L);

        BadRequestException ex = assertThrows(BadRequestException.class, () -> userService.delete(1L));
        assertTrue(ex.getMessage().contains("Cannot delete the last remaining ADMIN"));
        verify(userRepository, never()).delete(any());
        verify(refreshTokenRepository, never()).deleteByUserId(any());
    }

    @Test
    void updateRole_preventsDemotingLastAdmin() {
        User admin = User.builder().id(1L).email("admin@erp.com").role(Role.ADMIN).build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(userRepository.countByRole(Role.ADMIN)).thenReturn(1L);

        BadRequestException ex = assertThrows(BadRequestException.class, () -> userService.updateRole(1L, "EMPLOYEE"));
        assertTrue(ex.getMessage().contains("Cannot demote the last remaining ADMIN"));
        assertEquals(Role.ADMIN, admin.getRole()); // role preserved
    }

    @Test
    void delete_removesRefreshTokens_andLogsAudit() {
        User employee = User.builder().id(2L).email("emp@erp.com").fullName("Emp").role(Role.EMPLOYEE).build();
        when(userRepository.findById(2L)).thenReturn(Optional.of(employee));

        userService.delete(2L);

        verify(refreshTokenRepository).deleteByUserId(2L);
        verify(userRepository).delete(employee);
        verify(auditLogService).record(eq("User"), eq(2L), eq("DELETE"), contains("emp@erp.com"));
    }
}
