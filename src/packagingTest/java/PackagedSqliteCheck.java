import java.sql.*;
import java.nio.file.*;

class PackagedSqliteCheck {
    public static void main(String[] args) throws Exception {
        Path file = Files.createTempFile(Path.of(args[0]), "combined-driver-", ".db");
        String url = "jdbc:sqlite:" + file;
        try (Connection connection = DriverManager.getConnection(url);
             Statement statement = connection.createStatement()) {
            System.out.println("Packaged JDBC driver: " + connection.getClass().getName());
            statement.executeUpdate("CREATE TABLE smoke(value TEXT)");
            statement.executeUpdate("INSERT INTO smoke VALUES('saved')");
        }
        try (Connection connection = DriverManager.getConnection(url);
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT value FROM smoke")) {
            if (!result.next() || !"saved".equals(result.getString(1))) throw new AssertionError("Data missing");
        }
        System.out.println("Packaged driver smoke test passed");
    }
}
