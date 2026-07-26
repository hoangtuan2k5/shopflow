package dev.hoangtuan.shopflow.access;

record AuthenticatedUser(Long userId, String username, String displayName, Role role) {}
