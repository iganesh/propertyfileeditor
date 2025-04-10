// src/main/java/com/example/service/PropertyService.java
package com.ganesh.spring.thymeleaf.controller;

import org.springframework.stereotype.Service;

import java.io.*;
import java.util.Properties;

@Service
public class PropertyService {
    private Properties properties;
    private String currentFilePath;

    public PropertyService() {
        properties = new Properties();
    }

    public void loadProperties(String filePath) throws IOException {
        this.currentFilePath = filePath;
        File file = new File(filePath);

        // Create file if it doesn't exist
        if (!file.exists()) {
            file.getParentFile().mkdirs();
            file.createNewFile();
        }

        try (FileInputStream fis = new FileInputStream(filePath)) {
            properties.load(fis);
        }
    }

    public Properties getProperties() {
        return properties;
    }

    public String getCurrentFilePath() {
        return currentFilePath;
    }

    public void updateProperty(String key, String value) throws IOException {
        properties.setProperty(key, value);
        try (FileOutputStream fos = new FileOutputStream(currentFilePath)) {
            properties.store(fos, "Updated properties");
        }
    }
}