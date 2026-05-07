package com.aichat.app.controller;

import com.aichat.app.model.*;
import com.aichat.app.service.AgentChatService;
import com.aichat.app.service.ChatService;
import com.aichat.app.service.DatePlanService;
import com.aichat.app.service.RagService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 聊天控制器
 * 支持：普通 RAG 对话 + 约会规划（LangGraph4j）
 */
@RestController
@RequestMapping("/chat")
@RequiredArgsConstructor
@Slf4j
public class ChatController {

    private final ChatService chatService;
    private final AgentChatService agentChatService;
    private final RagService ragService;
    private final DatePlanService datePlanService;
    private final ConversationRepository conversationRepository;
    private final ChatMessageRepository chatMessageRepository;

    @GetMapping("/conversations")
    public List<Map<String, Object>> listConversations() {
        return conversationRepository.findAllByOrderByUpdatedAtDesc().stream()
                .map(conv -> Map.<String, Object>of(
                        "id", conv.getConvId(),
                        "title", conv.getTitle(),
                        "messageCount", conv.getMessages().size(),
                        "updatedAt", conv.getUpdatedAt()
                ))
                .toList();
    }

    @GetMapping("/conversations/{convId}/messages")
    public List<Map<String, String>> getMessages(@PathVariable String convId) {
        return chatMessageRepository.findByConversationConvIdOrderByCreatedAtAsc(convId)
                .stream()
                .map(msg -> Map.of(
                        "content", msg.getContent(),
                        "isUser", String.valueOf(msg.isUserMessage())
                ))
                .toList();
    }

    @PostMapping("/conversations")
    public Map<String, String> createConversation() {
        String convId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        Conversation conv = new Conversation();
        conv.setConvId(convId);
        conv.setTitle("新的对话");
        conversationRepository.save(conv);
        return Map.of("id", convId);
    }

    @DeleteMapping("/conversations/{convId}")
    public Map<String, String> deleteConversation(@PathVariable String convId) {
        conversationRepository.findByConvId(convId)
                .ifPresent(conversationRepository::delete);
        return Map.of("status", "ok");
    }

    /**
     * 普通对话（POST）
     */
    @PostMapping
    public ChatResponse chat(@RequestBody ChatRequest request) {
        try {
            Conversation conv = getOrCreateConversation(request.getChatId());
            saveMessage(conv, true, request.getMessage());

            String result;
            if (datePlanService.isComplexTask(request.getMessage())) {
                // 复杂任务：走 LangGraph4j
                result = datePlanService.execute(request.getMessage());
            } else {
                // 普通对话：走 RAG
                String userMessage = enrichWithRag(request.getMessage());
                result = chatService.chat(userMessage);
            }

            saveMessage(conv, false, result);
            updateTitle(conv, request.getMessage());

            return ChatResponse.builder()
                    .content(result)
                    .chatId(conv.getConvId())
                    .success(true)
                    .build();
        } catch (Exception e) {
            log.error("对话失败", e);
            return ChatResponse.builder()
                    .success(false)
                    .errorMessage(e.getMessage())
                    .build();
        }
    }

    /**
     * SSE 流式对话
     */
    @GetMapping(value = "/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatSse(@RequestParam String message,
                              @RequestParam String convId) {
        SseEmitter emitter = new SseEmitter(300000L);

        Conversation conv = getOrCreateConversation(convId);
        saveMessage(conv, true, message);

        if (datePlanService.isComplexTask(message)) {
            // 复杂任务：走 LangGraph4j，推送结果
            CompletableFuture.runAsync(() -> {
                try {
                    String result = datePlanService.execute(message);
                    emitter.send(Map.of("type", "text", "content", result));
                    saveMessage(conv, false, result);
                    updateTitle(conv, message);
                    emitter.send(Map.of("type", "done"));
                    emitter.complete();
                } catch (Exception e) {
                    emitter.completeWithError(e);
                }
            });
        } else {
            // 普通对话：走 RAG SSE
            StringBuilder aiResponse = new StringBuilder();
            String userMessage = enrichWithRag(message);

            agentChatService.chatStream(userMessage,
                    token -> {
                        aiResponse.append(token);
                        try {
                            emitter.send(Map.of("type", "text", "content", token));
                        } catch (IOException e) {
                            emitter.completeWithError(e);
                        }
                    },
                    () -> {
                        saveMessage(conv, false, aiResponse.toString());
                        updateTitle(conv, message);
                        try {
                            emitter.send(Map.of("type", "done"));
                            emitter.complete();
                        } catch (IOException e) {
                            emitter.completeWithError(e);
                        }
                    }
            );
        }

        return emitter;
    }

    private Conversation getOrCreateConversation(String convId) {
        if (convId != null && !convId.isBlank()) {
            return conversationRepository.findByConvId(convId)
                    .orElseGet(() -> {
                        Conversation conv = new Conversation();
                        conv.setConvId(convId);
                        return conversationRepository.save(conv);
                    });
        }
        String newId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        Conversation conv = new Conversation();
        conv.setConvId(newId);
        return conversationRepository.save(conv);
    }

    private void saveMessage(Conversation conv, boolean isUser, String content) {
        ChatMessageEntity msg = new ChatMessageEntity();
        msg.setConversation(conv);
        msg.setUserMessage(isUser);
        msg.setContent(content);
        chatMessageRepository.save(msg);
    }

    private void updateTitle(Conversation conv, String message) {
        if (conv.getTitle().equals("新的对话") && message.length() > 0) {
            conv.setTitle(message.substring(0, Math.min(message.length(), 20))
                    .replace("\n", " "));
            conversationRepository.save(conv);
        }
    }

    private String enrichWithRag(String message) {
        try {
            String ragContext = ragService.search(message);
            if (!ragContext.isEmpty()) {
                return ragContext + "\n\n用户问题：" + message;
            }
        } catch (Exception e) {
            log.warn("RAG 查询失败", e);
        }
        return message;
    }

    /**
     * PDF 下载
     */
    @GetMapping("/pdf/{filename}")
    public ResponseEntity<org.springframework.core.io.Resource> downloadPdf(@PathVariable String filename) {
        try {
            java.io.File file = new java.io.File(System.getProperty("user.dir") + "/tmp/" + filename);
            if (!file.exists()) {
                return ResponseEntity.notFound().build();
            }
            org.springframework.core.io.Resource resource =
                    new org.springframework.core.io.FileSystemResource(file);
            return ResponseEntity.ok()
                    .header("Content-Disposition", "attachment; filename=" + filename)
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}
