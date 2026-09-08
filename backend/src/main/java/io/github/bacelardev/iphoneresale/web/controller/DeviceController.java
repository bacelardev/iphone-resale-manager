package io.github.bacelardev.iphoneresale.web.controller;

import io.github.bacelardev.iphoneresale.application.service.device.DeviceService;
import io.github.bacelardev.iphoneresale.domain.enums.DeviceStatus;
import io.github.bacelardev.iphoneresale.web.dto.common.PageResponse;
import io.github.bacelardev.iphoneresale.web.dto.common.VersionRequest;
import io.github.bacelardev.iphoneresale.web.dto.device.ArchiveDeviceRequest;
import io.github.bacelardev.iphoneresale.web.dto.device.DeviceDetailResponse;
import io.github.bacelardev.iphoneresale.web.dto.device.DevicePhotoResponse;
import io.github.bacelardev.iphoneresale.web.dto.device.DeviceSummaryResponse;
import io.github.bacelardev.iphoneresale.web.dto.device.RegisterDeviceRequest;
import io.github.bacelardev.iphoneresale.web.dto.device.UpdateDeviceRequest;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/v1/devices")
public class DeviceController {

    private final DeviceService service;

    public DeviceController(DeviceService service) {
        this.service = service;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DeviceDetailResponse> create(
            @Valid @RequestPart("device") RegisterDeviceRequest request,
            @RequestPart("photos") List<MultipartFile> photos
    ) {
        return created(service.createOperational(request, photos));
    }

    @PostMapping(path = "/initial-import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DeviceDetailResponse> initialImport(
            @Valid @RequestPart("device") RegisterDeviceRequest request,
            @RequestPart("photos") List<MultipartFile> photos
    ) {
        return created(service.createInitialImport(request, photos));
    }

    @GetMapping
    public PageResponse<DeviceSummaryResponse> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) List<DeviceStatus> status,
            @RequestParam(required = false) UUID modelId,
            @RequestParam(required = false) UUID colorId,
            @RequestParam(required = false) Integer storageGb,
            @RequestParam(required = false) Instant purchasedFrom,
            @RequestParam(required = false) Instant purchasedTo,
            @RequestParam(defaultValue = "false") boolean archived,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String sort
    ) {
        return service.list(search, status, modelId, colorId, storageGb, purchasedFrom,
                purchasedTo, archived, page, size, sort);
    }

    @GetMapping("/{id}")
    public DeviceDetailResponse get(@PathVariable UUID id) {
        return service.get(id);
    }

    @PatchMapping("/{id}")
    public DeviceDetailResponse update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateDeviceRequest request
    ) {
        return service.update(id, request);
    }

    @PostMapping("/{id}/mark-pending-maintenance")
    public DeviceDetailResponse markPending(
            @PathVariable UUID id,
            @Valid @RequestBody VersionRequest request
    ) {
        return service.changeStatus(id, request.expectedVersion(), DeviceStatus.PENDENTE_MANUTENCAO);
    }

    @PostMapping("/{id}/mark-available")
    public DeviceDetailResponse markAvailable(
            @PathVariable UUID id,
            @Valid @RequestBody VersionRequest request
    ) {
        return service.changeStatus(id, request.expectedVersion(), DeviceStatus.DISPONIVEL_VENDA);
    }

    @PostMapping("/{id}/archive")
    public DeviceDetailResponse archive(
            @PathVariable UUID id,
            @Valid @RequestBody ArchiveDeviceRequest request
    ) {
        return service.archive(id, request);
    }

    @PostMapping(path = "/{id}/photos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DevicePhotoResponse> addPhoto(
            @PathVariable UUID id,
            @RequestPart("file") MultipartFile file,
            @RequestParam int position
    ) {
        DevicePhotoResponse response = service.addPhoto(id, position, file);
        return ResponseEntity.created(URI.create("/api/v1/devices/" + id
                + "/photos/" + response.id())).body(response);
    }

    @GetMapping("/{id}/photos")
    public List<DevicePhotoResponse> photos(@PathVariable UUID id) {
        return service.listPhotos(id);
    }

    @DeleteMapping("/{id}/photos/{photoId}")
    public ResponseEntity<Void> removePhoto(
            @PathVariable UUID id,
            @PathVariable UUID photoId
    ) {
        service.removePhoto(id, photoId);
        return ResponseEntity.noContent().build();
    }

    private static ResponseEntity<DeviceDetailResponse> created(DeviceDetailResponse response) {
        return ResponseEntity.created(URI.create("/api/v1/devices/" + response.id())).body(response);
    }
}
