package dev.zoel.keystone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling   // the CRL refresh and the expiry sweep depend on this
@SpringBootApplication(scanBasePackages = "dev.zoel.keystone.infrastructure")
public class KeystoneApplication {

    public static void main(String[] args) {
        SpringApplication.run(KeystoneApplication.class, args);
    }
}