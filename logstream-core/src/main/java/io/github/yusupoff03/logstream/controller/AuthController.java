package io.github.yusupoff03.logstream.controller;

import io.github.yusupoff03.logstream.configuration.LogStreamProperties;
import io.github.yusupoff03.logstream.configuration.SessionManager;
import io.github.yusupoff03.logstream.dto.LoginDto;
import io.github.yusupoff03.logstream.exception.BadCredentialsException;
import io.github.yusupoff03.logstream.model.LogStreamSession;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/logstream")
public class AuthController {

    private final SessionManager sessionManager;
    private final LogStreamProperties properties;

    public AuthController(SessionManager sessionManager, LogStreamProperties properties) {
        this.sessionManager = sessionManager;
        this.properties = properties;
    }

    @PostMapping("/login")
    public ResponseEntity<Void> login(@RequestBody LoginDto loginDto, HttpServletResponse response) {

        if (!properties.getAuth().getUsername().equals(loginDto.getUsername()) || !properties.getAuth().getPassword().equals(loginDto.getPassword())) {
            throw new BadCredentialsException("Login or Password is incorrect");
        }

        LogStreamSession session =
                sessionManager.create(loginDto.getUsername());

        Cookie cookie = new Cookie(
                "LOGSTREAM_SESSION",
                session.getSessionId());

        cookie.setHttpOnly(true);

        cookie.setPath("/");

        cookie.setMaxAge(60 * 60 * 24);

        response.addCookie(cookie);

        return ResponseEntity.ok().build();
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {

        // Invalidate the server-side session if a valid cookie is present.
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("LOGSTREAM_SESSION".equals(cookie.getName())) {
                    sessionManager.remove(cookie.getValue());
                    break;
                }
            }
        }

        // Expire the cookie in the browser.
        Cookie expiredCookie = new Cookie("LOGSTREAM_SESSION", "");
        expiredCookie.setHttpOnly(true);
        expiredCookie.setPath("/");
        expiredCookie.setMaxAge(0);
        response.addCookie(expiredCookie);

        return ResponseEntity.ok().build();
    }

}
