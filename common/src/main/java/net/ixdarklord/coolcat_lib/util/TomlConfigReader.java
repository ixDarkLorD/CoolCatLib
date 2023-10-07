package net.ixdarklord.coolcat_lib.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TomlConfigReader {
    private final Logger LOGGER;
    private final Map<String, String> configMap = new HashMap<>();;
    private boolean hasErrorOccurred;

    public TomlConfigReader(String modName, String filePath) {
        this.LOGGER = LogManager.getLogger(modName + "/TomlConfigReader");
        parseTOMLFile(filePath);
    }

    private void parseTOMLFile(String filePath) {
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            String currentSection = "";

            while ((line = reader.readLine()) != null) {
                line = line.trim();

                if (line.isEmpty() || line.startsWith("#")) {
                    continue; // Ignore empty lines and comments
                }

                if (line.startsWith("[")) {
                    // Handle section headers
                    Matcher matcher = Pattern.compile("\\[(.*?)\\]").matcher(line);
                    if (matcher.find()) {
                        currentSection = matcher.group(1);
                    }
                } else {
                    // Handle key-value pairs
                    String[] parts = line.split("=", 2);
                    if (parts.length == 2) {
                        String key = currentSection + "." + parts[0].trim();
                        String value = parts[1].trim();
                        configMap.put(key, value);
                    }
                }
            }
        } catch (IOException e) {
            LOGGER.error("Failed to load config file: {}", filePath);
            LOGGER.error("May the game need a restart to fix this issue.", filePath);
            this.hasErrorOccurred = true;
        }
    }

    public String getStringValue(String key, String fallbackValue) {
        if (this.hasErrorOccurred) return fallbackValue;

        String value = configMap.get(key);
        return value != null ? value : fallbackValue;
    }

    public int getIntValue(String key, int fallbackValue) {
        if (this.hasErrorOccurred) return fallbackValue;

        String value = configMap.get(key);
        if (value != null) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                LOGGER.error("This key does not contain an integer value: {}", key);
            }
        }
        return fallbackValue;
    }
}
