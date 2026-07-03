package io.github.yusupoff03.logstream.configuration;

import io.github.yusupoff03.logstream.dto.ExceptionResponse;
import io.github.yusupoff03.logstream.exception.BadCredentialsException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ExceptionResponse> handleDataNotFoundException(BadCredentialsException e) {
        ExceptionResponse response = new ExceptionResponse();
        response.setMessage(e.getMessage());

        return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
    }

}
