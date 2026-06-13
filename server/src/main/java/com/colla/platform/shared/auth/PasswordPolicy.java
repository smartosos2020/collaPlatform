package com.colla.platform.shared.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
@ConfigurationProperties(prefix = "colla.password-policy")
public class PasswordPolicy {
    private int minLength = 8;
    private boolean requireLetter = true;
    private boolean requireDigit = true;

    public void validate(String password) {
        String value = password == null ? "" : password;
        if (value.length() < minLength) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password is too short");
        }
        if (requireLetter && value.chars().noneMatch(Character::isLetter)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must contain a letter");
        }
        if (requireDigit && value.chars().noneMatch(Character::isDigit)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must contain a digit");
        }
    }

    public int getMinLength() {
        return minLength;
    }

    public void setMinLength(int minLength) {
        this.minLength = minLength;
    }

    public boolean isRequireLetter() {
        return requireLetter;
    }

    public void setRequireLetter(boolean requireLetter) {
        this.requireLetter = requireLetter;
    }

    public boolean isRequireDigit() {
        return requireDigit;
    }

    public void setRequireDigit(boolean requireDigit) {
        this.requireDigit = requireDigit;
    }
}
