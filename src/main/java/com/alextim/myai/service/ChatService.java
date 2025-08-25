package com.alextim.myai.service;

import com.alextim.myai.model.Chat;
import com.alextim.myai.model.ChatEntry;
import com.alextim.myai.model.Role;
import com.alextim.myai.repository.ChatRepository;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

import static com.alextim.myai.model.Role.ASSISTANT;
import static com.alextim.myai.model.Role.USER;


@Service
@RequiredArgsConstructor
@Slf4j
public class ChatService {

    private final ChatRepository chatRepo;

    private final ChatClient chatClient;

    public Chat createNewChat(String title) {
        Chat chat = Chat.builder().title(title).build();
        return chatRepo.save(chat);
    }

    public List<Chat> getAllChats() {
        return chatRepo.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    public Chat getChat(Long chatId) {
        return chatRepo.findById(chatId).orElseThrow();
    }

    public void deleteChat(Long chatId) {
        chatRepo.deleteById(chatId);
    }

    public void addChatEntry(Long chatId, String prompt, Role role) {
        Chat chat = chatRepo.findById(chatId).orElseThrow();
        chat.addChatEntry(ChatEntry.builder().content(prompt).role(role).build());
        chatRepo.save(chat);
    }

    public void proceedInteraction(Long chatId, String prompt) {
        log.info("ChatId: {}, Prompt; {}", chatId, prompt);

        addChatEntry(chatId, prompt, USER);

        String answer = chatClient.prompt().user(prompt).call().content();
        log.info("Answer: {}", answer);

        addChatEntry(chatId, answer, ASSISTANT);
    }

    public SseEmitter proceedInteractionWithStreaming(Long chatId, String userPrompt) {
        SseEmitter sseEmitter = new SseEmitter(0L);

        StringBuilder answer = new StringBuilder();

        chatClient
                .prompt(userPrompt)
                .advisors(advisorSpec -> advisorSpec.param(ChatMemory.CONVERSATION_ID, chatId))
                .stream()
                .chatResponse()
                .subscribe(
                        (ChatResponse response) -> processToken(response, sseEmitter, answer),
                        sseEmitter::completeWithError,
                        sseEmitter::complete
                );

        return sseEmitter;
    }


    @SneakyThrows
    private static void processToken(ChatResponse response, SseEmitter emitter, StringBuilder answer) {
        var token = response.getResult().getOutput();

        emitter.send(token);

        answer.append(token.getText());
    }
}
