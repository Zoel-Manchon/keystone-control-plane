package dev.zoel.keystone.infrastructure.web;

import dev.zoel.keystone.application.port.in.RegisterDevice;
import dev.zoel.keystone.application.port.in.RevokeDeviceCertificate;
import dev.zoel.keystone.application.port.in.RegisterDeviceCommand;
import dev.zoel.keystone.application.port.out.Clock;
import dev.zoel.keystone.application.port.out.DeviceRepository;
import dev.zoel.keystone.domain.device.Device;
import dev.zoel.keystone.domain.device.DuplicateSerialNumberException;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import dev.zoel.keystone.domain.device.DeviceId;
import dev.zoel.keystone.domain.device.DeviceNotFoundException;
import org.springframework.web.bind.annotation.PathVariable;

import java.time.Instant;
import java.util.List;

/**
 * Adaptador primario para la consola web (Thymeleaf).
 *
 * Convive con DeviceController (la API REST) porque son dos adaptadores
 * distintos sobre el MISMO puerto de entrada. Esa es exactamente la ventaja de
 * la hexagonal: la logica de registrar un dispositivo se escribe una vez.
 */
@Controller
@RequestMapping("/devices")
public class DeviceViewController {

    private final RegisterDevice registerDevice;
    private final RevokeDeviceCertificate revokeCertificate;
    private final DeviceRepository devices;
    private final Clock clock;

    DeviceViewController(RegisterDevice registerDevice, RevokeDeviceCertificate revokeCertificate,
                         DeviceRepository devices, Clock clock) {
        this.registerDevice = registerDevice;
        this.revokeCertificate = revokeCertificate;
        this.devices = devices;
        this.clock = clock;
    }

    @GetMapping
    public String list(Model model) {
        Instant now = clock.now();
        List<DeviceRow> rows = devices.findAll().stream()
                .map(device -> DeviceRow.from(device, now))
                .toList();
        model.addAttribute("devices", rows);
        return "devices/list";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable("id") String id, Model model) {
        DeviceId deviceId = DeviceId.of(id);
        Device device = devices.findById(deviceId)
                .orElseThrow(() -> new DeviceNotFoundException(deviceId));
        model.addAttribute("device", DeviceRow.from(device, clock.now()));
        return "devices/detail";
    }

    /**
     * Revocation is a POST, never a GET: a destructive action reachable by following
     * a link can be triggered by anything that prefetches URLs.
     */
    @org.springframework.web.bind.annotation.PostMapping("/{id}/revoke")
    public String revoke(@PathVariable("id") String id) {
        revokeCertificate.handle(DeviceId.of(id), "revoked from the console");
        return "redirect:/devices/" + id;
    }

    @GetMapping("/new")
    public String newDeviceForm(Model model) {
        model.addAttribute("form", RegisterDeviceForm.empty());
        return "devices/form";
    }

    @PostMapping
    public String register(@Valid @ModelAttribute("form") RegisterDeviceForm form,
            BindingResult binding, Model model) {
        if (binding.hasErrors()) {
            return "devices/form";
        }
        try {
            Device device = registerDevice.handle(
                    new RegisterDeviceCommand(form.serialNumber(), form.model()));
            return "redirect:/devices/" + device.id();
        } catch (DuplicateSerialNumberException ex) {
            model.addAttribute("conflict", ex.getMessage());
            return "devices/form";
        }
    }
}
