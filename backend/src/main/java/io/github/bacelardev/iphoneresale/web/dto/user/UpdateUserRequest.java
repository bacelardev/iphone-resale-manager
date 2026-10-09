package io.github.bacelardev.iphoneresale.web.dto.user;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
        @NotNull @Min(0) Long expectedVersion,
        @Size(min = 1, max = 120) String name,
        @Size(min = 3, max = 50) String username,
        @Size(min = 12, max = 128) String newPassword
) {
}
