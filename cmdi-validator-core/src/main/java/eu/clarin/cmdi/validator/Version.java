package eu.clarin.cmdi.validator;

import java.io.IOException;
import java.net.URL;
import java.util.Properties;


public final class Version {
    private static final String VERSION_PROPERTIES_URL = "/version.properties";
    private static final String PROP_VERSION           = "version";
    private static final String PROP_TIMESTAMP         = "timestamp";
    private static final Version INSTANCE;
    private final String version;
    private final String timestamp;


    private Version() {
        String version = null;
        String timestamp = null;

        final URL url = Version.class.getResource(VERSION_PROPERTIES_URL);
        if (url != null) {
            try {
                Properties props = new Properties();
                props.load(url.openStream());
                version   = props.getProperty(PROP_VERSION);
                timestamp = props.getProperty(PROP_TIMESTAMP);
            } catch (IOException e) {
                /* IGNORE */
            }
        }
        if ((version == null) || version.isEmpty() || version.contains("$")) {
            version = "[UNKNOWN]";
        }
        if ((timestamp == null) || timestamp.isEmpty() ||
                timestamp.contains("$")) {
            timestamp = "[UNKNOWN]";
        }

        this.version   = version;
        this.timestamp = timestamp;
    }


    public static String getVersion() {
        return INSTANCE.version;
    }


    public static String getTimestamp() {
        return INSTANCE.timestamp;
    }


    static {
        INSTANCE = new Version();
    }

} // class Version
