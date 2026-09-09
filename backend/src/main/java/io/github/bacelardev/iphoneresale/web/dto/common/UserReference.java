package io.github.bacelardev.iphoneresale.web.dto.common;

import io.github.bacelardev.iphoneresale.domain.model.AppUser;

import java.util.UUID;

public record UserReference(UUID id, String name) {
    public static UserReference from(AppUser user) {
        return user == null ? null : new UserReference(user.getId(), user.getName());
    }
}
