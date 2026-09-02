package dev.zoel.keystone.simulator;

import org.springframework.boot.WebApplicationType;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Runs the fleet simulation once and exits.
 *
 * No web server: this is a client, not a service. WebApplicationType.NONE keeps it
 * from starting a Tomcat that would sit there doing nothing.
 */
@SpringBootApplication
@EnableConfigurationProperties(SimulatorProperties.class)
public class SimulatorApplication {

    public static void main(String[] args) {
        var context = new SpringApplicationBuilder(SimulatorApplication.class)
            .web(WebApplicationType.NONE)
            .run(args);

        // This is a one-shot CLI. ApplicationRunner has completed before run()
        // returns, so close the Spring context and terminate the JVM explicitly.
        // MQTT clients/libraries may otherwise leave non-daemon housekeeping
        // threads alive after every scenario has already produced its result.
        System.exit(SpringApplication.exit(context));
    }
}
