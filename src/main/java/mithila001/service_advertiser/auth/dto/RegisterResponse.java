package mithila001.service_advertiser.auth.dto;

import java.time.Instant;

import mithila001.service_advertiser.user.UserRole;

public record RegisterResponse(Long id, String name, String email, UserRole role, Instant createdAt) {
}
