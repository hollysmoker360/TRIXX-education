package com.schoolsphere.messaging.web;

import com.schoolsphere.messaging.api.MessagingApi;
import com.schoolsphere.messaging.internal.MessagingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/chat")
public class MessagingController {

    private final MessagingService messagingService;

    public MessagingController(MessagingService messagingService) {
        this.messagingService = messagingService;
    }

    // ----- channels -----

    @GetMapping("/channels")
    @PreAuthorize("@policy.can('chat.use')")
    public List<MessagingService.ChannelSummary> channels() {
        return messagingService.listChannels();
    }

    @PostMapping("/channels")
    @PreAuthorize("@policy.can('chat.use')")
    public MessagingService.ChannelSummary createChannel(@Valid @RequestBody ChannelRequest request) {
        return messagingService.createChannel(request.channelType(), request.name(), request.departmentId(),
                request.classGroupId(), request.subjectId(), Boolean.TRUE.equals(request.staffOnly()),
                request.memberMembershipIds());
    }

    @GetMapping("/channels/{id}/members")
    @PreAuthorize("@policy.can('chat.use')")
    public List<MessagingService.MemberView> members(@PathVariable UUID id) {
        return messagingService.listMembers(id);
    }

    @PostMapping("/channels/{id}/members")
    @PreAuthorize("@policy.can('chat.use')")
    public List<MessagingService.MemberView> addMembers(@PathVariable UUID id,
                                                        @Valid @RequestBody MembersRequest request) {
        return messagingService.addMembers(id, request.membershipIds());
    }

    @DeleteMapping("/channels/{id}/members/{membershipId}")
    @PreAuthorize("@policy.can('chat.use')")
    public void removeMember(@PathVariable UUID id, @PathVariable UUID membershipId) {
        messagingService.removeMember(id, membershipId);
    }

    @PostMapping("/channels/{id}/archive")
    @PreAuthorize("@policy.can('chat.use')")
    public MessagingService.ChannelSummary archive(@PathVariable UUID id) {
        return messagingService.archiveChannel(id);
    }

    // ----- messages -----

    @GetMapping("/channels/{id}/messages")
    @PreAuthorize("@policy.can('chat.use')")
    public MessagingService.MessagePage messages(@PathVariable UUID id,
                                                 @RequestParam(required = false) Long afterSeq,
                                                 @RequestParam(required = false) Long beforeSeq,
                                                 @RequestParam(defaultValue = "50") int limit) {
        return messagingService.listMessages(id, afterSeq, beforeSeq, limit);
    }

    @PostMapping("/channels/{id}/messages")
    @PreAuthorize("@policy.can('chat.use')")
    public MessagingApi.MessageView send(@PathVariable UUID id, @Valid @RequestBody SendRequest request) {
        return messagingService.sendMessage(new MessagingApi.MessageInput(null, request.clientId(), id,
                request.body(), request.replyToId(), request.attachments())).message();
    }

    @PatchMapping("/messages/{id}")
    @PreAuthorize("@policy.can('chat.use')")
    public MessagingApi.MessageView edit(@PathVariable UUID id, @Valid @RequestBody EditRequest request) {
        return messagingService.editMessage(id, request.body());
    }

    @DeleteMapping("/messages/{id}")
    @PreAuthorize("@policy.can('chat.use')")
    public MessagingApi.MessageView delete(@PathVariable UUID id) {
        return messagingService.deleteMessage(id);
    }

    @PostMapping("/messages/{id}/report")
    @PreAuthorize("@policy.can('chat.use')")
    public MessagingApi.MessageView report(@PathVariable UUID id, @RequestBody(required = false) ReportRequest request) {
        return messagingService.reportMessage(id, request == null ? null : request.reason());
    }

    @GetMapping("/reported")
    @PreAuthorize("@policy.can('chat.moderate')")
    public List<MessagingApi.MessageView> reported(@RequestParam(defaultValue = "50") int limit) {
        return messagingService.listReported(limit);
    }

    @PostMapping("/channels/{id}/read")
    @PreAuthorize("@policy.can('chat.use')")
    public Map<String, Long> markRead(@PathVariable UUID id, @Valid @RequestBody ReadRequest request) {
        return Map.of("lastReadSeq", messagingService.markRead(id, request.seq()));
    }

    @GetMapping("/search")
    @PreAuthorize("@policy.can('chat.use')")
    public List<MessagingApi.MessageView> search(@RequestParam String q,
                                                 @RequestParam(required = false) UUID channelId,
                                                 @RequestParam(defaultValue = "50") int limit) {
        return messagingService.search(q, channelId, limit);
    }

    public record ChannelRequest(@NotBlank String channelType, @NotBlank String name, UUID departmentId,
                                 UUID classGroupId, UUID subjectId, Boolean staffOnly,
                                 List<UUID> memberMembershipIds) {
    }

    public record MembersRequest(@NotNull List<UUID> membershipIds) {
    }

    public record SendRequest(UUID clientId, @NotBlank String body, UUID replyToId,
                              List<Map<String, Object>> attachments) {
    }

    public record EditRequest(@NotBlank String body) {
    }

    public record ReportRequest(String reason) {
    }

    public record ReadRequest(long seq) {
    }
}
