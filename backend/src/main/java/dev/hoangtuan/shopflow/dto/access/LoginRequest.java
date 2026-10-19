package dev.hoangtuan.shopflow.dto.access;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
    @NotBlank @Size(max = 100) String username, @NotBlank @Size(max = 200) String password) {}
