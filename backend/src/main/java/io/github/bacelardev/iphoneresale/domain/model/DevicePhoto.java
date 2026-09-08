package io.github.bacelardev.iphoneresale.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "device_photo")
public class DevicePhoto extends CreatedOnlyEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "device_id", nullable = false, updatable = false)
    private Device device;

    @Column(name = "storage_key", nullable = false, unique = true, updatable = false, length = 512)
    private String storageKey;

    @Column(name = "original_filename", nullable = false, updatable = false, length = 255)
    private String originalFilename;

    @Column(name = "mime_type", nullable = false, updatable = false, length = 100)
    private String mimeType;

    @Column(name = "size_bytes", nullable = false, updatable = false)
    private long sizeBytes;

    @Column(name = "position", nullable = false, updatable = false)
    private int position;

    @Column(name = "removed_at")
    private Instant removedAt;

    protected DevicePhoto() {
    }

    public DevicePhoto(
            Device device,
            String storageKey,
            String originalFilename,
            String mimeType,
            long sizeBytes,
            int position
    ) {
        this.device = device;
        this.storageKey = storageKey;
        this.originalFilename = originalFilename;
        this.mimeType = mimeType;
        this.sizeBytes = sizeBytes;
        this.position = position;
    }

    public void remove(Instant removedAt) {
        this.removedAt = removedAt;
    }

    public Device getDevice() {
        return device;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public String getMimeType() {
        return mimeType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public int getPosition() {
        return position;
    }

    public Instant getRemovedAt() {
        return removedAt;
    }
}
