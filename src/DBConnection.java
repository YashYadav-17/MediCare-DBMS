import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

public class DBConnection {

    private static Map<String, String> dotEnv = null;

    private static synchronized String getEnvOrDotEnv(String key, String defaultValue) {
        // 1. Check System Environment Variable
        String val = System.getenv(key);
        if (val != null && !val.trim().isEmpty()) {
            return val.trim();
        }

        // 2. Load .env file once if exists
        if (dotEnv == null) {
            dotEnv = new HashMap<>();
            File envFile = new File(".env");
            if (!envFile.exists()) {
                // Also check parent directory if running from src or subfolder
                envFile = new File("../.env");
            }

            if (envFile.exists() && envFile.isFile()) {
                try (BufferedReader reader = new BufferedReader(new FileReader(envFile))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        line = line.trim();
                        if (line.isEmpty() || line.startsWith("#")) continue;
                        int eqIdx = line.indexOf('=');
                        if (eqIdx > 0) {
                            String k = line.substring(0, eqIdx).trim();
                            String v = line.substring(eqIdx + 1).trim();
                            // Strip optional quotes
                            if (v.startsWith("\"") && v.endsWith("\"") && v.length() >= 2) {
                                v = v.substring(1, v.length() - 1);
                            } else if (v.startsWith("'") && v.endsWith("'") && v.length() >= 2) {
                                v = v.substring(1, v.length() - 1);
                            }
                            dotEnv.put(k, v);
                        }
                    }
                } catch (IOException e) {
                    System.err.println("Note: Could not read .env file: " + e.getMessage());
                }
            }
        }

        if (dotEnv.containsKey(key)) {
            return dotEnv.get(key);
        }

        return defaultValue;
    }

    public static Connection getConnection() throws SQLException {
        String url = getEnvOrDotEnv("MYSQL_URL", "jdbc:mysql://localhost:3306/medicare");
        String username = getEnvOrDotEnv("MYSQL_USER", "root");
        String password = getEnvOrDotEnv("MYSQL_PASSWORD", "");

        return DriverManager.getConnection(url, username, password);
    }
}