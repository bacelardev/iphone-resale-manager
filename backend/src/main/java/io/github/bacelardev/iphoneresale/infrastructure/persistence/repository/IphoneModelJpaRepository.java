package io.github.bacelardev.iphoneresale.infrastructure.persistence.repository;

import io.github.bacelardev.iphoneresale.domain.model.IphoneModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface IphoneModelJpaRepository
        extends JpaRepository<IphoneModel, UUID>, JpaSpecificationExecutor<IphoneModel> {

    boolean existsByCode(String code);

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, UUID id);
}
