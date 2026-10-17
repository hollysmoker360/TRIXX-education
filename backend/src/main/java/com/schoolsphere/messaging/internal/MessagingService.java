package com.schoolsphere.messaging.internal;

import com.schoolsphere.auth.api.UserDirectory;
import com.schoolsphere.common.error.ApiException;
import com.schoolsphere.common.security.SecurityUtils;
import com.schoolsphere.common.tenant.TenantContext;
import com.schoolsphere.common.tenant.TenantScoped;
import com.schoolsphere.messaging.api.MessagingApi;
import com.schoolsphere.people.api.PeopleApi;
import com.schoolsphere.tenants.api.PermissionCatalog;
import com.schoolsphere.tenants.api.TenantsApi;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

/**
 * Channels and messages (spec 11.4): cursor history by server sequence, unread counts, read
 * receipts, full-text search scoped to the member's channels, report/moderation, and the
 * idempotent append used by the sync engine.
 */
@Service
@TenantScoped
public class MessagingService implements MessagingApi {

    private static final int MAX_BODY_LENGTH = 8000;
    private static final int MAX_ATTACHMENTS = 10;
    private static final List<String> CHANNEL_TYPES = List.of("department", "class", "subject", "direct");
    private static final Set<String> STAFF_ROLES = Set.of(
            PermissionCatalog.ROLE_OWNER, PermissionCatalog.ROLE_PRINCIPAL, PermissionCatalog.ROLE_ADMIN,
            PermissionCatalog.ROLE_DEPT_HEAD, PermissionCatalog.ROLE_TEACHER, PermissionCatalog.ROLE_BURSAR,
            PermissionCatalog.ROLE_LIBRARIAN, PermissionCatalog.ROLE_IT);

    private final ChannelRepository channelRepository;
    private final ChannelMemberRepository channelMemberRepository;
    private final MessageRepository messageRepository;
    private final TenantsApi tenantsApi;
    private final PeopleApi peopleApi;
    private final UserDirectory userDirectory;
    private final ApplicationEventPublisher eventPublisher;
    private final Duration editWindow;

    public MessagingService(ChannelRepository channelRepository,
                            ChannelMemberRepository channelMemberRepository,
                            MessageRepository messageRepository,
                            TenantsApi tenantsApi,
                            PeopleApi peopleApi,
                            UserDirectory userDirectory,
                            ApplicationEventPublisher eventPublisher,
                            @Value("${schoolsphere.chat.edit-window-minutes:15}") long editWindowMinutes) {
        this.channelRepository = channelRepository;
        this.channelMemberRepository = channelMemberRepository;
        this.messageRepository = messageRepository;
        this.tenantsApi = tenantsApi;
        this.peopleApi = peopleApi;
        this.userDirectory = userDirectory;
        this.eventPublisher = eventPublisher;
        this.editWindow = Duration.ofMinutes(editWindowMinutes);
    }

    // ----- channels -----

    public List<ChannelSummary> listChannels() {
        UUID schoolId = schoolId();
        UUID membershipId = membershipId();
        List<ChannelMember> mine = channelMemberRepository.findBySchoolIdAndMembershipId(schoolId, membershipId);
        if (mine.isEmpty()) {
            return List.of();
        }
        Map<UUID, ChannelMember> memberByChannel = mine.stream()
                .collect(Collectors.toMap(ChannelMember::getChannelId, Function.identity(), (a, b) -> a));
        List<Channel> channels = channelRepository
                .findBySchoolIdAndIdInAndDeletedAtIsNullOrderByNameAsc(schoolId, memberByChannel.keySet());
        Map<UUID, MessageRepository.LastMessageRow> latest = messageRepository
                .findLatestPerChannel(memberByChannel.keySet()).stream()
                .collect(Collectors.toMap(MessageRepository.LastMessageRow::getChannelId, Function.identity(),
                        (a, b) -> a));
        Map<UUID, Long> unread = messageRepository
                .countUnreadPerChannel(membershipId, memberByChannel.keySet()).stream()
                .collect(Collectors.toMap(MessageRepository.UnreadRow::getChannelId,
                        MessageRepository.UnreadRow::getUnread, (a, b) -> a));
        Map<UUID, String> names = resolveNames(latest.values().stream()
                .map(MessageRepository.LastMessageRow::getSenderMembershipId).toList());
        List<ChannelSummary> result = new ArrayList<>();
        for (Channel channel : channels) {
            ChannelMember member = memberByChannel.get(channel.getId());
            MessageRepository.LastMessageRow last = latest.get(channel.getId());
            result.add(new ChannelSummary(
                    channel.getId(), channel.getChannelType(), channel.getName(), channel.isStaffOnly(),
                    channel.getDepartmentId(), channel.getClassGroupId(), channel.getSubjectId(),
                    member.getMemberRole(), member.getLastReadSeq(), unread.getOrDefault(channel.getId(), 0L),
                    last == null ? null : new MessagePreview(last.getSeq(), last.getId(), last.getBody(),
                            names.get(last.getSenderMembershipId()), last.getSentAt()),
                    channel.getArchivedAt() != null));
        }
        return result;
    }

