package net.ixdarklord.coolcatlib.api.config;

import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TomlConfigReader {
    private final Map<String, String> configMap = new HashMap<>();
    private FileErrorState fileErrorState = FileErrorState.NO_ERROR;

    public TomlConfigReader(String modName, String filePath) {
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
                    Matcher matcher = Pattern.compile("\\[(.*?)]").matcher(line);
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
        } catch (FileNotFoundException e) {
            this.fileErrorState = FileErrorState.FILE_NOT_FOUND;
        } catch (IOException e) {
            this.fileErrorState = FileErrorState.UNABLE_TO_READ;
        } catch (Exception e) {
            this.fileErrorState = FileErrorState.UNEXPECTED_ERROR;
        }
    }

    @Nullable
    public String getResult(String key) {
        if (this.fileErrorState != FileErrorState.NO_ERROR) return null;
        return configMap.get(key);
    }

    public Map<String, String> getConfigMap() {
        return configMap;
    }

    public boolean hasErrorOccurred() {
        return fileErrorState != FileErrorState.NO_ERROR;
    }

    public FileErrorState getFileErrorState() {
        return fileErrorState;
    }

    public enum FileErrorState {
        NO_ERROR,
        FILE_NOT_FOUND,
        UNABLE_TO_READ,
        UNEXPECTED_ERROR
    }
}
