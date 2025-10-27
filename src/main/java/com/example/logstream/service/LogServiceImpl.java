package com.example.logstream.service;

import com.example.logstream.repository.LogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class LogServiceImpl {

    @Autowired
    private LogRepository logRepository;



}
