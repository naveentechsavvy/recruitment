package com.example.recruitment.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

/** The JSON shape of every error the API returns. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiError(Instant timestamp, int status, String error, String message, Map<String, String> fieldErrors) { }
