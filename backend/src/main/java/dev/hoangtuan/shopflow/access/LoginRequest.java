package dev.hoangtuan.shopflow.access;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

record LoginRequest(
    @NotBlank @Size(max = 100) String username, @NotBlank @Size(max = 200) String password) {}
