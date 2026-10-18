package dev.starterkit.ai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class StarterKitApplication {

    public static void main(String[] args) {
        SpringApplication.run(StarterKitApplication.class, args);
    }
}
