import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * One place that opens connections to the MySQL database.
 *
 * >>> Change USER and PASSWORD below to match your own MySQL installation. <<<
 */
public class DBConnection {

    private static final String URL =
            "jdbc:mysql://localhost:3306/library_db"
            + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Kolkata";
    private static final String USER = "root";
    private static final String PASSWORD = "root";   // <-- put your MySQL password here

    public static Connection getConnection() throws SQLException {
        // System properties allow overriding the settings without editing code,
        // e.g.  java -Ddb.password=secret -cp ... Main
        return DriverManager.getConnection(
                System.getProperty("db.url", URL),
                System.getProperty("db.user", USER),
                System.getProperty("db.password", PASSWORD));
    }
}