    public ChannelSummary createChannel(String channelType, String name, UUID departmentId, UUID classGroupId,
                                        UUID subjectId, boolean staffOnly, List<UUID> memberMembershipIds) {
        UUID schoolId = schoolId();
        if (!CHANNEL_TYPES.contains(channelType)) {
            throw ApiException.badRequest("invalid_channel_type", "Unknown channel type.");
        }
        if (name == null || name.isBlank()) {
            throw ApiException.badRequest("name_required", "A channel name is required.");
        }
        if ("class".equals(channelType) && classGroupId != null
                && channelRepository.existsBySchoolIdAndClassGroupIdAndChannelTypeAndDeletedAtIsNull(schoolId,
                classGroupId, "class")) {
            throw ApiException.conflict("channel_exists", "A channel already exists for this class.");
        }
        if ("direct".equals(channelType)) {
            Map<String, Object> settings = tenantsApi.schoolSettings(schoolId);
            boolean enabled = Boolean.TRUE.equals(settings.get("chat.directMessagesEnabled"));
            if (!enabled && !hasPermission(PermissionCatalog.CHAT_MODERATE)) {
                throw ApiException.forbidden("direct_messages_disabled",
                        "Direct messages are disabled by school policy.");
            }
        }
        Channel channel = new Channel();
        channel.setSchoolId(schoolId);
        channel.setChannelType(channelType);
        channel.setName(name.trim());
        channel.setDepartmentId(departmentId);
        channel.setClassGroupId(classGroupId);
        channel.setSubjectId(subjectId);
        channel.setStaffOnly(staffOnly || "department".equals(channelType));
        channel.setCreatedBy(actorUserId());
        channel = channelRepository.save(channel);
        addMemberRow(channel, membershipId(), "moderator");
        if (memberMembershipIds != null) {
            for (UUID id : memberMembershipIds) {
                addMember(channel, id);
            }
        }
        if ("class".equals(channelType) && classGroupId != null) {
            Channel classChannel = channel;
            for (PeopleApi.StudentRef student : peopleApi.listStudentsByClass(schoolId, classGroupId, null)) {
                tenantsApi.resolveMembership(student.userId(), schoolId)
                        .ifPresent(m -> addMember(classChannel, m.membershipId()));
            }
        }
        return new ChannelSummary(channel.getId(), channel.getChannelType(), channel.getName(),
                channel.isStaffOnly(), channel.getDepartmentId(), channel.getClassGroupId(), channel.getSubjectId(),
                "moderator", 0L, 0L, null, false);
    }

    public List<MemberView> listMembers(UUID channelId) {
        requireChannelMember(channelId);
        List<ChannelMember> members = channelMemberRepository.findBySchoolIdAndChannelId(schoolId(), channelId);
        Map<UUID, String> names = resolveNames(members.stream().map(ChannelMember::getMembershipId).toList());
        return members.stream()
                .map(m -> new MemberView(m.getMembershipId(), names.get(m.getMembershipId()), m.getMemberRole(),
                        m.getLastReadSeq()))
                .toList();
    }

    public List<MemberView> addMembers(UUID channelId, List<UUID> membershipIds) {
        Channel channel = requireChannel(channelId);
        requireModerator(channelId);
        if (membershipIds != null) {
            for (UUID id : membershipIds) {
                addMember(channel, id);
            }
        }
        return listMembers(channelId);
    }

    public void removeMember(UUID channelId, UUID membershipId) {
        requireModerator(channelId);
        channelMemberRepository.findBySchoolIdAndChannelIdAndMembershipId(schoolId(), channelId, membershipId)
                .ifPresent(channelMemberRepository::delete);
    }

