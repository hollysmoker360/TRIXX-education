package com.schoolsphere.sync.web;

import com.schoolsphere.sync.internal.SyncService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Offline sync endpoints (spec 10.2, 10.3, 10.7). */
@RestController
@RequestMapping("/api/v1/sync")
public class SyncController {

    private final SyncService syncService;

    public SyncController(SyncService syncService) {
        this.syncService = syncService;
    }

    @PostMapping("/push")
    @PreAuthorize("@policy.can('school.view')")
    public SyncService.PushResult push(@Valid @RequestBody PushRequest request) {
        return syncService.push(request.deviceId(), request.batchId(), request.operations().stream()
                .map(op -> new SyncService.OperationInput(op.opId(), op.entity(), op.entityId(), op.type(),
                        op.baseVersion(), op.fields(), op.clientTime()))
                .toList());
    }

    @GetMapping("/pull")
    @PreAuthorize("@policy.can('school.view')")
    public SyncService.PullResult pull(@RequestParam(required = false) Long cursor,
                                       @RequestParam(required = false) Integer limit,
                                       @RequestParam(required = false) String deviceId) {
        return syncService.pull(cursor, limit, deviceId);
    }

    @PostMapping("/devices")
    @PreAuthorize("@policy.can('school.view')")
    public SyncService.DeviceView registerDevice(@Valid @RequestBody DeviceRequest request) {
        return syncService.registerDevice(request.deviceId(), request.name(), request.platform());
    }

    @GetMapping("/devices")
    @PreAuthorize("@policy.can('school.view')")
    public List<SyncService.DeviceView> listDevices() {
        return syncService.listDevices();
    }

    @DeleteMapping("/devices/{id}")
    @PreAuthorize("@policy.can('sync.device.manage')")
    public SyncService.DeviceView revokeDevice(@PathVariable UUID id) {
        return syncService.revokeDevice(id);
    }

    public record PushRequest(String deviceId, UUID batchId,
                              @NotNull List<@Valid Operation> operations) {
    }

    public record Operation(@NotNull UUID opId, @NotBlank String entity, UUID entityId, @NotBlank String type,
                            Integer baseVersion, java.util.Map<String, Object> fields,
                            java.time.Instant clientTime) {
    }

    public record DeviceRequest(@NotBlank String deviceId, String name, String platform) {
    }
}
