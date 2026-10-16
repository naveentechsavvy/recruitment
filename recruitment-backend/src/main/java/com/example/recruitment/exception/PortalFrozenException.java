package com.example.recruitment.exception;

/** The client portal is frozen, so the requested change is not allowed. Answered as HTTP 423 (Locked). */
public class PortalFrozenException extends RuntimeException {
    public PortalFrozenException(String message) { super(message); }
}
