package com.omnistudy.exception;

public class AccountDisabledException extends RuntimeException {
    public AccountDisabledException(String message) { super(message); }
}
