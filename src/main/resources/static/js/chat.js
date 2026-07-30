"use strict";

/*
 * Captura el prompt, lo envía por POST a /api/chat/stream y lee la respuesta
 * como stream SSE, mostrando la conversación (prompt del usuario + respuesta del
 * agente) en el área de mensajes.
 *
 * Se usa fetch + ReadableStream (en lugar de EventSource, que solo admite GET)
 * para poder enviar el prompt por POST: así no queda en la URL ni en los logs.
 *
 * Cada turno crea dos burbujas: una del usuario ("Tú") y otra del agente. La del
 * usuario se pinta con textContent (nunca HTML). La del agente llega en Markdown,
 * se renderiza con `marked` y SIEMPRE se sanea con `DOMPurify` antes de inyectarlo
 * en el DOM (protección contra XSS). Si las librerías no estuvieran disponibles,
 * se cae de forma segura a texto plano.
 *
 * Nota: la app no tiene memoria conversacional; el historial que se ve aquí es
 * solo visual — cada prompt se envía al agente de forma independiente.
 */
(() => {
    const formulario = document.getElementById("formulario");
    const boton = document.getElementById("enviar");
    const reiniciar = document.getElementById("reiniciar");
    const salida = document.getElementById("respuesta");
    const entrada = document.getElementById("prompt");
    const estado = document.getElementById("estado");

    // Estado de la interacción en curso.
    let textoCrudo = "";          // Markdown acumulado de la respuesta actual
    let renderPendiente = false;
    let respuestaConError = false; // si hubo error, NO se limpia el prompt
    let burbujaAgente = null;      // burbuja de la respuesta en curso
    let cuerpoAgente = null;       // contenedor donde se pinta la respuesta
    let controladorAbort = null;   // permite cancelar la petición en curso (reinicio)

    // ¿Están disponibles las librerías de render seguro?
    const puedeRenderizarMarkdown =
        typeof marked !== "undefined" && typeof DOMPurify !== "undefined";

    if (puedeRenderizarMarkdown) {
        // gfm: tablas, etc.  breaks: un salto de línea simple se vuelve <br>.
        marked.setOptions({ gfm: true, breaks: true });
        // Los enlaces se abren en pestaña nueva de forma segura.
        DOMPurify.addHook("afterSanitizeAttributes", (nodo) => {
            if (nodo.tagName === "A") {
                nodo.setAttribute("target", "_blank");
                nodo.setAttribute("rel", "noopener noreferrer");
            }
        });
    }

    formulario.addEventListener("submit", (evento) => {
        evento.preventDefault();
        enviarPrompt();
    });

    // Atajo: Ctrl/Cmd + Enter envía sin salir del textarea.
    entrada.addEventListener("keydown", (evento) => {
        if ((evento.ctrlKey || evento.metaKey) && evento.key === "Enter") {
            evento.preventDefault();
            enviarPrompt();
        }
    });

    reiniciar.addEventListener("click", reiniciarApp);

    async function enviarPrompt() {
        const prompt = entrada.value.trim();
        if (!prompt) {
            entrada.focus();
            return;
        }

        const controlador = new AbortController();
        controladorAbort = controlador;

        iniciarInteraccion(prompt);

        try {
            const respuesta = await fetch("/api/chat/stream", {
                method: "POST",
                headers: {
                    "Content-Type": "application/json",
                    // Aceptamos también JSON para recibir el mensaje de error
                    // cuando el fallo ocurre antes de abrir el stream (p. ej. validación).
                    "Accept": "text/event-stream, application/json"
                },
                body: JSON.stringify({ prompt }),
                signal: controlador.signal
            });

            if (!respuesta.ok) {
                await mostrarErrorHttp(respuesta);
                return;
            }

            await leerStream(respuesta.body);
        } catch (error) {
            if (controlador.signal.aborted) {
                return; // el reinicio canceló la petición: no es un error real
            }
            mostrarError("No se pudo contactar con el agente. Revisa tu conexión.");
        } finally {
            // Si el reinicio abortó esta petición, ya se ocupó de restaurar la UI.
            if (!controlador.signal.aborted) {
                finalizarUI();
            }
            if (controladorAbort === controlador) {
                controladorAbort = null;
            }
        }
    }

    /**
     * Lee el cuerpo como stream SSE con un búfer por líneas: acumula bytes,
     * separa eventos por la línea en blanco y procesa cada uno.
     */
    async function leerStream(cuerpo) {
        const lector = cuerpo.getReader();
        const decodificador = new TextDecoder();
        let buffer = "";

        for (;;) {
            const { value, done } = await lector.read();
            if (done) {
                break;
            }
            // Normalizamos CRLF a LF para separar eventos de forma uniforme.
            buffer += decodificador.decode(value, { stream: true }).replace(/\r\n/g, "\n");

            let corte;
            while ((corte = buffer.indexOf("\n\n")) !== -1) {
                const bloque = buffer.slice(0, corte);
                buffer = buffer.slice(corte + 2);
                if (procesarEvento(bloque)) {
                    return; // evento "done" o "error": no hace falta seguir leyendo.
                }
            }
        }

        // Procesa un último bloque si el stream cerró sin línea en blanco final.
        if (buffer.trim()) {
            procesarEvento(buffer);
        }
    }

    /**
     * Interpreta un bloque de evento SSE y actúa según su nombre.
     * @returns {boolean} true si el evento marca el fin del stream.
     */
    function procesarEvento(bloque) {
        let nombreEvento = "message";
        const lineasDatos = [];

        for (const linea of bloque.split("\n")) {
            if (linea === "" || linea.startsWith(":")) {
                continue; // línea vacía o comentario SSE
            }
            const separador = linea.indexOf(":");
            const campo = separador === -1 ? linea : linea.slice(0, separador);
            let valor = separador === -1 ? "" : linea.slice(separador + 1);
            if (valor.startsWith(" ")) {
                valor = valor.slice(1); // el espacio tras "campo:" es opcional
            }

            if (campo === "event") {
                nombreEvento = valor;
            } else if (campo === "data") {
                lineasDatos.push(valor);
            }
        }

        const datos = lineasDatos.join("\n");

        switch (nombreEvento) {
            case "message":
                acumularTexto(extraerTexto(datos));
                return false;
            case "done":
                renderizarAhora(); // asegura el render final del último fragmento
                return true;
            case "error":
                mostrarErrorPayload(datos);
                return true;
            default:
                return false; // evento desconocido: se ignora
        }
    }

    /**
     * Extrae el texto de un evento "message". El payload es JSON ({"text": ...})
     * para preservar espacios y saltos de línea; si no fuese JSON, se usa tal cual.
     */
    function extraerTexto(datos) {
        try {
            const cuerpo = JSON.parse(datos);
            return (cuerpo && typeof cuerpo.text === "string") ? cuerpo.text : "";
        } catch (_) {
            return datos;
        }
    }

    /** Acumula el nuevo fragmento y programa un render (o cae a texto plano). */
    function acumularTexto(texto) {
        textoCrudo += texto;
        if (puedeRenderizarMarkdown) {
            programarRender();
        } else if (cuerpoAgente) {
            cuerpoAgente.textContent = textoCrudo; // respaldo seguro sin librerías
            desplazarAlFinal();
        }
    }

    /** Agrupa varios fragmentos en un único render por frame (rendimiento). */
    function programarRender() {
        if (renderPendiente) {
            return;
        }
        renderPendiente = true;
        requestAnimationFrame(() => {
            renderPendiente = false;
            renderizarMarkdown();
        });
    }

    function renderizarAhora() {
        if (puedeRenderizarMarkdown) {
            renderizarMarkdown();
        }
    }

    /** Parsea el Markdown y lo SANEA antes de inyectarlo (nunca innerHTML crudo). */
    function renderizarMarkdown() {
        if (!cuerpoAgente) {
            return;
        }
        cuerpoAgente.innerHTML = DOMPurify.sanitize(marked.parse(textoCrudo));
        desplazarAlFinal();
    }

    async function mostrarErrorHttp(respuesta) {
        let mensaje = "Ocurrió un error (" + respuesta.status + ").";
        try {
            const cuerpo = await respuesta.json();
            if (cuerpo && cuerpo.message) {
                mensaje = cuerpo.message;
            }
        } catch (_) {
            // Cuerpo no JSON: nos quedamos con el mensaje genérico.
        }
        mostrarError(mensaje);
    }

    function mostrarErrorPayload(datos) {
        let mensaje = "Ocurrió un error al generar la respuesta.";
        try {
            const cuerpo = JSON.parse(datos);
            if (cuerpo && cuerpo.message) {
                mensaje = cuerpo.message;
            }
        } catch (_) {
            // Payload no JSON: mensaje genérico.
        }
        mostrarError(mensaje);
    }

    /**
     * Inicia un turno: añade la burbuja del usuario y una burbuja (vacía) para la
     * respuesta del agente, que se irá rellenando con el stream.
     */
    function iniciarInteraccion(prompt) {
        textoCrudo = "";
        respuestaConError = false;

        agregarBurbujaUsuario(prompt);
        crearBurbujaAgente();

        boton.disabled = true;
        estado.textContent = "Generando respuesta…";
        desplazarAlFinal();
    }

    /** Burbuja con el prompt del usuario (texto plano, nunca HTML). */
    function agregarBurbujaUsuario(texto) {
        const cuerpo = document.createElement("div");
        cuerpo.className = "mensaje__cuerpo";
        cuerpo.textContent = texto;
        salida.appendChild(construirBurbuja("mensaje--usuario", "Tú", cuerpo));
    }

    /** Burbuja (vacía) donde se pintará la respuesta del agente en streaming. */
    function crearBurbujaAgente() {
        cuerpoAgente = document.createElement("div");
        cuerpoAgente.className = "mensaje__cuerpo";
        burbujaAgente = construirBurbuja("mensaje--agente mensaje--escribiendo", "Agente", cuerpoAgente);
        salida.appendChild(burbujaAgente);
    }

    /** Crea el nodo de una burbuja con su autor y su cuerpo. */
    function construirBurbuja(clases, autor, cuerpo) {
        const burbuja = document.createElement("div");
        burbuja.className = "mensaje " + clases;
        const etiqueta = document.createElement("div");
        etiqueta.className = "mensaje__autor";
        etiqueta.textContent = autor;
        burbuja.append(etiqueta, cuerpo);
        return burbuja;
    }

    function finalizarUI() {
        if (burbujaAgente) {
            burbujaAgente.classList.remove("mensaje--escribiendo");
        }
        boton.disabled = false;
        estado.textContent = "";
        // Limpia el prompt solo si el agente respondió sin errores.
        if (!respuestaConError) {
            entrada.value = "";
        }
        entrada.focus();
        desplazarAlFinal();
    }

    /**
     * Muestra un aviso de error dentro de la burbuja del agente, sin borrar lo ya
     * recibido. Se usa textContent, así que el mensaje nunca puede inyectar HTML.
     */
    function mostrarError(mensaje) {
        respuestaConError = true; // evita que se limpie el prompt tras un error
        const aviso = document.createElement("div");
        aviso.className = "aviso-error";
        aviso.textContent = "⚠ " + mensaje;
        (cuerpoAgente || salida).appendChild(aviso);
        if (burbujaAgente) {
            burbujaAgente.classList.add("mensaje--error");
        }
        desplazarAlFinal();
    }

    /** Mantiene la conversación desplazada hasta el último mensaje. */
    function desplazarAlFinal() {
        salida.scrollTop = salida.scrollHeight;
    }

    /**
     * Reinicia la aplicación desde cero (en el cliente): cancela cualquier
     * respuesta en curso, borra la conversación y el prompt, y deja la UI como
     * recién abierta. Pide confirmación si hay algo que se vaya a perder.
     */
    function reiniciarApp() {
        const hayContenido = salida.children.length > 0 || entrada.value.trim() !== "";
        if (hayContenido &&
            !window.confirm("¿Reiniciar la aplicación? Se borrará toda la conversación.")) {
            return;
        }

        // Cancela la petición/stream en curso, si lo hubiera.
        if (controladorAbort) {
            controladorAbort.abort();
            controladorAbort = null;
        }
        renderPendiente = false;

        // Borra la conversación y restablece el estado.
        salida.textContent = "";
        entrada.value = "";
        textoCrudo = "";
        respuestaConError = false;
        burbujaAgente = null;
        cuerpoAgente = null;

        // Restaura los controles a su estado inicial.
        boton.disabled = false;
        estado.textContent = "";
        entrada.focus();
    }
})();
