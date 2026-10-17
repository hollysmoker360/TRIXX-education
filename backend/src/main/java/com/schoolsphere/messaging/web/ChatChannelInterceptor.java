package com.schoolsphere.messaging.web;

import com.schoolsphere.common.tenant.TenantContext;
import com.schoolsphere.messaging.internal.MessagingService;
import com.schoolsphere.tenants.api.TenantsApi;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

/**
 * Authorizes every STOMP frame (spec 7.4): membership is validated at CONNECT, at each
 * SUBSCRIBE (school and channel must match the principal) and again at each SEND.
 */
@Component
public class ChatChannelInterceptor implements ChannelInterceptor {

    static final String CP_ATTR = "schoolsphere.chat.principal";
    static final String SCHOOL_HEADER = "X-School-Id";

    private static final Pattern TOPIC_DESTINATION =
            Pattern.compile("^/topic/school\\.([0-9a-fA-F-]{36})\\.channel\\.([0-9a-fA-F-]{36})$");
    private static final Pattern APP_DESTINATION =
            Pattern.compile("^/app/channels/([0-9a-fA-F-]{36})/(send|typing)$");

    private final TenantsApi tenantsApi;
    private final MessagingService messagingService;

    public ChatChannelInterceptor(TenantsApi tenantsApi, MessagingService messagingService) {
        this.tenantsApi = tenantsApi;
        this.messagingService = messagingService;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }
        switch (accessor.getCommand()) {
            case CONNECT -> authenticate(accessor);
            case SUBSCRIBE -> authorizeSubscribe(accessor);
            case SEND -> authorizeSend(accessor);
            default -> {
                // DISCONNECT, heartbeats, acks pass through.
            }
        }
        return message;
    }

    private void authenticate(StompHeaderAccessor accessor) {
        Object userIdAttr = sessionAttributes(accessor).get(ChatHandshakeInterceptor.ATTR_USER_ID);
        if (!(userIdAttr instanceof String rawUserId)) {
            throw new MessagingException("Authentication required.");
        }
        String schoolHeader = accessor.getFirstNativeHeader(SCHOOL_HEADER);
        UUID schoolId;
        try {
            schoolId = UUID.fromString(schoolHeader);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new MessagingException("A valid " + SCHOOL_HEADER + " header is required.");
        }
        UUID userId = UUID.fromString(rawUserId);
        TenantsApi.MembershipView membership = tenantsApi.resolveMembership(userId, schoolId)
                .filter(m -> "active".equals(m.status()))
                .orElseThrow(() -> new MessagingException("No active membership for this school."));
        if (!membership.permissions().contains("chat.use")) {
            throw new MessagingException("The chat.use permission is required.");
        }
        Object displayName = sessionAttributes(accessor).get(ChatHandshakeInterceptor.ATTR_DISPLAY_NAME);
        ChatPrincipal principal = new ChatPrincipal(userId, membership.membershipId(), schoolId,
                membership.permissions(), displayName == null ? null : displayName.toString());
        sessionAttributes(accessor).put(CP_ATTR, principal);
        accessor.setUser(principal);
    }

    private void authorizeSubscribe(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (destination == null) {
            throw new MessagingException("A destination is required.");
        }
        if (destination.startsWith("/user/")) {
            return; // session-scoped queue (acks/errors); Spring resolves per principal
        }
        Matcher matcher = TOPIC_DESTINATION.matcher(destination);
        if (!matcher.matches()) {
            throw new MessagingException("Subscriptions are not allowed to " + destination);
        }
        ChatPrincipal principal = requirePrincipal(accessor);
        UUID schoolId = UUID.fromString(matcher.group(1));
        UUID channelId = UUID.fromString(matcher.group(2));
        if (!schoolId.equals(principal.schoolId())) {
            throw new MessagingException("Cross-school subscription denied.");
        }
        requireChannelMember(principal, channelId);
    }

    private void authorizeSend(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (destination == null) {
            throw new MessagingException("A destination is required.");
        }
        Matcher matcher = APP_DESTINATION.matcher(destination);
        if (!matcher.matches()) {
            throw new MessagingException("Messaging is not allowed to " + destination);
        }
        requireChannelMember(requirePrincipal(accessor), UUID.fromString(matcher.group(1)));
    }

    private void requireChannelMember(ChatPrincipal principal, UUID channelId) {
        boolean member = TenantContext.run(principal.schoolId(), principal.userId(), principal.membershipId(),
                () -> messagingService.isMember(channelId, principal.membershipId()));
        if (!member) {
            throw new MessagingException("You are not a member of this channel.");
        }
    }

    private static ChatPrincipal requirePrincipal(SimpMessageHeaderAccessor accessor) {
        if (accessor.getSessionAttributes() != null) {
            Object stored = accessor.getSessionAttributes().get(CP_ATTR);
            if (stored instanceof ChatPrincipal principal) {
                return principal;
            }
        }
        if (accessor.getUser() instanceof ChatPrincipal principal) {
            return principal;
        }
        throw new MessagingException("Not authenticated: send a CONNECT frame first.");
    }

    private static java.util.Map<String, Object> sessionAttributes(SimpMessageHeaderAccessor accessor) {
        java.util.Map<String, Object> attributes = accessor.getSessionAttributes();
        if (attributes == null) {
            throw new MessagingException("No WebSocket session.");
        }
        return attributes;
    }

    /** Principal stored at CONNECT, for use by STOMP controller handlers. */
    static ChatPrincipal principal(SimpMessageHeaderAccessor accessor) {
        return requirePrincipal(accessor);
    }
}
