package net.ixdarklord.coolcatcore.api.config.format;

/**
 * A config file couldn't be parsed. The message says where, when the format knows.
 */
public class ConfigFormatException extends Exception {
    public ConfigFormatException(String message) {
        super(message);
    }

    public ConfigFormatException(String message, Throwable cause) {
        super(message, cause);
    }
}
