package io.github.bacelardev.iphoneresale.application.service.device;

import io.github.bacelardev.iphoneresale.application.port.storage.PhotoStorage;
import io.github.bacelardev.iphoneresale.application.port.storage.StoredPhoto;
import io.github.bacelardev.iphoneresale.application.service.AuditService;
import io.github.bacelardev.iphoneresale.application.service.BusinessException;
import io.github.bacelardev.iphoneresale.application.service.PageableFactory;
import io.github.bacelardev.iphoneresale.application.service.VersionGuard;
import io.github.bacelardev.iphoneresale.application.service.initialization.BusinessInitializationService;
import io.github.bacelardev.iphoneresale.domain.enums.AuditAction;
import io.github.bacelardev.iphoneresale.domain.enums.AuditedEntityType;
import io.github.bacelardev.iphoneresale.domain.enums.BusinessInitializationStatus;
import io.github.bacelardev.iphoneresale.domain.enums.DeviceStatus;
import io.github.bacelardev.iphoneresale.domain.enums.MaintenanceStatus;
import io.github.bacelardev.iphoneresale.domain.enums.RegistrationOrigin;
import io.github.bacelardev.iphoneresale.domain.model.BusinessInitialization;
import io.github.bacelardev.iphoneresale.domain.model.Device;
import io.github.bacelardev.iphoneresale.domain.model.DeviceColor;
import io.github.bacelardev.iphoneresale.domain.model.DevicePhoto;
import io.github.bacelardev.iphoneresale.domain.model.FinancialTransaction;
import io.github.bacelardev.iphoneresale.domain.model.IphoneModel;
import io.github.bacelardev.iphoneresale.infrastructure.persistence.repository.DeviceColorJpaRepository;
import io.github.bacelardev.iphoneresale.infrastructure.persistence.repository.DeviceJpaRepository;
import io.github.bacelardev.iphoneresale.infrastructure.persistence.repository.DevicePhotoJpaRepository;
import io.github.bacelardev.iphoneresale.infrastructure.persistence.repository.FinancialTransactionJpaRepository;
import io.github.bacelardev.iphoneresale.infrastructure.persistence.repository.IphoneModelJpaRepository;
import io.github.bacelardev.iphoneresale.infrastructure.persistence.repository.MaintenanceJpaRepository;
import io.github.bacelardev.iphoneresale.web.dto.common.CatalogReference;
import io.github.bacelardev.iphoneresale.web.dto.common.PageResponse;
import io.github.bacelardev.iphoneresale.web.dto.common.UserReference;
import io.github.bacelardev.iphoneresale.web.dto.device.ArchiveDeviceRequest;
import io.github.bacelardev.iphoneresale.web.dto.device.DeviceDetailResponse;
import io.github.bacelardev.iphoneresale.web.dto.device.DevicePhotoResponse;
import io.github.bacelardev.iphoneresale.web.dto.device.DeviceSummaryResponse;
import io.github.bacelardev.iphoneresale.web.dto.device.RegisterDeviceRequest;
import io.github.bacelardev.iphoneresale.web.dto.device.UpdateDeviceRequest;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class DeviceService {

    private static final Set<Integer> STORAGE_OPTIONS = Set.of(64, 128, 256, 512, 1024, 2048);
    private static final Map<String, String> SORT = Map.of(
            "internalCode", "internalCode", "purchasePrice", "purchasePrice",
            "purchasedAt", "purchasedAt", "createdAt", "createdAt", "updatedAt", "updatedAt"
    );

    private final DeviceJpaRepository devices;
    private final DevicePhotoJpaRepository photos;
    private final IphoneModelJpaRepository models;
    private final DeviceColorJpaRepository colors;
    private final FinancialTransactionJpaRepository transactions;
    private final MaintenanceJpaRepository maintenances;
    private final PhotoStorage storage;
    private final PhotoUrlSigner photoUrls;
    private final AuditService audit;
    private final BusinessInitializationService initializationService;
    private final Clock clock;

    public DeviceService(
            DeviceJpaRepository devices,
            DevicePhotoJpaRepository photos,
            IphoneModelJpaRepository models,
            DeviceColorJpaRepository colors,
            FinancialTransactionJpaRepository transactions,
            MaintenanceJpaRepository maintenances,
            PhotoStorage storage,
            PhotoUrlSigner photoUrls,
            AuditService audit,
            BusinessInitializationService initializationService,
            Clock clock
    ) {
        this.devices = devices;
        this.photos = photos;
        this.models = models;
        this.colors = colors;
        this.transactions = transactions;
        this.maintenances = maintenances;
        this.storage = storage;
        this.photoUrls = photoUrls;
        this.audit = audit;
        this.initializationService = initializationService;
        this.clock = clock;
    }

    @Transactional
    public DeviceDetailResponse createOperational(
            RegisterDeviceRequest request,
            List<MultipartFile> files
    ) {
        Device device = createDevice(request, RegistrationOrigin.OPERATIONAL, files);
        FinancialTransaction purchase = transactions.saveAndFlush(
                FinancialTransaction.devicePurchase(device));
        audit.record(AuditAction.CREATED, AuditedEntityType.DEVICE, device.getId(),
                device.getInternalCode(), "Aparelho " + device.getInternalCode() + " cadastrado.",
                Map.of("registrationOrigin", RegistrationOrigin.OPERATIONAL.name(),
                        "purchaseTransactionId", purchase.getId().toString()));
        return detail(device);
    }

    @Transactional
    public DeviceDetailResponse createInitialImport(
            RegisterDeviceRequest request,
            List<MultipartFile> files
    ) {
        BusinessInitialization initialization = initializationService.requiredForUpdate();
        if (initialization.getStatus() != BusinessInitializationStatus.PREPARING) {
            throw BusinessException.conflict("INITIAL_IMPORT_CLOSED",
                    "A importação inicial não está mais disponível.");
        }
        if (request.purchasedAt().isAfter(initialization.getCutoffAt())) {
            throw BusinessException.unprocessable("INITIAL_IMPORT_INVALID_DATE",
                    "A compra deve ser anterior ou igual à data de corte persistida.");
        }
        Device device = createDevice(request, RegistrationOrigin.INITIAL_IMPORT, files);
        audit.record(AuditAction.CREATED, AuditedEntityType.DEVICE, device.getId(),
                device.getInternalCode(),
                "Aparelho " + device.getInternalCode() + " importado como estoque inicial.",
                Map.of("registrationOrigin", RegistrationOrigin.INITIAL_IMPORT.name(),
                        "cutoffAt", initialization.getCutoffAt().toString()));
        return detail(device);
    }

    @Transactional(readOnly = true)
    public PageResponse<DeviceSummaryResponse> list(
            String search,
            List<DeviceStatus> statuses,
            UUID modelId,
            UUID colorId,
            Integer storageGb,
            Instant purchasedFrom,
            Instant purchasedTo,
            boolean archived,
            int page,
            int size,
            String sort
    ) {
        if (storageGb != null) validateStorage(storageGb);
        if (purchasedFrom != null && purchasedTo != null && !purchasedFrom.isBefore(purchasedTo)) {
            throw BusinessException.badRequest("VALIDATION_ERROR",
                    "purchasedFrom deve ser anterior a purchasedTo.");
        }
        Pageable pageable = PageableFactory.create(page, size, sort, SORT,
                "createdAt", Sort.Direction.DESC);
        return PageResponse.from(devices.findAll(filter(search, statuses, modelId, colorId,
                        storageGb, purchasedFrom, purchasedTo, archived), pageable),
                this::summary);
    }

    @Transactional(readOnly = true)
    public DeviceDetailResponse get(UUID id) {
        return detail(device(id));
    }

    @Transactional
    public DeviceDetailResponse update(UUID id, UpdateDeviceRequest request) {
        Device device = mutableDevice(id);
        VersionGuard.require(device.getVersion(), request.expectedVersion());
        IphoneModel model = request.modelId() == null ? device.getModel() : activeModel(request.modelId());
        DeviceColor color = request.colorId() == null ? device.getColor() : activeColor(request.colorId());
        int storageGb = request.storageGb() == null ? device.getStorageGb() : request.storageGb();
        validateStorage(storageGb);
        BigDecimal purchasePrice = request.purchasePrice() == null
                ? device.getPurchasePrice() : request.purchasePrice();
        Instant purchasedAt = request.purchasedAt() == null ? device.getPurchasedAt() : request.purchasedAt();
        boolean purchaseChanged = purchasePrice.compareTo(device.getPurchasePrice()) != 0
                || !purchasedAt.equals(device.getPurchasedAt());
        if (purchaseChanged && device.getStatus() == DeviceStatus.VENDIDO) {
            throw BusinessException.unprocessable("DEVICE_PURCHASE_LOCKED_BY_SALE",
                    "Preço e data de compra não podem mudar enquanto houver venda ativa.");
        }
        if (purchaseChanged && device.getRegistrationOrigin() == RegistrationOrigin.OPERATIONAL) {
            FinancialTransaction original = activePurchase(device.getId());
            transactions.saveAndFlush(FinancialTransaction.devicePurchaseReversal(
                    original, "Correção dos dados de compra do aparelho " + device.getInternalCode() + "."));
        }
        device.update(
                model, color, storageGb, purchasePrice, purchasedAt,
                request.faceIdWorking() == null ? device.isFaceIdWorking() : request.faceIdWorking(),
                request.originalScreen() == null ? device.isOriginalScreen() : request.originalScreen(),
                request.originalBattery() == null ? device.isOriginalBattery() : request.originalBattery(),
                request.batteryHealthPercent() == null
                        ? device.getBatteryHealthPercent() : request.batteryHealthPercent()
        );
        devices.flush();
        if (purchaseChanged && device.getRegistrationOrigin() == RegistrationOrigin.OPERATIONAL) {
            transactions.saveAndFlush(FinancialTransaction.devicePurchase(device));
        }
        audit.record(AuditAction.UPDATED, AuditedEntityType.DEVICE, device.getId(),
                device.getInternalCode(), "Aparelho " + device.getInternalCode() + " atualizado.",
                Map.of("purchaseDataChanged", purchaseChanged));
        return detail(device);
    }

    @Transactional
    public DeviceDetailResponse changeStatus(UUID id, long expectedVersion, DeviceStatus target) {
        Device device = mutableDevice(id);
        VersionGuard.require(device.getVersion(), expectedVersion);
        boolean allowed = device.getStatus() == DeviceStatus.PENDENTE_MANUTENCAO
                && target == DeviceStatus.DISPONIVEL_VENDA
                || device.getStatus() == DeviceStatus.DISPONIVEL_VENDA
                && target == DeviceStatus.PENDENTE_MANUTENCAO;
        if (!allowed) {
            throw BusinessException.unprocessable("INVALID_DEVICE_STATUS_TRANSITION",
                    "A transição de status solicitada não é permitida.");
        }
        DeviceStatus previous = device.getStatus();
        device.changeStatus(target);
        devices.flush();
        audit.record(AuditAction.STATUS_CHANGED, AuditedEntityType.DEVICE, id,
                device.getInternalCode(), "Status do aparelho " + device.getInternalCode() + " atualizado.",
                Map.of("from", previous.name(), "to", target.name()));
        return detail(device);
    }

    @Transactional
    public DeviceDetailResponse archive(UUID id, ArchiveDeviceRequest request) {
        Device device = devices.findByIdForUpdate(id).orElseThrow(() -> notFoundDevice());
        if (device.getArchivedAt() != null) {
            throw BusinessException.conflict("DEVICE_ALREADY_ARCHIVED", "O aparelho já está arquivado.");
        }
        VersionGuard.require(device.getVersion(), request.expectedVersion());
        if (device.getStatus() == DeviceStatus.VENDIDO) {
            throw BusinessException.unprocessable("DEVICE_HAS_ACTIVE_SALE",
                    "Cancele a venda antes de arquivar o aparelho.");
        }
        if (maintenances.existsByDeviceIdAndStatus(id, MaintenanceStatus.ACTIVE)) {
            throw BusinessException.unprocessable("DEVICE_HAS_ACTIVE_MAINTENANCE",
                    "Cancele as manutenções ativas antes de arquivar o aparelho.");
        }
        if (device.getRegistrationOrigin() == RegistrationOrigin.OPERATIONAL) {
            FinancialTransaction original = activePurchase(id);
            transactions.saveAndFlush(FinancialTransaction.devicePurchaseReversal(
                    original, "Arquivamento: " + request.reason().trim()));
        }
        device.archive(clock.instant(), audit.actor());
        devices.flush();
        audit.record(AuditAction.ARCHIVED, AuditedEntityType.DEVICE, id, device.getInternalCode(),
                "Aparelho " + device.getInternalCode() + " arquivado.",
                Map.of("reason", request.reason().trim()));
        return detail(device);
    }

    @Transactional
    public DevicePhotoResponse addPhoto(UUID deviceId, int position, MultipartFile file) {
        Device device = mutableDevice(deviceId);
        if (position < 1 || position > 4) {
            throw BusinessException.badRequest("VALIDATION_ERROR", "A posição deve ficar entre 1 e 4.");
        }
        long count = photos.countByDeviceIdAndRemovedAtIsNull(deviceId);
        if (count >= 4) {
            throw BusinessException.unprocessable("PHOTO_LIMIT_EXCEEDED",
                    "Um aparelho pode possuir no máximo quatro fotos ativas.");
        }
        if (photos.existsByDeviceIdAndPositionAndRemovedAtIsNull(deviceId, position)) {
            throw BusinessException.conflict("PHOTO_POSITION_OCCUPIED", "A posição já possui uma foto ativa.");
        }
        StoredPhoto stored = storeWithRollbackCompensation(deviceId, file);
        DevicePhoto photo = photos.saveAndFlush(new DevicePhoto(
                device, stored.storageKey(), stored.originalFilename(), stored.mimeType(),
                stored.sizeBytes(), position));
        audit.record(AuditAction.PHOTO_ADDED, AuditedEntityType.DEVICE_PHOTO, photo.getId(),
                device.getInternalCode(), "Foto adicionada ao aparelho " + device.getInternalCode() + ".",
                Map.of("deviceId", deviceId.toString(), "position", position));
        return photoResponse(photo);
    }

    @Transactional(readOnly = true)
    public List<DevicePhotoResponse> listPhotos(UUID deviceId) {
        device(deviceId);
        return activePhotos(deviceId).stream().map(this::photoResponse).toList();
    }

    @Transactional
    public void removePhoto(UUID deviceId, UUID photoId) {
        Device device = mutableDevice(deviceId);
        DevicePhoto photo = photos.findByIdAndDeviceIdAndRemovedAtIsNull(photoId, deviceId)
                .orElseThrow(() -> BusinessException.notFound("PHOTO_NOT_FOUND", "Foto não encontrada."));
        if (photos.countByDeviceIdAndRemovedAtIsNull(deviceId) <= 2) {
            throw BusinessException.unprocessable("PHOTO_MINIMUM_VIOLATION",
                    "Um aparelho ativo deve manter ao menos duas fotos.");
        }
        photo.remove(clock.instant());
        photos.flush();
        audit.record(AuditAction.PHOTO_REMOVED, AuditedEntityType.DEVICE_PHOTO, photoId,
                device.getInternalCode(), "Foto removida do aparelho " + device.getInternalCode() + ".",
                Map.of("deviceId", deviceId.toString(), "position", photo.getPosition()));
    }

    private Device createDevice(
            RegisterDeviceRequest request,
            RegistrationOrigin origin,
            List<MultipartFile> files
    ) {
        validateNewDevice(request, files);
        Device device = devices.saveAndFlush(new Device(
                activeModel(request.modelId()), activeColor(request.colorId()), request.storageGb(),
                request.purchasePrice(), request.purchasedAt(), request.faceIdWorking(),
                request.originalScreen(), request.originalBattery(), request.batteryHealthPercent(),
                request.initialStatus(), origin
        ));
        List<DevicePhoto> metadata = new ArrayList<>();
        for (int index = 0; index < files.size(); index++) {
            StoredPhoto stored = storeWithRollbackCompensation(device.getId(), files.get(index));
            metadata.add(new DevicePhoto(device, stored.storageKey(), stored.originalFilename(),
                    stored.mimeType(), stored.sizeBytes(), index + 1));
        }
        photos.saveAllAndFlush(metadata);
        return device;
    }

    private void validateNewDevice(RegisterDeviceRequest request, List<MultipartFile> files) {
        validateStorage(request.storageGb());
        if (request.initialStatus() == DeviceStatus.VENDIDO) {
            throw BusinessException.unprocessable("INVALID_DEVICE_STATUS",
                    "O status vendido só pode ser definido pelo registro de uma venda.");
        }
        int count = files == null ? 0 : files.size();
        if (count < 2) {
            throw BusinessException.unprocessable("PHOTO_MINIMUM_VIOLATION",
                    "Informe ao menos duas fotos.");
        }
        if (count > 4) {
            throw BusinessException.unprocessable("PHOTO_LIMIT_EXCEEDED",
                    "Informe no máximo quatro fotos.");
        }
    }

    private StoredPhoto storeWithRollbackCompensation(UUID deviceId, MultipartFile file) {
        StoredPhoto stored = storage.store(deviceId, file);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) storage.delete(stored.storageKey());
            }
        });
        return stored;
    }

    private Device mutableDevice(UUID id) {
        Device device = devices.findByIdForUpdate(id).orElseThrow(() -> notFoundDevice());
        if (device.getArchivedAt() != null) {
            throw BusinessException.unprocessable("DEVICE_ARCHIVED", "O aparelho está arquivado.");
        }
        return device;
    }

    private Device device(UUID id) {
        return devices.findById(id).orElseThrow(() -> notFoundDevice());
    }

    private FinancialTransaction activePurchase(UUID deviceId) {
        return transactions.findActiveDevicePurchase(deviceId).orElseThrow(() ->
                BusinessException.conflict("DEVICE_PURCHASE_LEDGER_MISSING",
                        "O lançamento de compra ativo do aparelho não foi encontrado."));
    }

    private IphoneModel activeModel(UUID id) {
        IphoneModel model = models.findById(id).orElseThrow(() ->
                BusinessException.notFound("MODEL_NOT_FOUND", "Modelo não encontrado."));
        if (!model.isActive()) inactiveCatalog();
        return model;
    }

    private DeviceColor activeColor(UUID id) {
        DeviceColor color = colors.findById(id).orElseThrow(() ->
                BusinessException.notFound("COLOR_NOT_FOUND", "Cor não encontrada."));
        if (!color.isActive()) inactiveCatalog();
        return color;
    }

    private static void inactiveCatalog() {
        throw BusinessException.unprocessable("CATALOG_ITEM_INACTIVE",
                "O item de catálogo selecionado está inativo.");
    }

    private static void validateStorage(int storageGb) {
        if (!STORAGE_OPTIONS.contains(storageGb)) {
            throw BusinessException.badRequest("VALIDATION_ERROR", "Capacidade de armazenamento inválida.");
        }
    }

    private DeviceSummaryResponse summary(Device device) {
        BigDecimal maintenanceTotal = maintenanceTotal(device.getId());
        List<DevicePhoto> activePhotos = activePhotos(device.getId());
        String cover = activePhotos.isEmpty() ? null : photoUrls.sign(activePhotos.getFirst().getId());
        return new DeviceSummaryResponse(
                device.getId(), device.getInternalCode(), catalog(device.getModel()), catalog(device.getColor()),
                device.getStorageGb(), device.getPurchasePrice(), maintenanceTotal,
                device.getPurchasePrice().add(maintenanceTotal), device.getPurchasedAt(), device.getStatus(),
                device.getRegistrationOrigin(), device.getArchivedAt() != null, cover,
                device.getUpdatedAt(), version(device)
        );
    }

    private DeviceDetailResponse detail(Device device) {
        BigDecimal maintenanceTotal = maintenanceTotal(device.getId());
        return new DeviceDetailResponse(
                device.getId(), device.getInternalCode(), catalog(device.getModel()), catalog(device.getColor()),
                device.getStorageGb(), device.getPurchasePrice(), maintenanceTotal,
                device.getPurchasePrice().add(maintenanceTotal), device.getPurchasedAt(), device.getStatus(),
                device.getRegistrationOrigin(), device.getArchivedAt() != null, device.isFaceIdWorking(),
                device.isOriginalScreen(), device.isOriginalBattery(),
                device.getBatteryHealthPercent() == 0 ? null : device.getBatteryHealthPercent(),
                activePhotos(device.getId()).stream().map(this::photoResponse).toList(),
                device.getCreatedAt(), UserReference.from(device.getCreatedBy()), device.getUpdatedAt(),
                UserReference.from(device.getUpdatedBy()), device.getArchivedAt(),
                UserReference.from(device.getArchivedBy()), version(device)
        );
    }

    private DevicePhotoResponse photoResponse(DevicePhoto photo) {
        return DevicePhotoResponse.from(photo, photoUrls.sign(photo.getId()));
    }

    private List<DevicePhoto> activePhotos(UUID deviceId) {
        return photos.findByDeviceIdAndRemovedAtIsNullOrderByPositionAsc(deviceId);
    }

    private BigDecimal maintenanceTotal(UUID deviceId) {
        BigDecimal total = maintenances.sumActiveCostByDeviceId(deviceId);
        return total == null ? BigDecimal.ZERO.setScale(2) : total;
    }

    private static CatalogReference catalog(IphoneModel model) {
        return new CatalogReference(model.getId(), model.getCode(), model.getName());
    }

    private static CatalogReference catalog(DeviceColor color) {
        return new CatalogReference(color.getId(), color.getCode(), color.getName());
    }

    private static long version(Device device) {
        return device.getVersion() == null ? 0 : device.getVersion();
    }

    private static BusinessException notFoundDevice() {
        return BusinessException.notFound("DEVICE_NOT_FOUND", "Aparelho não encontrado.");
    }

    private static Specification<Device> filter(
            String search,
            List<DeviceStatus> statuses,
            UUID modelId,
            UUID colorId,
            Integer storageGb,
            Instant purchasedFrom,
            Instant purchasedTo,
            boolean archived
    ) {
        return (root, query, cb) -> {
            var predicates = new ArrayList<jakarta.persistence.criteria.Predicate>();
            if (search != null && !search.isBlank()) {
                String like = "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
                var model = root.join("model", JoinType.INNER);
                var color = root.join("color", JoinType.INNER);
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("internalCode")), like),
                        cb.like(cb.lower(model.get("name")), like),
                        cb.like(cb.lower(color.get("name")), like)
                ));
            }
            if (statuses != null && !statuses.isEmpty()) predicates.add(root.get("status").in(statuses));
            if (modelId != null) predicates.add(cb.equal(root.get("model").get("id"), modelId));
            if (colorId != null) predicates.add(cb.equal(root.get("color").get("id"), colorId));
            if (storageGb != null) predicates.add(cb.equal(root.get("storageGb"), storageGb));
            if (purchasedFrom != null) predicates.add(cb.greaterThanOrEqualTo(root.get("purchasedAt"), purchasedFrom));
            if (purchasedTo != null) predicates.add(cb.lessThan(root.get("purchasedAt"), purchasedTo));
            predicates.add(archived ? cb.isNotNull(root.get("archivedAt")) : cb.isNull(root.get("archivedAt")));
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }
}
