package com.example.loanplatform.messaging;

import com.example.loanplatform.application.PendingOutboxEvent;

/** Transport boundary kept separate from outbox orchestration for deterministic failure testing. */
public interface EventSender {

    void send(String topic, PendingOutboxEvent event);
}
