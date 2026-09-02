package dev.zoel.keystone.simulator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

/** Drives the whole simulation and prints a report. */
@Component
class FleetSimulation implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger("keystone.simulator");

    private final KeystoneClient client;
    private final AttackScenarios attacks;
    private final MtlsScenarios mtls;
    private final SimulatorProperties properties;

    FleetSimulation(KeystoneClient client, AttackScenarios attacks, MtlsScenarios mtls,
                    SimulatorProperties properties) {
        this.client = client;
        this.attacks = attacks;
        this.mtls = mtls;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        log.info("enrolling {} devices against {}",
            properties.getDeviceCount(), properties.getBaseUrl());

        Instant start = Instant.now();
        List<SimulatedDevice.Result> results = enrolFleet();
        Duration elapsed = Duration.between(start, Instant.now());

        report(results, elapsed);

        if (properties.isRunAttackScenarios()) {
            reportAttacks(attacks.runAll());
        }
        if (properties.isRunMtlsScenarios()) {
            log.info("=== mTLS against the broker ===");
            reportAttacks(mtls.runAll());
        }
    }

    /**
     * One virtual thread per device.
     *
     * newVirtualThreadPerTaskExecutor creates a thread per task rather than reusing a
     * pool, which only makes sense because virtual threads cost a few hundred bytes
     * instead of a megabyte of stack. Bumping deviceCount to 2000 changes nothing
     * structurally here - that is the point worth demonstrating.
     */
    private List<SimulatedDevice.Result> enrolFleet() throws Exception {
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Callable<SimulatedDevice.Result>> tasks = IntStream.range(0, properties.getDeviceCount())
                .<Callable<SimulatedDevice.Result>>mapToObj(
                    i -> () -> new SimulatedDevice(i, client, properties).run())
                .toList();

            List<SimulatedDevice.Result> results = new ArrayList<>();
            for (Future<SimulatedDevice.Result> future : executor.invokeAll(tasks)) {
                try {
                    results.add(future.get());
                } catch (Exception e) {
                    results.add(SimulatedDevice.Result.failed("unknown", e.getMessage()));
                }
            }
            return results;
        }
    }

    private void report(List<SimulatedDevice.Result> results, Duration elapsed) {
        long enrolled = results.stream().filter(SimulatedDevice.Result::success).count();
        long failed = results.size() - enrolled;

        log.info("");
        log.info("=== fleet enrolment ===");
        log.info("enrolled : {}", enrolled);
        log.info("failed   : {}", failed);
        log.info("elapsed  : {} ms ({} ms per device)",
            elapsed.toMillis(), results.isEmpty() ? 0 : elapsed.toMillis() / results.size());

        results.stream()
            .filter(result -> !result.success())
            .limit(5)
            .forEach(result -> log.warn("  {} -> {}", result.serialNumber(), result.failure()));

        results.stream()
            .filter(SimulatedDevice.Result::success)
            .findFirst()
            .ifPresent(sample -> {
                log.info("sample device : {}", sample.serialNumber());
                log.info("  fingerprint : {}", sample.fingerprint());
                log.info("  valid until : {}", sample.notAfter());
            });
    }

    private void reportAttacks(List<AttackScenarios.Outcome> outcomes) {
        log.info("");
        log.info("=== adversarial scenarios ===");
        // A failure here is not a flaky test: it means the control plane accepted
        // something it must refuse.
        for (AttackScenarios.Outcome outcome : outcomes) {
            if (outcome.passed()) {
                log.info("  PASS  {} ({})", outcome.scenario(), outcome.detail());
            } else {
                log.error("  FAIL  {} ({}) <- the control plane accepted this",
                    outcome.scenario(), outcome.detail());
            }
        }
        log.info("");
    }
}
