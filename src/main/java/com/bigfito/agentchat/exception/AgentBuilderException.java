package com.bigfito.agentchat.exception;

/**
 * Excepción base de la aplicación. Toda excepción de dominio lleva un
 * {@link ErrorCode} que permite traducir el fallo a un mensaje entendible y a un
 * estado HTTP adecuado.
 */
public class AgentBuilderException extends RuntimeException {

    private final transient ErrorCode errorCode;

    /**
     * @param errorCode código significativo del error.
     * @param message   detalle técnico o mensaje enriquecido para el log/usuario.
     */
    public AgentBuilderException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    /**
     * @param errorCode código significativo del error.
     * @param message   detalle del error.
     * @param cause     excepción original que provocó este fallo.
     */
    public AgentBuilderException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    /** @return código de error asociado. */
    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
