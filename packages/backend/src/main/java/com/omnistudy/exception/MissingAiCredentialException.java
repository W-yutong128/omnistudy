package com.omnistudy.exception;

public class MissingAiCredentialException extends RuntimeException {
    public MissingAiCredentialException(String message) {
        super(message);
    }
}

