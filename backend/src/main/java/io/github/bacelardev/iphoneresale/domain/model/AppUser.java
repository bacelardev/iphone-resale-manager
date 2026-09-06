package io.github.bacelardev.iphoneresale.domain.model;

import io.github.bacelardev.iphoneresale.domain.enums.UserRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity
@Table(name = "app_user")
public class AppUser extends AuditableEntity {

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "username", nullable = false, unique = true, length = 50)
    private String username;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private UserRole role;

    @Column(name = "active", nullable = false)
    private boolean active;

    protected AppUser() {
    }

    public static AppUser bootstrapSocio(String name, String username, String passwordHash) {
        AppUser user = new AppUser();
        user.name = name;
        user.username = username;
        user.passwordHash = passwordHash;
        user.role = UserRole.SOCIO;
        user.active = true;
        return user;
    }

    public String getName() {
        return name;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public UserRole getRole() {
        return role;
    }

    public boolean isActive() {
        return active;
    }
}
