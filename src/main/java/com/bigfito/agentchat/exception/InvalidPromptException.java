package com.bigfito.agentchat.exception;

/**
 * El prompt del usuario no es válido:
 * {@link ErrorCode#PROMPT_EMPTY} o {@link ErrorCode#PROMPT_TOO_LONG}.
 */
public class InvalidPromptException extends AgentBuilderException {

    public InvalidPromptException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
