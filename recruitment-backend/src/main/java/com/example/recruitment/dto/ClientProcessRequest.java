package com.example.recruitment.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/** Body of POST /api/clients/{id}/processes. The list replaces the client's current configuration. */
public record ClientProcessRequest(
        @NotNull(message = "processes is required")
        List<@Valid Item> processes
) {
    public record Item(
            @NotNull(message = "processId is required")
            Long processId,

            /** Defaults to true when omitted. */
            Boolean enabled,

            @Min(value = 1, message = "displayOrder must be 1 or more")
            Integer displayOrder
    ) { }
}
