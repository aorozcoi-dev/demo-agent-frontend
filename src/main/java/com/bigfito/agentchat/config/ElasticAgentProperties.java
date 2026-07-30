package com.bigfito.agentchat.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Configuración de acceso a Elastic Agent Builder.
 *
 * <p><strong>Importante:</strong> {@code kibanaBaseUrl} apunta a <em>KIBANA</em>
 * (por ejemplo {@code https://mi-kibana:5601}), no al nodo de Elasticsearch
 * ({@code :9200}). El endpoint para conversar con un agente vive en Kibana.</p>
 *
 * <p>La API Key se toma siempre de variable de entorno; nunca debe escribirse
 * en el repositorio.</p>
 *
 * @param kibanaBaseUrl   URL base de Kibana.
 * @param apiKey          API Key de Kibana con permisos de Agent Builder.
 * @param agentId         Identificador del agente (p. ej. {@code elastic-ai-agent}).
 * @param space           Space de Kibana; vacío o {@code null} = space por defecto.
 * @param connectTimeout  Tiempo máximo para establecer la conexión.
 * @param responseTimeout Tiempo máximo de respuesta (los agentes pueden tardar).
 */
@Validated
@ConfigurationProperties(prefix = "elastic.agent")
public record ElasticAgentProperties(
        @NotBlank String kibanaBaseUrl,
        @NotBlank String apiKey,
        @NotBlank String agentId,
        String space,
        Duration connectTimeout,
        Duration responseTimeout) {

    /** Valor por defecto si no se configura {@code connect-timeout}. */
    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(5);

    /** Valor por defecto si no se configura {@code response-timeout}. */
    private static final Duration DEFAULT_RESPONSE_TIMEOUT = Duration.ofSeconds(120);

    /**
     * Compacta valores nulos a sus valores por defecto para que el resto de la
     * aplicación no tenga que preocuparse por {@code null} en los timeouts.
     */
    public ElasticAgentProperties {
        if (connectTimeout == null) {
            connectTimeout = DEFAULT_CONNECT_TIMEOUT;
        }
        if (responseTimeout == null) {
            responseTimeout = DEFAULT_RESPONSE_TIMEOUT;
        }
    }

    /**
     * Construye la ruta del endpoint de streaming respetando el Space.
     *
     * @return {@code /api/agent_builder/converse/async}, con el prefijo
     *         {@code /s/<space>} cuando se ha configurado un Space no vacío.
     */
    public String converseStreamPath() {
        return spacePrefix() + "/api/agent_builder/converse/async";
    }

    /**
     * Construye la ruta del endpoint síncrono (alternativa sin streaming).
     *
     * @return {@code /api/agent_builder/converse}, con prefijo de Space si aplica.
     */
    public String conversePath() {
        return spacePrefix() + "/api/agent_builder/converse";
    }

    /** Prefijo de Space para las rutas ({@code ""} si es el space por defecto). */
    private String spacePrefix() {
        boolean hasSpace = space != null && !space.isBlank();
        return hasSpace ? "/s/" + space.trim() : "";
    }
}