    public ChannelSummary archiveChannel(UUID channelId) {
        Channel channel = requireChannel(channelId);
        ChannelMember member = requireChannelMember(channelId);
        boolean creator = actorUserId() != null && actorUserId().equals(channel.getCreatedBy());
        if (!creator && !hasPermission(PermissionCatalog.CHAT_MODERATE)) {
            throw ApiException.forbidden("not_moderator", "Only the channel creator or a moderator may archive it.");
        }
        channel.setArchivedAt(Instant.now());
        channel.setUpdatedBy(actorUserId());
        channelRepository.save(channel);
        return new ChannelSummary(channel.getId(), channel.getChannelType(), channel.getName(), channel.isStaffOnly(),
                channel.getDepartmentId(), channel.getClassGroupId(), channel.getSubjectId(), member.getMemberRole(),
                member.getLastReadSeq(), 0L, null, true);
    }

    // ----- messages -----

    public MessagePage listMessages(UUID channelId, Long afterSeq, Long beforeSeq, int limit) {
        requireChannelMember(channelId);
        int size = Math.max(1, Math.min(limit <= 0 ? 50 : limit, 200));
        List<Message> rows;
        if (beforeSeq != null) {
            List<Message> descending = messageRepository
                    .findByChannelIdAndSeqLessThanOrderBySeqDesc(channelId, beforeSeq, PageRequest.of(0, size + 1));
            boolean hasMore = descending.size() > size;
            rows = new ArrayList<>(hasMore ? descending.subList(0, size) : descending);
            java.util.Collections.reverse(rows);
            return toPage(rows, hasMore);
        }
        long cursor = afterSeq == null ? 0L : afterSeq;
        List<Message> ascending = messageRepository
                .findByChannelIdAndSeqGreaterThanOrderBySeqAsc(channelId, cursor, PageRequest.of(0, size + 1));
        boolean hasMore = ascending.size() > size;
        rows = hasMore ? ascending.subList(0, size) : ascending;
        return toPage(rows, hasMore);
    }

    @Override
    public SendOutcome sendMessage(MessageInput input) {
        if (input.body() == null || input.body().isBlank()) {
            throw ApiException.badRequest("empty_message", "A message body is required.");
        }
        if (input.body().length() > MAX_BODY_LENGTH) {
            throw ApiException.badRequest("message_too_long", "The message exceeds the maximum length.");
        }
        List<Map<String, Object>> attachments = input.attachments() == null ? List.of() : input.attachments();
        if (attachments.size() > MAX_ATTACHMENTS) {
            throw ApiException.badRequest("too_many_attachments", "Too many attachments.");
        }
        UUID schoolId = schoolId();
        Channel channel = requireChannel(input.channelId());
        requireChannelMember(input.channelId());
        if (channel.getArchivedAt() != null) {
            throw ApiException.conflict("channel_archived", "This channel is archived.");
        }
        if (input.clientId() != null) {
            var existing = messageRepository.findByChannelIdAndClientId(input.channelId(), input.clientId());
            if (existing.isPresent()) {
                return new SendOutcome(toView(existing.get()), false);
            }
        }
        if (input.id() != null) {
            var existing = messageRepository.findBySchoolIdAndId(schoolId, input.id());
            if (existing.isPresent()) {
                return new SendOutcome(toView(existing.get()), false);
            }
        }
        if (input.replyToId() != null) {
            Message parent = requireMessage(input.replyToId());
            if (!parent.getChannelId().equals(input.channelId())) {
                throw ApiException.badRequest("invalid_reply", "The referenced message is in another channel.");
            }
        }
        Message message = new Message();
        message.setId(input.id());
        message.setSchoolId(schoolId);
        message.setChannelId(input.channelId());
        message.setClientId(input.clientId());
        message.setSenderMembershipId(membershipId());
        message.setBody(input.body().trim());
        message.setReplyToId(input.replyToId());
        message.setAttachments(new ArrayList<>(attachments));
        try {
            message = messageRepository.saveAndFlush(message);
        } catch (DataIntegrityViolationException e) {
            Message existing = messageRepository.findByChannelIdAndClientId(input.channelId(), input.clientId())
                    .orElseThrow(() -> e);
            return new SendOutcome(toView(existing), false);
        }
        MessageView view = toView(message);
        eventPublisher.publishEvent(new ChatEvents.MessageChanged(schoolId, message.getChannelId(), view));
        return new SendOutcome(view, true);
    }

