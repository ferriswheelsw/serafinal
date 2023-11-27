package sera.sera;

import yahoofinance.YahooFinance;

import java.io.*;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static sera.sera.UserDB.insert;
import static sera.sera.UserDB.update;

/**
 * User object stores the login details and stock portfolio of a user
 */

public class User {

    private ArrayList<Stock> stocks; // stores all stocks owned by user
    private int UserID; // unique identifier of user
    private String firstName;
    private String lastName;
    private String email;
    private Blob password;
    private String defaultCurrency; // user can change this in profile page

    private ArrayList<Integer> holdings; // list of corresponding number of holdings for each stock
    private ArrayList<String> markets; // list of corresponding markets for each stock

    // constructor to create a new user with all thier login details
    public User(int userID, String firstName, String lastName, String email, Blob password, String default_currency) {
        this.UserID = userID;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.password = password;
        this.defaultCurrency = default_currency;
    }

    public int getUserID() {
        return UserID;
    }

    public void setUserID(int userID) {
        UserID = userID;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Blob getPassword() {
        return password;
    }

    public void setPassword(Blob password) {
        this.password = password;
    }

    public String getDefaultCurrency() {
        return defaultCurrency;
    }

    public void setDefaultCurrency(String defaultCurrency) throws SQLException, ClassNotFoundException {
        this.defaultCurrency = defaultCurrency;
        update(this.UserID, defaultCurrency);
    }

    public ArrayList<Stock> getStocks() {
        return stocks;
    }

    public void setStocks(ArrayList<Stock> stocks) {
        this.stocks = stocks;
    }

    public ArrayList<Integer> getHoldings() {
        return holdings;
    }

    public void setHoldings(ArrayList<Integer> holdings) {
        this.holdings = holdings;
    }

    public ArrayList<String> getMarkets() {
        return markets;
    }

    public void setMarkets(ArrayList<String> markets) {
        this.markets = markets;
    }

    // method to update the lists above with database information
    public void updatestocks() throws ClassNotFoundException, SQLException {
        // reset all of the lists
        this.stocks = new ArrayList<Stock>();
        List<String> symbols = new ArrayList<>();
        List<Integer> holdings = new ArrayList<>();
        List<String> markets = new ArrayList<>();

        // to look up corresponding market of each stock (stored in separate table than the one storing the list of stocks THIS USER has)
        List<String> symbolPair = new ArrayList<>();
        List<String> marketPair = new ArrayList<>();

        //Establish connection
        Class.forName("com.mysql.jdbc.Driver");
        Connection con = DriverManager.getConnection("jdbc:mysql://localhost:3306/seraschema", "root", "Appletree1!");
        Statement stmt = con.createStatement();

        //Query to get the stocks in this user's portfolio
        String query = "select * from seraschema.user_stock where uid = '" + this.UserID + "'";

        //Executing the query
        ResultSet rs1 = stmt.executeQuery(query);

        // query to get everything from stock table (all the stocks available)
        Statement stmt2 = con.createStatement();
        String query2 = "select * from seraschema.stock";

        ResultSet rs2 = stmt2.executeQuery(query2);
        while (rs2.next()) {
            // add stock symbol and corresponding market into lists
            symbolPair.add(rs2.getString("stockCode"));
            marketPair.add(rs2.getString("market"));
        }

        // loop through each stock the user has and look up the market through result of query 2
        while(rs1.next()){
            symbols.add(rs1.getString("symbol"));
            holdings.add(rs1.getInt("holding"));
            // look up the market and add it to the list
            for (int i=0;i<marketPair.size();i++){
                if (rs1.getString("symbol").equals(symbolPair.get(i))){
                    markets.add(marketPair.get(i));
                }
            }
        }

        // combine symbol, market and no of holdings into one object (a Stock object) and add that to a list
        for(int i=0;i<symbols.size();i++){
            this.stocks.add(new Stock(symbols.get(i), markets.get(i),holdings.get(i)));
        }

        // find number of unique markets and store them as the user's markets
        Set<String> uniquemarket = new HashSet<String>(markets);
        this.markets = new ArrayList<String>(uniquemarket);


        // Close all the connections
        con.close();

    }

    // parse CSV into a nested list of strings
    public static List<List<String>> parseCsv(InputStream csvInput, char csvSeparator) {

        // Prepare
        BufferedReader csvReader = null;
        List<List<String>> csvList = new ArrayList<List<String>>();
        String csvRecord = null;

        // Process csv
        try {
            // initialise new csv reader
            csvReader = new BufferedReader(new InputStreamReader(csvInput, "UTF-8"));
            // call the parseCsvRecord method and add it to the nested list csvList
            while ((csvRecord = csvReader.readLine()) != null) {
                csvList.add(parseCsvRecord(csvRecord, csvSeparator));
            }
        } catch (IOException e) {
            throw new RuntimeException("Reading CSV failed.", e);
        } finally {
            if (csvReader != null) {
                try {
                    csvReader.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        return csvList;
    }

    private static List<String> parseCsvRecord(String record, char csvSeparator) {

        // Prepare
        boolean quoted = false;
        StringBuilder fieldBuilder = new StringBuilder();
        List<String> fields = new ArrayList<String>();

        // Process
        for (int i = 0; i < record.length(); i++) {
            char c = record.charAt(i);
            fieldBuilder.append(c);

            // detect nested quotes
            if (c == '"') {
                quoted = !quoted;
            }

            // if detected separator or at the end of the record
            if ((!quoted && c == csvSeparator) || i + 1 == record.length()) {
                String field = fieldBuilder.toString() // Get the field,
                        .replaceAll(csvSeparator + "$", "") // cut off ending separator,
                        .replaceAll("^\"|\"$", "") // cut off surrounding quotes,
                        .replace("\"\"", "\""); // and the un-escape quotes.
                fields.add(field.trim()); // add field to List
                fieldBuilder = new StringBuilder(); // reset the field builder
            }
        }
        return fields;
    }

    // method to update stock portfolio through CSV uploaded by user (through the profile page)
    public void uploadCSV(InputStream csvIS) throws ClassNotFoundException, SQLException, IOException{
        // establish connection with database
        Class.forName("com.mysql.jdbc.Driver");
        Connection con = DriverManager.getConnection("jdbc:mysql://localhost:3306/seraschema", "root", "Appletree1!");
        // delete all user stocks (so we can totally reset a user's stock portfolio and use the CSV)
        PreparedStatement del = con.prepareStatement("delete from user_stock where uid = ?");
        del.setInt(1, this.getUserID());
        del.executeUpdate();

        // process the CSV using methods defined above
        List<List<String>> input = parseCsv(csvIS, ',');
        String insertQuery = "Insert into user_stock (uid, symbol, holding, emailStatus) values (?,?,?,?)";
        PreparedStatement pstmt = con.prepareStatement(insertQuery);
        // loop through all stocks (each is one row) in the CSV input
        for(int count=1;count<input.size();count++){
            List<String> i = input.get(count);
            // this is one stock
            pstmt.setInt(1,this.getUserID());
            pstmt.setString(2, i.get(0));
            pstmt.setString(3,i.get(1));
            pstmt.setString(4,"null");
            pstmt.addBatch();

            // update stock db if specific stock not already in the db
            String query = "select * from seraschema.stock where stockCode = '"+i.get(0)+"'";
            System.out.println(query);
            Statement stmt = con.createStatement();
            ResultSet rs = stmt.executeQuery(query);
            if (rs.next() != false){
                System.out.println(i.get(1) + "exists");
            }else{
                System.out.println(i.get(1) + "not in");
                yahoofinance.Stock stock = YahooFinance.get(i.get(0));
                String market = stock.getStockExchange();
                String name = stock.getName();
                sera.sera.StockDB.insertStock(i.get(0), market, name);
                System.out.println(i.get(0) + "in");
            }

        }
        pstmt.executeBatch();
        System.out.println("successfully uploaded");

    }


}
