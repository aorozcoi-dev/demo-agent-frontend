package com.bigfito.agentchat.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Entrada del usuario recibida por el controlador de chat.
 *
 * @param prompt pregunta escrita por el usuario; no puede estar vacía.
 */
public record PromptRequest(
        @NotBlank(message = "El prompt no puede estar vacío.")
        @Size(max = 4_000, message = "El prompt supera el máximo de 4000 caracteres.")
        String prompt) {
}
