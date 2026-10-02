package com.erp.controller;

import com.erp.dto.AuthDtos.*;
import com.erp.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public List<UserResponse> list() {
        return userService.list();
    }

    @PostMapping
    public ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest request) {
        UserResponse created = userService.create(request);
        return ResponseEntity.created(URI.create("/api/users/" + created.id())).body(created);
    }

    @PatchMapping("/{id}/role")
    public UserResponse updateRole(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return userService.updateRole(id, body.getOrDefault("role", ""));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        userService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
