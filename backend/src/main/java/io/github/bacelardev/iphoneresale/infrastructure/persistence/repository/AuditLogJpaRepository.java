package io.github.bacelardev.iphoneresale.infrastructure.persistence.repository;

import io.github.bacelardev.iphoneresale.domain.model.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AuditLogJpaRepository extends JpaRepository<AuditLog, UUID> {
}
