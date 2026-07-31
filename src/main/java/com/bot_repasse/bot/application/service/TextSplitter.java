package com.bot_repasse.bot.application.service;

import java.util.ArrayList;
import java.util.List;

public class TextSplitter {

    private TextSplitter() {}

    /**
     * Divide um texto em uma lista de strings, garantindo que nenhuma parte
     * ultrapasse o tamanho máximo especificado.
     * Tenta quebrar de forma segura (nos espaços ou quebras de linha) para não destruir palavras ou links.
     *
     * @param text      Texto original a ser processado.
     * @param maxLength Limite máximo de caracteres por bloco.
     * @return Lista contendo os blocos de texto divididos.
     */
    public static List<String> split(String text, int maxLength) {
        List<String> parts = new ArrayList<>();

        if (text == null || text.isBlank()) {
            return parts;
        }

        String remaining = text.trim();

        while (remaining.length() > maxLength) {
            // Tenta quebrar primeiro na última quebra de linha permitida dentro do limite
            int splitIndex = remaining.lastIndexOf('\n', maxLength);

            // Se não houver quebra de linha, tenta no último espaço em branco
            if (splitIndex == -1) {
                splitIndex = remaining.lastIndexOf(' ', maxLength);
            }

            // Se for uma palavra/link gigantesco sem espaços (raro, mas possível), quebra exatamente no limite
            if (splitIndex == -1) {
                splitIndex = maxLength;
            }

            // Extrai a parte, limpa espaços sobressalentes e adiciona na lista
            parts.add(remaining.substring(0, splitIndex).trim());

            // Atualiza o texto restante
            remaining = remaining.substring(splitIndex).trim();
        }

        // Adiciona a "sobra" final, se houver
        if (!remaining.isEmpty()) {
            parts.add(remaining);
        }

        return parts;
    }
}
