package com.example.logstream.repository;

import ch.qos.logback.classic.spi.ILoggingEvent;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

@Repository
public interface LogRepository {

    List<ILoggingEvent> logStorage = new ArrayList<>();

}
