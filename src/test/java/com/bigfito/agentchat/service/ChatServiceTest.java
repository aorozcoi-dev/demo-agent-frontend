package com.bigfito.agentchat.service;

import com.bigfito.agentchat.client.AgentBuilderClient;
import com.bigfito.agentchat.exception.ErrorCode;
import com.bigfito.agentchat.exception.InvalidPromptException;
import com.bigfito.agentchat.model.StreamChunk;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ChatService: validación del prompt y delegación en el cliente")
class ChatServiceTest {

    @Mock
    private AgentBuilderClient client;

    private ChatService chatService;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        chatService = new ChatService(client);
    }

    @Test
    @DisplayName("prompt null lanza InvalidPromptException con PROMPT_EMPTY")
    void nullPromptFails() {
        assertThatThrownBy(() -> chatService.ask(null))
                .isInstanceOf(InvalidPromptException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PROMPT_EMPTY);
    }

    @Test
    @DisplayName("prompt en blanco lanza InvalidPromptException con PROMPT_EMPTY")
    void blankPromptFails() {
        assertThatThrownBy(() -> chatService.ask("   "))
                .isInstanceOf(InvalidPromptException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PROMPT_EMPTY);
    }

    @Test
    @DisplayName("prompt demasiado largo lanza InvalidPromptException con PROMPT_TOO_LONG")
    void tooLongPromptFails() {
        String largo = "a".repeat(ChatService.MAX_PROMPT_LENGTH + 1);

        assertThatThrownBy(() -> chatService.ask(largo))
                .isInstanceOf(InvalidPromptException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PROMPT_TOO_LONG);
    }

    @Test
    @DisplayName("prompt válido se recorta y se delega al cliente")
    void validPromptDelegatesTrimmed() {
        when(client.converseStream("hola"))
                .thenReturn(Flux.just(new StreamChunk("respuesta")));

        StepVerifier.create(chatService.ask("  hola  "))
                .expectNext(new StreamChunk("respuesta"))
                .verifyComplete();

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(client).converseStream(captor.capture());
        assertThat(captor.getValue()).isEqualTo("hola");
    }
}
