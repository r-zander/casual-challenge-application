package gg.casualchallenge.application;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertySource;

public class ConfigFileLogger implements EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        for (PropertySource<?> propertySource : environment.getPropertySources()) {
            if (propertySource.getName().startsWith("Config resource")) {
                System.out.println("!!!!!!!!!!!!!!!!!!!!!!!! Loaded configuration file: " + propertySource.getName());
                System.out.println(propertySource.getProperty("custom.name"));
            }
        }
    }
}
