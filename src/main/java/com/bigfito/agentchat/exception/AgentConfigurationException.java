package com.bigfito.agentchat.exception;

/**
 * Falta configuración esencial para hablar con Agent Builder
 * (HOST de Kibana, API Key o {@code agent_id}).
 */
public class AgentConfigurationException extends AgentBuilderException {

    public AgentConfigurationException(String message) {
        super(ErrorCode.CONFIG_MISSING, message);
    }
}
