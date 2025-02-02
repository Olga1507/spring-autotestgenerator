package ru.diasoft.springautotestgenerator.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import jakarta.websocket.server.PathParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import ru.diasoft.springautotestgenerator.domain.AllCases;
import ru.diasoft.springautotestgenerator.service.ExecutorService;
import ru.diasoft.springautotestgenerator.service.GenerateService;

@Slf4j
@RestController
@RequestMapping("/api")
public class RestApiController {

    @Autowired // аннотация, которая импортирует экземпляр класса GenerateService (обязательна если есть @Service)
    private GenerateService generateService;

    @Autowired // аннотация, которая импортирует экземпляр класса GenerateService (обязательна если есть @Service)
    private ExecutorService executorService;

    @GetMapping("hello")
    public String getHello(@PathParam("name") String name) {
        log.info("Вызван метод getHello для имени: {}", name);
        return "Hello, " + name + "!";
    }

    @PostMapping("generate")
    public String generate(@RequestBody String body) throws JsonProcessingException {
        return generateService.generate(body);
    }

    @PostMapping("execute")
    public String execute(@RequestBody AllCases allCases) throws Exception {
        return executorService.execute(allCases);
    }




}