    public MessageView editMessage(UUID messageId, String newBody) {
        if (newBody == null || newBody.isBlank()) {
            throw ApiException.badRequest("empty_message", "A message body is required.");
        }
        if (newBody.length() > MAX_BODY_LENGTH) {
            throw ApiException.badRequest("message_too_long", "The message exceeds the maximum length.");
        }
        Message message = requireMessage(messageId);
        if (message.getDeletedAt() != null) {
            throw ApiException.conflict("message_deleted", "The message has been deleted.");
        }
        if (!message.getSenderMembershipId().equals(membershipId())) {
            throw ApiException.forbidden("not_sender", "Only the sender can edit a message.");
        }
        if (message.getEditedAt() == null && message.getSentAt().plus(editWindow).isBefore(Instant.now())) {
            throw ApiException.forbidden("edit_window_passed", "The edit window for this message has passed.");
        }
        message.setBody(newBody.trim());
        message.setEditedAt(Instant.now());
        message = messageRepository.save(message);
        MessageView view = toView(message);
        eventPublisher.publishEvent(new ChatEvents.MessageChanged(schoolId(), message.getChannelId(), view));
        return view;
    }

    public MessageView deleteMessage(UUID messageId) {
        Message message = requireMessage(messageId);
        if (message.getDeletedAt() != null) {
            return toView(message);
        }
        boolean sender = message.getSenderMembershipId().equals(membershipId());
        if (!sender && !hasPermission(PermissionCatalog.CHAT_MODERATE)) {
            throw ApiException.forbidden("not_allowed", "Only the sender or a moderator can delete this message.");
        }
        message.setDeletedAt(Instant.now());
        message.setDeletedByMembershipId(membershipId());
        message = messageRepository.save(message);
        MessageView view = toView(message);
        eventPublisher.publishEvent(new ChatEvents.MessageChanged(schoolId(), message.getChannelId(), view));
        return view;
    }

    public MessageView reportMessage(UUID messageId, String reason) {
        Message message = requireMessage(messageId);
        if (message.getDeletedAt() != null) {
            throw ApiException.conflict("message_deleted", "The message has been deleted.");
        }
        if (!hasPermission(PermissionCatalog.CHAT_MODERATE)) {
            requireChannelMember(message.getChannelId());
        }
        message.setReportedAt(Instant.now());
        message.setReportReason(reason);
        return toView(messageRepository.save(message));
    }

    public List<MessageView> listReported(int limit) {
        int size = Math.max(1, Math.min(limit <= 0 ? 50 : limit, 200));
        List<Message> messages = messageRepository.findBySchoolIdAndReportedAtIsNotNullOrderByReportedAtDesc(
                schoolId(), PageRequest.of(0, size));
        Map<UUID, String> names = resolveNames(messages.stream().map(Message::getSenderMembershipId).toList());
        return messages.stream().map(m -> toView(m, names.get(m.getSenderMembershipId()))).toList();
    }

    public long markRead(UUID channelId, long seq) {
        ChannelMember member = requireChannelMember(channelId);
        if (seq > member.getLastReadSeq()) {
            member.setLastReadSeq(seq);
            channelMemberRepository.save(member);
        }
        return member.getLastReadSeq();
    }

    public List<MessageView> search(String query, UUID channelId, int limit) {
        if (query == null || query.isBlank()) {
            throw ApiException.badRequest("query_required", "A search query is required.");
        }
        if (channelId != null) {
            requireChannelMember(channelId);
        }
        int size = Math.max(1, Math.min(limit <= 0 ? 50 : limit, 100));
        List<Message> messages = messageRepository.search(schoolId(), channelId, query.trim(), membershipId(), size);
        Map<UUID, String> names = resolveNames(messages.stream().map(Message::getSenderMembershipId).toList());
        return messages.stream().map(m -> toView(m, names.get(m.getSenderMembershipId()))).toList();
    }

    /** Membership check used by the WebSocket authorizer (spec 7.4). */
    public boolean isMember(UUID channelId, UUID membershipId) {
        return channelMemberRepository.findBySchoolIdAndChannelIdAndMembershipId(schoolId(), channelId, membershipId)
                .isPresent();
    }

    // ----- helpers -----

    private Channel requireChannel(UUID channelId) {
        return channelRepository.findBySchoolIdAndIdAndDeletedAtIsNull(schoolId(), channelId)
                .orElseThrow(() -> ApiException.notFound("channel_not_found", "Channel not found."));
    }

    private ChannelMember requireChannelMember(UUID channelId) {
        return channelMemberRepository
                .findBySchoolIdAndChannelIdAndMembershipId(schoolId(), channelId, membershipId())
                .orElseThrow(() -> ApiException.forbidden("not_channel_member",
                        "You are not a member of this channel."));
    }

