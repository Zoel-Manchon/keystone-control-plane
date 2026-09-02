package dev.zoel.keystone.infrastructure.rest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Inbound DTO, validated at the boundary before the domain is touched. */
public record RegisterDeviceRequest(

    @NotBlank
    @Size(max = 128)
    @Pattern(regexp = "[A-Za-z0-9\\-_]+", message = "serial number accepts only alphanumerics, hyphen and underscore")
    String serialNumber,

    @NotBlank
    @Size(max = 128)
    String model
) {}
