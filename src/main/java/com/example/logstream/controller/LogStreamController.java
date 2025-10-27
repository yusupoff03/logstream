package com.example.logstream.controller;

import com.example.logstream.core.InMemoryAppender;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/logs-ui")
public class LogStreamController {

    private final InMemoryAppender appender;

    public LogStreamController(InMemoryAppender appender) {
        this.appender = appender;
    }

    @GetMapping
    public List<String> getLogs() {
        return appender.getLogs();
    }

}
