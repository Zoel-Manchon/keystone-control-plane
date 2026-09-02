package dev.zoel.keystone.application.port.in;

/** Inbound command. Deliberately NOT the REST controller DTO. */
public record RegisterDeviceCommand(String serialNumber, String model) {}
