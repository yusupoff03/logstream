package io.github.yusupoff03.logstream.dto;

public class LoginDto {

    private String username;

    private String password;

    public LoginDto(String username, String password) {
        this.username = username;
        this.password = password;
    }

    public LoginDto() {
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

}
