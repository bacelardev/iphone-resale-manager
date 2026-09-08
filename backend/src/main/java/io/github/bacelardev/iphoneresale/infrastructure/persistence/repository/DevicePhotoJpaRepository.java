package io.github.bacelardev.iphoneresale.infrastructure.persistence.repository;

import io.github.bacelardev.iphoneresale.domain.model.DevicePhoto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DevicePhotoJpaRepository extends JpaRepository<DevicePhoto, UUID> {

    List<DevicePhoto> findByDeviceIdAndRemovedAtIsNullOrderByPositionAsc(UUID deviceId);

    Optional<DevicePhoto> findByIdAndDeviceIdAndRemovedAtIsNull(UUID id, UUID deviceId);

    Optional<DevicePhoto> findByIdAndRemovedAtIsNull(UUID id);

    long countByDeviceIdAndRemovedAtIsNull(UUID deviceId);

    boolean existsByDeviceIdAndPositionAndRemovedAtIsNull(UUID deviceId, int position);
}
