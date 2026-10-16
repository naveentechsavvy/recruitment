package com.example.recruitment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RemarkRequest(@NotBlank @Size(max = 1000) String text) { }
