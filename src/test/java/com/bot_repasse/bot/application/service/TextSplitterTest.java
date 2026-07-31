package com.bot_repasse.bot.application.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TextSplitterTest {

    @Test
    @DisplayName("Deve retornar lista vazia se o texto for nulo ou vazio")
    void deveTratarTextoVazioENulo() {
        assertTrue(TextSplitter.split(null, 10).isEmpty());
        assertTrue(TextSplitter.split("   ", 10).isEmpty());
    }

    @Test
    @DisplayName("Não deve dividir texto que seja menor que o limite")
    void naoDeveDividirTextoCurto() {
        String texto = "Link de oferta do AliExpress!";
        List<String> partes = TextSplitter.split(texto, 1024);

        assertEquals(1, partes.size());
        assertEquals(texto, partes.getFirst());
    }

    @Test
    @DisplayName("Deve dividir texto exatamente no limite do espaço em branco para não quebrar a palavra")
    void deveDividirNoEspaco() {
        // "Promoção do" tem 11 chars. "celular" começa no 12.
        String texto = "Promoção do celular";

        // Se o limite é 15, o algoritmo não pode cortar "celular" no meio. Deve cortar no espaço anterior.
        List<String> partes = TextSplitter.split(texto, 15);

        assertEquals(2, partes.size());
        assertEquals("Promoção do", partes.get(0));
        assertEquals("celular", partes.get(1));
    }

    @Test
    @DisplayName("Deve forçar quebra dura (hard break) se a palavra for maior que o limite")
    void deveQuebrarPalavraGigante() {
        String urlGigante = "https://pt.aliexpress.com/item/100500123456789.html"; // 53 chars

        // Vamos forçar um limite de 20. Como não há espaços na URL, o corte deve ser exato no char 20.
        List<String> partes = TextSplitter.split(urlGigante, 20);

        assertEquals(3, partes.size());
        assertEquals("https://pt.aliexpres", partes.get(0));
        assertEquals("s.com/item/100500123", partes.get(1));
        assertEquals("456789.html", partes.get(2));
    }
}