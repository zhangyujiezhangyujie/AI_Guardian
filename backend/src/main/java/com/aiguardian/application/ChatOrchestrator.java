package com.aiguardian.application;

import com.aiguardian.domain.model.ChatResponse;

public interface ChatOrchestrator {
    ChatResponse handle(ChatCommand command);
}
