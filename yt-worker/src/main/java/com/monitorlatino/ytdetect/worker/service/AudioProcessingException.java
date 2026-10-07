package com.monitorlatino.ytdetect.worker.service;

public class AudioProcessingException extends RuntimeException {

    private final String errorCode;

    public AudioProcessingException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public AudioProcessingException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
