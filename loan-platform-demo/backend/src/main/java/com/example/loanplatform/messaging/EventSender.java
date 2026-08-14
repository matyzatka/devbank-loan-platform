package com.example.loanplatform.messaging;

import com.example.loanplatform.application.PendingOutboxEvent;

public interface EventSender {

    void send(String topic, PendingOutboxEvent event);
}
