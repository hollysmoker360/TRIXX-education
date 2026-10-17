package com.schoolsphere.messaging.web;

import com.schoolsphere.common.error.ApiException;
import com.schoolsphere.common.tenant.TenantContext;
import com.schoolsphere.messaging.api.MessagingApi;
import com.schoolsphere.messaging.internal.ChatEvents;
import com.schoolsphere.messaging.internal.MessagingService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

/**
 * STOMP entry points for chat (spec 7.4). Broadcasts are relayed after commit by
 * {@link ChatBroadcastListener}; duplicates and errors answer on the sender's /user queue.
 */
@Controller
public class ChatStompController {

    private static final Logger log = LoggerFactory.getLogger(ChatStompController.class);

    private final MessagingService messagingService;
    private final SimpMessagingTemplate broker;

    public ChatStompController(MessagingService messagingService, SimpMessagingTemplate broker) {
        this.messagingService = messagingService;
        this.broker = broker;
    }

    @MessageMapping("/channels/{channelId}/send")
    public void send(@DestinationVariable UUID channelId, @Payload SendPayload payload,
                     SimpMessageHeaderAccessor accessor) {
        ChatPrincipal principal = ChatChannelInterceptor.principal(accessor);
        try {
            MessagingApi.SendOutcome outcome = TenantContext.run(principal.schoolId(), principal.userId(),
                    principal.membershipId(),
                    () -> messagingService.sendMessage(new MessagingApi.MessageInput(
                            null, payload.clientId(), channelId, payload.body(), payload.replyToId(),
                            payload.attachments())));
            if (!outcome.created()) {
                broker.convertAndSendToUser(principal.getName(), "/queue/chat.ack", outcome.message());
            }
        } catch (ApiException e) {
            log.debug("STOMP send rejected for channel {}: {}", channelId, e.getMessage());
            broker.convertAndSendToUser(principal.getName(), "/queue/chat.errors",
                    Map.of("code", e.getCode(), "message", e.getMessage()));
        }
    }

    @MessageMapping("/channels/{channelId}/typing")
    public void typing(@DestinationVariable UUID channelId, SimpMessageHeaderAccessor accessor) {
        ChatPrincipal principal = ChatChannelInterceptor.principal(accessor);
        broker.convertAndSend(ChatEvents.topic(principal.schoolId(), channelId),
                new ChatEvents.Typing(principal.schoolId(), channelId, principal.membershipId(),
                        principal.displayName()));
    }

    public record SendPayload(UUID clientId, String body, UUID replyToId,
                              List<Map<String, Object>> attachments) {
    }
}
