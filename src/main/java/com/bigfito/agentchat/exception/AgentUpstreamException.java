package com.bigfito.agentchat.exception;

/**
 * Fallo aguas arriba (en Kibana/Elastic) al conversar con el agente:
 * {@link ErrorCode#UPSTREAM_ERROR} (5xx), {@link ErrorCode#TIMEOUT} o
 * {@link ErrorCode#AGENT_NOT_FOUND}.
 */
public class AgentUpstreamException extends AgentBuilderException {

    public AgentUpstreamException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public AgentUpstreamException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
