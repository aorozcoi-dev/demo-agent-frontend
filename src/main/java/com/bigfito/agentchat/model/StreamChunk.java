package com.bigfito.agentchat.model;

/**
 * Fragmento de salida ya normalizado, listo para pintarse en el navegador.
 *
 * <p>Representa un trozo de texto ({@code delta}) emitido por el agente durante
 * el streaming. El cliente los va concatenando en pantalla.</p>
 *
 * @param text texto visible del fragmento.
 */
public record StreamChunk(String text) {
}
