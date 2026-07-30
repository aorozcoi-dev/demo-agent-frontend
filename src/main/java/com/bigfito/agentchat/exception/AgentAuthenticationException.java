package com.bigfito.agentchat.exception;

/**
 * Fallo de autenticación o autorización frente a Kibana:
 * {@link ErrorCode#AUTH_INVALID} (401) o {@link ErrorCode#FORBIDDEN} (403).
 */
public class AgentAuthenticationException extends AgentBuilderException {

    public AgentAuthenticationException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
