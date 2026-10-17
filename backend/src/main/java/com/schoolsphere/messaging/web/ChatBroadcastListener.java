package com.schoolsphere.messaging.web;

import com.schoolsphere.messaging.internal.ChatEvents;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Relays in-process chat events to STOMP topics after the database transaction commits
 * (spec 3.1); fallbackExecution covers callers that run without a transaction.
 */
@Component
public class ChatBroadcastListener {

    private final SimpMessagingTemplate broker;

    public ChatBroadcastListener(SimpMessagingTemplate broker) {
        this.broker = broker;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onMessageChanged(ChatEvents.MessageChanged event) {
        broker.convertAndSend(ChatEvents.topic(event.schoolId(), event.channelId()), event.message());
    }
}
