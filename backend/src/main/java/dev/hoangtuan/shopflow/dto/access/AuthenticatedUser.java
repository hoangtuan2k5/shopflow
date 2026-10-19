package dev.hoangtuan.shopflow.dto.access;

import dev.hoangtuan.shopflow.model.access.Role;

public record AuthenticatedUser(Long userId, String username, String displayName, Role role) {}
