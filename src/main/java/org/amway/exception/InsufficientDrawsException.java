package org.amway.exception;

public class InsufficientDrawsException extends RuntimeException {
    public InsufficientDrawsException(String message) {
        super(message);
    }
}