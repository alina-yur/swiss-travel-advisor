package com.example.controller;

import com.example.memory.OracleChatMemoryConfiguration;
import com.example.memory.OracleChatMemoryStore;
import com.example.model.WishlistItem;
import com.example.repository.WishlistRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LegacyConversationControllerTest {

    @Test
    void loadsLegacyConversationWhoseIdIsNotUuid() {
        AtomicReference<String> requestedId = new AtomicReference<>();
        OracleChatMemoryStore memoryStore = new OracleChatMemoryStore(
                null, new OracleChatMemoryConfiguration()) {
            @Override
            public ConversationDetail getConversation(String memoryId) {
                requestedId.set(memoryId);
                return new ConversationDetail(memoryId, "Legacy trip", List.of());
            }
        };
        ChatController controller = new ChatController((id, message) -> "unused", memoryStore, null);

        OracleChatMemoryStore.ConversationDetail conversation = controller.conversation("legacy-demo-session");

        assertEquals("legacy-demo-session", requestedId.get());
        assertEquals("Legacy trip", conversation.title());
    }

    @Test
    void loadsWishlistForLegacyConversationId() {
        AtomicReference<String> requestedId = new AtomicReference<>();
        WishlistRepository repository = new WishlistRepository(null) {
            @Override
            public List<WishlistItem> findAll(String conversationId) {
                requestedId.set(conversationId);
                return List.of();
            }
        };
        WishlistController controller = new WishlistController(repository);

        controller.getWishlist("legacy-demo-session");

        assertEquals("legacy-demo-session", requestedId.get());
    }
}
