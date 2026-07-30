package com.bigfito.agentchat.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Traduce las excepciones de la aplicación a respuestas HTTP claras cuando el
 * fallo ocurre <em>antes</em> de abrir el stream (por ejemplo, validación del
 * cuerpo de la petición).
 *
 * <p>Los fallos que ocurren <em>durante</em> el stream se convierten en un
 * evento SSE {@code error} dentro de
 * {@code ChatStreamController}, porque una vez enviada la cabecera no se puede
 * cambiar el estado HTTP.</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** Respuesta de error uniforme para el cliente. */
    public record ApiError(String code, String message) {
    }

    /** Errores de validación del cuerpo de la petición (@Valid). */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(fieldError -> fieldError.getDefaultMessage())
                .orElse(ErrorCode.PROMPT_EMPTY.defaultMessage());
        log.warn("Petición inválida: {}", message);
        return build(ErrorCode.PROMPT_EMPTY.name(), message, HttpStatus.BAD_REQUEST);
    }

    /** Cualquier excepción de dominio con su {@link ErrorCode}. */
    @ExceptionHandler(AgentBuilderException.class)
    public ResponseEntity<ApiError> handleDomain(AgentBuilderException ex) {
        ErrorCode code = ex.getErrorCode();
        log.error("Error de dominio [{}]: {}", code, ex.getMessage());
        return build(code.name(), code.defaultMessage(), code.httpStatus());
    }

    /** Red de seguridad para cualquier otro fallo no previsto. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex) {
        log.error("Error no controlado", ex);
        return build(ErrorCode.UPSTREAM_ERROR.name(),
                "Ocurrió un error inesperado. Inténtalo de nuevo más tarde.",
                HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private ResponseEntity<ApiError> build(String code, String message, HttpStatus status) {
        return ResponseEntity.status(status).body(new ApiError(code, message));
    }
}
