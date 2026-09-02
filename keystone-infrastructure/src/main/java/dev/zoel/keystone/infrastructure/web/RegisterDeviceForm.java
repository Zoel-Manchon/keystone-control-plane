package dev.zoel.keystone.infrastructure.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Form backing bean. Same rules as the REST DTO, but with UI-facing messages. */
public record RegisterDeviceForm(

    @NotBlank(message = "Enter the serial number")
    @Size(max = 128, message = "128 characters maximum")
    @Pattern(regexp = "[A-Za-z0-9\\-_]*", message = "Letters, digits, hyphen and underscore only")
    String serialNumber,

    @NotBlank(message = "Enter the model")
    @Size(max = 128, message = "128 characters maximum")
    String model
) {
    public static RegisterDeviceForm empty() {
        return new RegisterDeviceForm("", "");
    }
}
