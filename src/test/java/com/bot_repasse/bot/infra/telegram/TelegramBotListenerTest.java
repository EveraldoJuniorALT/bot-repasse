package com.bot_repasse.bot.infra.telegram;

import com.bot_repasse.bot.application.service.PromoPostOrchestrator;
import com.bot_repasse.bot.config.TelegramProperties;
import com.bot_repasse.bot.domain.model.PromoPost;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TelegramBotListenerTest {

    @Mock private TelegramProperties properties;
    @Mock private PromoPostOrchestrator orchestrator;
    @Mock private TelegramMediaDownloader downloader;
    private TelegramBotListener listener;

    @BeforeEach
    void setUp() {
        when(properties.token()).thenReturn("token-ficticio");
        listener = new TelegramBotListener(properties, orchestrator, downloader);
    }

    @Test
    @DisplayName("Deve ignorar mensagens antigas")
    void deveIgnorarMensagemAntiga() {
        Update update = mock(Update.class);
        Message message = mock(Message.class);

        when(update.hasMessage()).thenReturn(true);
        when(update.getMessage()).thenReturn(message);
        when(message.getDate()).thenReturn((int) (System.currentTimeMillis() / 1000L) - 10);

        listener.onUpdateReceived(update);

        verify(orchestrator, never()).processNewPost(any());
    }

    @Test
    @DisplayName("Deve processar mensagem de texto corretamente")
    void deveProcessarMensagemDeTexto() {
        Update update = mock(Update.class);
        Message message = mock(Message.class);

        when(update.hasMessage()).thenReturn(true);
        when(update.getMessage()).thenReturn(message);
        when(message.getDate()).thenReturn((int) (System.currentTimeMillis() / 1000L) + 10);
        when(message.getMessageId()).thenReturn(42);
        when(message.getText()).thenReturn("Link do AliExpress!");

        listener.onUpdateReceived(update);

        ArgumentCaptor<PromoPost> captor = ArgumentCaptor.forClass(PromoPost.class);
        verify(orchestrator, times(1)).processNewPost(captor.capture());

        PromoPost postEnviado = captor.getValue();
        assertEquals("42", postEnviado.id());
        assertEquals("Link do AliExpress!", postEnviado.text());
        assertNull(postEnviado.mediaBytes());
        assertNull(postEnviado.mimeType());
    }
}