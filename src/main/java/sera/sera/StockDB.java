package sera.sera;


import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * Class to communicate with Stock database
 */

public class StockDB {
    public static void insertStock(String stockCode, String market, String name) throws ClassNotFoundException, SQLException {
        // Establish connection
        Class.forName("com.mysql.jdbc.Driver");
        Connection con = DriverManager.getConnection("jdbc:mysql://localhost:3306/seraschema", "root", "Appletree1!");

        // Insert new stock
        PreparedStatement create = con.prepareStatement("insert into seraschema.stock values(?,?,?)");
        create.setString(1, stockCode);
        create.setString(2, market);
        create.setString(3, name);
        create.executeUpdate();

        // Close all the connections
        create.close();
        con.close();
    }


}
