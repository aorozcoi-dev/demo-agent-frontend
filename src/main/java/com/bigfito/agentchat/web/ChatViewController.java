package com.bigfito.agentchat.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Controlador de la vista principal: sirve la página del chat (Thymeleaf).
 */
@Controller
public class ChatViewController {

    /**
     * @return el nombre de la plantilla {@code templates/index.html}.
     */
    @GetMapping("/")
    public String index() {
        return "index";
    }
}
