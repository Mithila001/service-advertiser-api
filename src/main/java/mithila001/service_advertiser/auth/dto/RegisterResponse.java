package mithila001.service_advertiser.auth.dto;

import java.time.LocalDateTime;

import mithila001.service_advertiser.user.UserRole;

public record RegisterResponse(Long id, String name, String email, UserRole role, LocalDateTime createdAt) {
}
