package com.bigfito.agentchat.exception;

import org.springframework.http.HttpStatus;

/**
 * Códigos de error significativos de la aplicación.
 *
 * <p>Cada código explica <em>qué</em> pasó y lleva asociado un mensaje pensado
 * para que el usuario final entienda la causa probable y qué acción tomar,
 * además del estado HTTP con el que se responde cuando el fallo ocurre antes de
 * abrir el stream.</p>
 */
public enum ErrorCode {

    /** Prompt vacío. */
    PROMPT_EMPTY(HttpStatus.BAD_REQUEST,
            "El prompt no puede estar vacío. Escribe una pregunta."),

    /** Prompt más largo que el máximo permitido. */
    PROMPT_TOO_LONG(HttpStatus.BAD_REQUEST,
            "El prompt es demasiado largo. Acorta el texto e inténtalo de nuevo."),

    /** Falta configuración esencial (HOST, API KEY o agentId). */
    CONFIG_MISSING(HttpStatus.INTERNAL_SERVER_ERROR,
            "Falta configuración del servidor (HOST, API KEY o agentId). Revisa las variables de entorno."),

    /** API Key inválida o expirada (HTTP 401). */
    AUTH_INVALID(HttpStatus.UNAUTHORIZED,
            "La API Key es inválida o ha expirado. Regenera la API Key en Kibana."),

    /** La API Key no tiene permisos de Agent Builder (HTTP 403). */
    FORBIDDEN(HttpStatus.FORBIDDEN,
            "La API Key no tiene permisos para Agent Builder. Ajusta los privilegios del rol asociado."),

    /** El {@code agent_id} configurado no existe (HTTP 404). */
    AGENT_NOT_FOUND(HttpStatus.NOT_FOUND,
            "No se encontró el agente indicado. Verifica el 'agent_id' configurado."),

    /** Fallo en Kibana/Elastic (HTTP 5xx). */
    UPSTREAM_ERROR(HttpStatus.BAD_GATEWAY,
            "El servicio de Elastic no está disponible en este momento. Inténtalo de nuevo más tarde."),

    /** El agente tardó más de lo permitido. */
    TIMEOUT(HttpStatus.GATEWAY_TIMEOUT,
            "El agente tardó demasiado en responder. Vuelve a intentarlo."),

    /** Un evento SSE no se pudo parsear (se registra y se ignora el evento). */
    STREAM_PARSE(HttpStatus.INTERNAL_SERVER_ERROR,
            "Se recibió un evento no interpretable del agente y se ignoró.");

    private final HttpStatus httpStatus;
    private final String defaultMessage;

    ErrorCode(HttpStatus httpStatus, String defaultMessage) {
        this.httpStatus = httpStatus;
        this.defaultMessage = defaultMessage;
    }

    /** @return estado HTTP con el que responder cuando el fallo ocurre antes del stream. */
    public HttpStatus httpStatus() {
        return httpStatus;
    }

    /** @return mensaje por defecto, entendible por el usuario final. */
    public String defaultMessage() {
        return defaultMessage;
    }
}
