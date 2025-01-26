package ru.diasoft.springautotestgenerator.controller;

import jakarta.websocket.server.PathParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api")
public class RestApiController {

    @GetMapping("hello")
    public String getHello(@PathParam("name") String name) {
        log.info("Вызван метод getHello для имени: {}", name);
        return "Hello, " + name + "!";
    }

}