    private void requireModerator(UUID channelId) {
        ChannelMember member = requireChannelMember(channelId);
        if (!"moderator".equals(member.getMemberRole())
                && !hasPermission(PermissionCatalog.CHAT_MODERATE)) {
            throw ApiException.forbidden("not_moderator", "Only moderators may manage channel membership.");
        }
    }

    private Message requireMessage(UUID messageId) {
        return messageRepository.findBySchoolIdAndId(schoolId(), messageId)
                .orElseThrow(() -> ApiException.notFound("message_not_found", "Message not found."));
    }

    private void addMember(Channel channel, UUID membershipId) {
        if (membershipId == null) {
            return;
        }
        var membership = tenantsApi.requireMembership(membershipId)
                .filter(m -> m.schoolId().equals(channel.getSchoolId()) && "active".equals(m.status()))
                .orElseThrow(() -> ApiException.badRequest("invalid_member",
                        "The selected member is not an active member of this school."));
        if (channel.isStaffOnly() && membership.roles().stream().noneMatch(STAFF_ROLES::contains)) {
            throw ApiException.forbidden("staff_only", "This channel is limited to staff.");
        }
        addMemberRow(channel, membershipId, "member");
    }

    private void addMemberRow(Channel channel, UUID membershipId, String role) {
        if (channelMemberRepository
                .findBySchoolIdAndChannelIdAndMembershipId(channel.getSchoolId(), channel.getId(), membershipId)
                .isPresent()) {
            return;
        }
        ChannelMember member = new ChannelMember();
        member.setSchoolId(channel.getSchoolId());
        member.setChannelId(channel.getId());
        member.setMembershipId(membershipId);
        member.setMemberRole(role);
        channelMemberRepository.save(member);
    }

    private Map<UUID, String> resolveNames(Collection<UUID> membershipIds) {
        Set<UUID> distinct = new LinkedHashSet<>(membershipIds);
        Map<UUID, String> result = new HashMap<>();
        for (UUID membershipId : distinct) {
            if (membershipId == null) {
                continue;
            }
            String name = tenantsApi.requireMembership(membershipId)
                    .flatMap(m -> userDirectory.findById(m.userId()))
                    .map(UserDirectory.UserRef::displayName)
                    .orElse(null);
            if (name != null) {
                result.put(membershipId, name);
            }
        }
        return result;
    }

    private MessagePage toPage(List<Message> rows, boolean hasMore) {
        Map<UUID, String> names = resolveNames(rows.stream().map(Message::getSenderMembershipId).toList());
        List<MessageView> views = rows.stream()
                .map(m -> toView(m, names.get(m.getSenderMembershipId())))
                .toList();
        Long lastSeq = rows.isEmpty() ? null : rows.get(rows.size() - 1).getSeq();
        return new MessagePage(views, hasMore, lastSeq);
    }

    private MessageView toView(Message message) {
        return toView(message, resolveNames(List.of(message.getSenderMembershipId()))
                .get(message.getSenderMembershipId()));
    }

    private MessageView toView(Message message, String senderName) {
        return new MessageView(message.getId(), message.getChannelId(), message.getClientId(),
                message.getSenderMembershipId(), senderName, message.getBody(), message.getReplyToId(),
                message.getAttachments(), message.getSeq(), message.getSentAt(), message.getEditedAt(),
                message.getDeletedAt());
    }

    private boolean hasPermission(String permission) {
        var principal = SecurityUtils.principalOrNull();
        return principal != null && principal.permissions() != null && principal.permissions().contains(permission);
    }

    private UUID schoolId() {
        return TenantContext.schoolId();
    }

    private UUID membershipId() {
        return TenantContext.actorMembershipId();
    }

    private UUID actorUserId() {
        return TenantContext.actorUserId();
    }

    public record ChannelSummary(UUID id, String channelType, String name, boolean staffOnly, UUID departmentId,
                                 UUID classGroupId, UUID subjectId, String memberRole, long lastReadSeq,
                                 long unreadCount, MessagePreview lastMessage, boolean archived) {
    }

    public record MessagePreview(Long seq, UUID messageId, String body, String senderName, Instant sentAt) {
    }

    public record MessagePage(List<MessageView> messages, boolean hasMore, Long lastSeq) {
    }

    public record MemberView(UUID membershipId, String displayName, String memberRole, long lastReadSeq) {
    }
}
