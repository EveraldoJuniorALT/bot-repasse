package com.bot_repasse.bot.infra.telegram;

import com.bot_repasse.bot.application.service.PromoPostOrchestrator;
import com.bot_repasse.bot.config.TelegramProperties;
import com.bot_repasse.bot.domain.model.PromoPost;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
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
    @Mock
    private TelegramMediaDownloader downloader;
    @InjectMocks
    private TelegramBotListener listener;

    @BeforeEach
    void setUp() {
        // Evita NullPointerException ao criar a classe mãe do Telegram
        lenient().when(properties.token()).thenReturn("token-ficticio");
        lenient().when(properties.channelId()).thenReturn("-100");
        listener = new TelegramBotListener(properties, orchestrator, downloader);
    }

    @Test
    @DisplayName("Deve ignorar mensagens vindas de um canal não autorizado")
    void deveIgnorarCanalNaoAutorizado() {
        Update update = mock(Update.class);
        Message message = mock(Message.class);

        when(update.hasChannelPost()).thenReturn(true);
        when(update.getChannelPost()).thenReturn(message);
        when(message.getChatId()).thenReturn(-999L); // ID diferente do configurado

        listener.onUpdateReceived(update);

        // O Orquestrador nunca deve ser chamado
        verify(orchestrator, never()).processNewPost(any());
    }

    @Test
    @DisplayName("Deve processar mensagem de texto corretamente")
    void deveProcessarMensagemDeTexto() {
        Update update = mock(Update.class);
        Message message = mock(Message.class);

        when(update.hasChannelPost()).thenReturn(true);
        when(update.getChannelPost()).thenReturn(message);
        when(message.getChatId()).thenReturn(-100L); // ID Correto
        when(message.getMessageId()).thenReturn(42);
        when(message.getText()).thenReturn("Link do AliExpress!");

        listener.onUpdateReceived(update);

        // Captura o objeto PromoPost que foi enviado para o orquestrador
        ArgumentCaptor<PromoPost> captor = ArgumentCaptor.forClass(PromoPost.class);
        verify(orchestrator, times(1)).processNewPost(captor.capture());

        PromoPost postEnviado = captor.getValue();
        assertEquals("42", postEnviado.id());
        assertEquals("Link do AliExpress!", postEnviado.text());
    }
}