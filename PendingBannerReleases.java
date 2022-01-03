/*
 * PROGRAM:
 * PendingBannerReleases
 *
 * VERSION:
 * 1.3.0
 *
 * DESCRIPTION:
 * This program reads data from both the ESM H2 database and the Banner Oracle database,
 * and determines which releases have not yet been installed for a given product.
 *
 * AUTHOR:
 * Dave Parker
 * Utica College IITS
 *
 * DATE:
 * August 19, 2019
 *
 * CHANGELOG:
 *
 * November 8, 2019:
 * - Version 1.1.0.
 * - Added side-by-side output for up to three Banner database instances.
 *
 * August 25, 2021:
 * - Version 1.2.0.
 * - Removed obsolete releases from the results.
 * - Added ga_releases_only config parameter.
 *
 * December 23, 2021:
 * - Version 1.3.0.
 * - Added checks for missing config values for Oracle databases.
 * - Added code to handle database connection failures for H2 and Oracle.
 */

import java.lang.*;
import java.util.*;
import java.sql.*;
import java.io.*;

public class PendingBannerReleases {
    // Program info
    static final String PROGRAM = "Pending Banner Releases";
    static final String VERSION = "1.3.0";
    static final String AUTHOR = "Dave Parker <dparker@utica.edu>";

    // The file from which to read the database connection info
    static final String CONFIG_FILE = "config.properties";

    //
    // Array of product info
    //
    // Entry format:
    // [Friendly name][ESM name][GURPOST name][GURWADB name][GURWAPP name][*VERS table name]
    //
    static final String[][] products = {
        {"Banner 9x Database Upgrade","BXE_DBU","cxedb","BannerDbUpgrade","",""},
        {"Banner Accounts Receivable","BNR_AR","tas","","AccountsReceivable","TURVERS"},
        {"Banner Admin Common","BNR_ADMCOM","","","AdminCommon",""},
        {"Banner Advancement","BNR_ADV","alu","","Advancement","AURVERS"},
        {"Banner Advancement Self-Service 8","BNR_ADVSS","bwa","","","BWAVERS"},
        {"Banner Application Navigator","BXE_APPNAV","appNav","ApplicationNavigator","ApplicationNavigator",""},
        {"Banner Communication Management","BXE_BCM","bcm","CommunicationManagement","CommunicationManagement",""},
        {"Banner Employee Self-Service 8","BNR_EMPSS","bwp","","","BWPVERS"},
        {"Banner Employee Self-Service 9","BXE_EMPSS","ess","EmployeeSelfService","EmployeeSelfService",""},
        {"Banner Extensibility","BXE_BEXT","bext","BannerExtensibility","BannerExtensibility",""},
        {"Banner Faculty and Advisor Self-Service 8","BNR_FACSS","bwl","","","BWLVERS"},
        {"Banner Faculty Self-Service 9","BXE_FACSS","","FacultySelfService","FacultySelfService",""},
        {"Banner Finance","BNR_FIN","fin","","Finance","FURVERS"},
        {"Banner Finance Self-Service 8","BNR_FINSS","fss","","",""},
        {"Banner Finance Self-Service 9","BXE_FINSS","fss","BannerFinanceSSB","BannerFinanceSSB",""},
        {"Banner Financial Aid","BNR_FINAID","res","","FinancialAid","RURVERS"},
        {"Banner Financial Aid Self-Service 8","BNR_FINAIDSS","bwr","","","BWRVERS"},
        {"Banner General","BNR_GEN","gen","","General","GURVERS"},
        {"Banner General Self-Service","BXE_GENSS","","BannerGeneralSsb","BannerGeneralSsb",""},
        {"Banner HR and Payroll","BNR_HRPAY","pay","","HumanResources","PURVERS"},
        {"Banner Position Control","BNR_POSCTL","pos","","PositionControl","NURVERS"},
        {"Banner Student","BNR_STU","stu","","Student","SURVERS"},
        {"Banner Student eTranscript","BXE_ETRANS","","eTranscript","eTranscript",""},
        {"Banner Student Self-Service 8","BNR_STUSS","bws","","","BWSVERS"},
        {"Banner Student Self-Service 9","BXE_STUSS2","bsss","StudentSelfService","StudentSelfService",""},
        {"Banner Student Registration Self-Service","BXE_STUREGSSB","regssb","StudentRegistrationSsb","StudentRegistrationSsb",""},
        {"Banner Web General","BNR_WEBGEN","bwg","","","BWGVERS"},
        {"Banner Web Tailor","BNR_WEBTLR","twb","","","TWGRVERS"},
    };

    /*
     * These constants are here because I like to refrence things by name so I don't get confused later.
     */

    // Constants for the fields in the products[][] array
    static final int PRODUCT = 0;  // Product name in a friendly format is in products[][0]
    static final int RELEASE = 1;  // Product name from ESM table RELEASE is in products[][1]
    static final int GURPOST = 2;  // Product name from GURPOST is in products[][2]
    static final int GURWADB = 3;  // Product name from GURWADB is in products[][3]
    static final int GURWAPP = 4;  // Product name from GURWAPP is in products[][4]
    static final int VERSTBL = 5;  // Product *VERS table name is in products[][5]

    // Constants for fields in the oracleInfo[] array
    static final int HOST = 0;  // Oracle database host
    static final int PORT = 1;  // Oracle database port
    static final int NAME = 2;  // Oracle database name
    static final int USER = 3;  // Oracle database username
    static final int PASS = 4;  // Oracle database password
    static final int JDBC = 5;  // Oracle database JDBC connection string

    public static void main(String[] args) throws Exception {
        String[][] oracleInfo = new String[3][6];
        List<Map<String,String[]>> pendingReleases = new ArrayList<Map<String,String[]>>();

        System.out.println();
        System.out.println(PROGRAM + " ver. " + VERSION);
        System.out.println("By " + AUTHOR);

        Properties config = new Properties();

        // Read the properties file
        try {
            BufferedReader input = new BufferedReader(new FileReader(CONFIG_FILE));
            config.load(input);
            input.close();
        }
        catch( FileNotFoundException e ) {
            System.err.println( "ERROR: File " + CONFIG_FILE + " does not exist." );
            System.exit(1);
        }
        catch( IOException ioe ) {
            System.err.println( "ERROR: Failed to read file " + CONFIG_FILE + "." );
            System.exit(1);
        }

        // ESM H2 database connection info
        String H2_FILE      = config.getProperty("h2.db.file").trim();
        String H2_FILE_PASS = config.getProperty("h2.db.file.pass").trim();
        String H2_USER      = config.getProperty("h2.db.user").trim();
        String H2_USER_PASS = config.getProperty("h2.db.user.pass").trim();
        String H2_JDBC      = String.format("jdbc:h2:%s;%s", H2_FILE.substring(0,H2_FILE.indexOf(".")), "CIPHER=AES");

        if( ! config.getProperty("orcl.db1.host","").equals("") ) {
            oracleInfo[0][HOST] = config.getProperty("orcl.db1.host").trim();
            oracleInfo[0][PORT] = config.getProperty("orcl.db1.port","").trim();
            oracleInfo[0][NAME] = config.getProperty("orcl.db1.name","").trim();
            oracleInfo[0][USER] = config.getProperty("orcl.db1.user","").trim();
            oracleInfo[0][PASS] = config.getProperty("orcl.db1.pass","").trim();

            if( oracleInfo[0][PORT].equals("") || oracleInfo[0][NAME].equals("") || oracleInfo[0][USER].equals("") || oracleInfo[0][PASS].equals("") ) {
                System.out.println( "ERROR: Missing connection detail for Oracle DB 1. Please check the config." );
                System.exit(1);
            }
            else {
                oracleInfo[0][JDBC] = String.format("jdbc:oracle:thin:@%s:%s:%s", oracleInfo[0][HOST], oracleInfo[0][PORT], oracleInfo[0][NAME]);
            }
        }

        if( ! config.getProperty("orcl.db2.host","").equals("") ) {
            oracleInfo[1][HOST] = config.getProperty("orcl.db2.host").trim();
            oracleInfo[1][PORT] = config.getProperty("orcl.db2.port","").trim();
            oracleInfo[1][NAME] = config.getProperty("orcl.db2.name","").trim();
            oracleInfo[1][USER] = config.getProperty("orcl.db2.user","").trim();
            oracleInfo[1][PASS] = config.getProperty("orcl.db2.pass","").trim();

            if( oracleInfo[1][PORT].equals("") || oracleInfo[1][NAME].equals("") || oracleInfo[1][USER].equals("") || oracleInfo[1][PASS].equals("") ) {
                System.out.println( "ERROR: Missing connection detail for Oracle DB 2. Please check the config." );
                System.exit(1);
            }
            else {
                oracleInfo[1][JDBC] = String.format("jdbc:oracle:thin:@%s:%s:%s", oracleInfo[1][HOST], oracleInfo[1][PORT], oracleInfo[1][NAME]);
            }
        }

        if( ! config.getProperty("orcl.db3.host","").equals("") ) {
            oracleInfo[2][HOST] = config.getProperty("orcl.db3.host").trim();
            oracleInfo[2][PORT] = config.getProperty("orcl.db3.port","").trim();
            oracleInfo[2][NAME] = config.getProperty("orcl.db3.name","").trim();
            oracleInfo[2][USER] = config.getProperty("orcl.db3.user","").trim();
            oracleInfo[2][PASS] = config.getProperty("orcl.db3.pass","").trim();

            if( oracleInfo[2][PORT].equals("") || oracleInfo[2][NAME].equals("") || oracleInfo[2][USER].equals("") || oracleInfo[2][PASS].equals("") ) {
                System.out.println( "ERROR: Missing connection detail for Oracle DB 3. Please check the config." );
                System.exit(1);
            }
            else {
                oracleInfo[2][JDBC] = String.format("jdbc:oracle:thin:@%s:%s:%s", oracleInfo[2][HOST], oracleInfo[2][PORT], oracleInfo[2][NAME]);
            }
        }

        boolean gaReleasesOnly = Boolean.parseBoolean(config.getProperty("ga_releases_only","false").trim());

        // Display the menu and get the user's selection
        int selection = getUserSelection();

        // If the returned value is -2 then exit
        if( selection == -2 ) {
            System.out.println("Quitting.");
            System.out.println();
            System.exit(0);
        }

        System.out.println();

        Connection[] oracleConnections = new Connection[3];
        Connection h2Connection = null;

        // Establish a connection to the H2 (ESM) database
        try {
            Class.forName("org.h2.Driver");
            h2Connection = DriverManager.getConnection(H2_JDBC, H2_USER, H2_FILE_PASS + " " + H2_USER_PASS);
            System.out.println("* Connected to " + H2_FILE + " as " + H2_USER);
            H2_FILE_PASS = new String();
            H2_USER_PASS = new String();
        }
        catch( SQLTimeoutException ste ) {
            System.out.println( "ERROR: Timed out while connecting to " + H2_FILE + "." );
            System.exit(1);
        }
        catch( SQLException se ) {
            System.out.println( "ERROR: Failed to connect to " + H2_FILE + ". Please check the connection details and try again." );
            System.exit(1);
        }

        // Establish a connection to the Oracle (Banner) database
        DriverManager.registerDriver(new oracle.jdbc.OracleDriver());

        for( int x = 0; x < 3; x++ ) {
            if( oracleInfo[x][NAME] != null && oracleInfo[x][NAME].length() > 0 ) {
                try {
                    oracleConnections[x] = DriverManager.getConnection(oracleInfo[x][JDBC], oracleInfo[x][USER], oracleInfo[x][PASS]);
                    System.out.println("* Connected to " + oracleInfo[x][NAME] + " as " + oracleInfo[x][USER]);
                    oracleInfo[x][PASS] = new String();
                }
                catch( SQLTimeoutException ste ) {
                    System.out.println( "ERROR: Timed out while connecting to " + oracleInfo[x][NAME] + ". Please check the database and try again." );
                    h2Connection.close();
                    System.exit(1);
                }
                catch( SQLException se ) {
                    System.out.println( "ERROR: Failed to connect to " + oracleInfo[x][NAME] + ". Please check the connection details and try again." );
                    h2Connection.close();
                    System.exit(1);
                }
            }
        }
	
        // Create a HashMap of the required queries
        HashMap<String,String> queries = new HashMap<>();

        if( gaReleasesOnly ) {
            queries.put(
                "esm_release_by_product",
                "SELECT RELEASE_VERSION FROM RELEASE WHERE STATUS = 'GA' AND PRODUCT_ID = '%s'"
            );
        }
        else {
            queries.put(
                "esm_release_by_product",
                "SELECT RELEASE_VERSION FROM RELEASE WHERE STATUS != 'OBSOLETE' AND PRODUCT_ID = '%s'"
            );
        }

        queries.put(
            "gurwapp_release_by_product",
            "SELECT GURWAPP_RELEASE FROM GURWAPP WHERE GURWAPP_APPLICATION_NAME = '%s'"
        );

        queries.put(
            "gurwadb_release_by_product",
            "SELECT GURWADB_RELEASE FROM GURWADB WHERE GURWADB_APPLICATION_NAME = '%s'"
        );

        queries.put(
            "gurpost_patch_by_product",
            "SELECT GURPOST_PATCH FROM GURPOST WHERE GURPOST_PATCH LIKE 'pcr-%%_%s%%'"
        );

        queries.put(
            "vers_table_release",
            "SELECT %s_RELEASE FROM %s"
        );

        // String to hold the query
        String q = new String();

        // Statements connected to the Oracle and H2 databases
        Statement oracleStatement;
        Statement h2Statement = h2Connection.createStatement();

        // Result sets for the executed statements
        ResultSet h2Result;
        ResultSet oracleResult;

        // Start and end indexes for reading the products[][] array
        int start;
        int end;

        // Width of the product name being displayed
        int width = 0;

        // If selection is -1 then the user chose to see all products
        if( selection == -1 ) {
            start = 0;
            end = products.length;

            // Use the longest product name as the width
            width = getLongestProductName();
        }
        // Otherwise set the indexes so we only get the selected product
        else {
            start = selection;
            end = start+1;
        }

        for( int x = 0; x < 3 ; x++ ) {
            if( oracleConnections[x] != null ) {
                oracleStatement = oracleConnections[x].createStatement();
            }
            else {
                continue;
            }

            // Dynamically-sized arrays to hold the query results
            ArrayList<String> releaseResults = new ArrayList<String>();
            ArrayList<String> gurwadbResults = new ArrayList<String>();
            ArrayList<String> gurwappResults = new ArrayList<String>();
            ArrayList<String> gurpostResults = new ArrayList<String>();
            ArrayList<String> verstblResults = new ArrayList<String>();

            // Map to hold the pending versions information (productName => pendingVersions)
            Map<String, String[]> m = new HashMap<String, String[]>();

            for( int i = start; i < end; i++ ) {
                // Get the info for this product
                String[] p = products[i];
 
                // If no width is set then use the length of this product's name
                if( width == 0 ) {
                    width = p[PRODUCT].length();
                }

                // Get all releases in the RELEASE table for this product
                if( p[RELEASE].length() > 0 ) {
                    q = String.format(queries.get("esm_release_by_product"), p[RELEASE]);
                    h2Result = h2Statement.executeQuery(q);

                    // Populate the RELEASE results array with the query results
                    while( h2Result.next() ) {
                        releaseResults.add(h2Result.getString(1));
                    }

                    h2Result.close();
                }

                // Get all releases in the GURWADB table for this product
                if( p[GURWADB].length() > 0 ) {
                    q = String.format(queries.get("gurwadb_release_by_product"), p[GURWADB]);
                    oracleResult = oracleStatement.executeQuery(q);

                    // Populate the GURWADB results array with the query results
                    while( oracleResult.next() ) {
                        gurwadbResults.add(oracleResult.getString(1));
                    }

                    oracleResult.close();
                }

                // Get all releases in the GURWAPP table for this product
                if( p[GURWAPP].length() > 0 ) {
                    q = String.format(queries.get("gurwapp_release_by_product"), p[GURWAPP]);
                    oracleResult = oracleStatement.executeQuery(q);

                    // Populate the GURWAPP results array with the query results
                    while( oracleResult.next() ) {
                        gurwappResults.add(oracleResult.getString(1));
                    }

                    oracleResult.close();
                }

                // Get all patches in the GURPOST table for this product
                if( p[GURPOST].length() > 0 ) {
                    q = String.format(queries.get("gurpost_patch_by_product"), p[GURPOST]);
                    oracleResult = oracleStatement.executeQuery(q);

                    //
                    // This all gets a bit messy because the release numbers are not stored in a friendly
                    // way in GURPOST.  We have to take something like "pcr-000163330_stu8170002" and
                    // convert that into "8.17.0.2" which requires a lot of string manipulation.
                    //

                    while( oracleResult.next() ) {
                        StringBuilder release = new StringBuilder();

                        String[] patchArr= oracleResult.getString(1).split("_");
                        String tmp = patchArr[1].replace(p[GURPOST], "");
                        String top = tmp.substring(0,1); // The top-level version number (8 or 9)
                        String rev = tmp.substring(1);   // The revision (everything after the release number)

                        // Add the top-level version to the release string
                        release.append(top);

                        // Loop through the revision string
                        for( int j = 0; j < (rev.length()-1); j+=2 ) {
                            // Get the next two characters
                            String num = rev.substring(j,j+2);

                            // If the first character is a 0 then strip it out (e.g., "01" => "1")
                            if( num.startsWith("0") ) {
                                num = num.replaceFirst("0","");
                            }

                            // Append the remaining characters to the release string
                            release.append(".").append(num); 
                        }

                        // Populate the GURPOST results array with the query results
                        gurpostResults.add(release.toString());
                    }

                    oracleResult.close();
                }

                // Get all releases in the *VERS table for this product
                if( p[VERSTBL].length() > 0 ) {
                    q = String.format(queries.get("vers_table_release"), p[VERSTBL], p[VERSTBL]);
                    oracleResult = oracleStatement.executeQuery(q);

                    // Populate the VERSTBL results array with the query results
                    while( oracleResult.next() ) {
                        verstblResults.add(oracleResult.getString(1));
                    }

                    oracleResult.close();
                }

                // Remove the GURWADB, GURWAPP, GURPOST, and *VERS results from the RELEASE results
                releaseResults.removeAll(gurwadbResults);
                releaseResults.removeAll(gurwappResults);
                releaseResults.removeAll(gurpostResults);
                releaseResults.removeAll(verstblResults);

                String[] s1 = new String[]{""};

                if( releaseResults.isEmpty() ) {
                    // Add a placeholder for this product if there were no results
                    m.put(p[PRODUCT], s1);
                }
                else {
                    // Sort the remaining RELEASE results and add them to the map
                    Collections.sort(releaseResults);
                    m.put(p[PRODUCT], releaseResults.toArray(s1));
                }

                // Clear out all of the ArrayLists
                releaseResults.clear();
                gurwadbResults.clear();
                gurwappResults.clear();
                gurpostResults.clear();
                verstblResults.clear();
            }

            pendingReleases.add(x, m);
            oracleStatement.close();
            oracleConnections[x].close();
        }

        h2Statement.close();
        h2Connection.close();

        /*
         * Display the results
         */

        Map<String,String[]> m0 = new HashMap<String,String[]>();
        Map<String,String[]> m1 = new HashMap<String,String[]>();
        Map<String,String[]> m2 = new HashMap<String,String[]>();

        m0 = pendingReleases.get(0);
        if( pendingReleases.size() > 1 ) m1 = pendingReleases.get(1);
        if( pendingReleases.size() > 2 ) m2 = pendingReleases.get(2);

        System.out.println();
        System.out.print(String.format("+-%-" + width + "s-+", " ").replace(" ", "-"));

        for( String[] s : oracleInfo )
            if( s[NAME] != null )
                System.out.print(String.format("-%-" + 20 + "s-+", " ").replace(" ", "-"));

        System.out.println();
        System.out.print(String.format("| %-" + width + "s |", "Product"));

        for( String[] s : oracleInfo )
            if( s[NAME] != null )
                System.out.print(String.format(" %-" + 20 + "s |", s[NAME]));

        System.out.println();
        System.out.print(String.format("+-%-" + width + "s-+", " ").replace(" ", "-"));

        for( String[] s : oracleInfo )
            if( s[NAME] != null )
                System.out.print(String.format("-%-" + 20 + "s-+", " ").replace(" ", "-"));

        for( Map.Entry<String,String[]> entry : m0.entrySet() ) {
            String[] vals0 = new String[]{};
            String[] vals1 = new String[]{};
            String[] vals2 = new String[]{};

            String pName = entry.getKey();

            vals0 = entry.getValue();
            int rows = vals0.length;

            if( ! m1.isEmpty() ) {
                vals1 = m1.get(pName);
                if( vals1.length > rows ) rows = vals1.length;
            }

            if( ! m2.isEmpty() ) {
                vals2 = m2.get(pName);
                if( vals2.length > rows ) rows = vals2.length;
            }

            System.out.println();
            System.out.print(String.format("| %-" + width + "s |", pName));

            if( vals0.length > 0 ) {
                System.out.print(String.format(" %-" + 20 + "s |", vals0[0]));
            }
            else {
                System.out.print(String.format(" %-" + 20 + "s |", ""));
            }

            if( ! m1.isEmpty() ) {
                if( vals1.length > 0 ) {
                    System.out.print(String.format(" %-" + 20 + "s |", vals1[0]));
                }
                else {
                    System.out.print(String.format(" %-" + 20 + "s |", ""));
                }
            }

            if( ! m2.isEmpty() ) {
                if( vals2.length > 0 ) {
                    System.out.print(String.format(" %-" + 20 + "s |", vals2[0]));
                }
                else {
                    System.out.print(String.format(" %-" + 20 + "s |", ""));
                }
            }

            System.out.println();

            for( int z = 1; z < rows; z++ ) {
                System.out.print(String.format("| %-" + width + "s |", ""));

                if( z < vals0.length ) {
                    System.out.print(String.format(" %-" + 20 + "s |", vals0[z]));
                }
                else {
                    System.out.print(String.format(" %-" + 20 + "s |", ""));
                }

                if( vals1.length > 0 ) {
                    if( z < vals1.length ) {
                        System.out.print(String.format(" %-" + 20 + "s |", vals1[z]));
                    }
                    else {
                        System.out.print(String.format(" %-" + 20 + "s |", ""));
                    }
                }

                if( vals2.length > 0 ) {
                    if( z < vals2.length ) {
                        System.out.print(String.format(" %-" + 20 + "s |", vals2[z]));
                    }
                    else {
                        System.out.print(String.format(" %-" + 20 + "s |", ""));
                    }
                }

                System.out.println();
            }

            System.out.print(String.format("+-%-" + width + "s-+", " ").replace(" ", "-"));
            System.out.print(String.format("-%-" + 20 + "s-+", " ").replace(" ", "-"));

            if( ! m1.isEmpty() ) {
                System.out.print(String.format("-%-" + 20 + "s-+", " ").replace(" ", "-"));
            }

            if( ! m2.isEmpty() ) {
                System.out.print(String.format("-%-" + 20 + "s-+", " ").replace(" ", "-"));
            }
        }

        // Exit
        System.out.println();
        System.exit(0);
    }

    /*
     * Display the list of options and get the user's selection.
     *
     * Returns:
     *   -2 for immediate exit, or
     *   -1 for "all products", or
     *   A valid numeric selection
     */
    private static int getUserSelection() {
        System.out.println();

        for( int i = 0; i < products.length; i++ ) {
            System.out.println(String.format("\t[%2d] " + products[i][PRODUCT], i));
        }

        System.out.println();
        System.out.println("\t[a] All of the above");
        System.out.println("\t[q] Quit");
        System.out.println();

        System.out.print("Enter selection: ");

        Scanner s = new Scanner(System.in);
        String in = s.nextLine();
        s.close();

        if( in.toLowerCase().equals("a") ) {
            return -1;
        }
        else if( in.toLowerCase().equals("q") ) {
            return -2;
        }
        else {
            try {
                int selection = Integer.parseInt(in);

                if( selection < 0 || selection >= products.length ) {
                    // A number was entered but it's out of range
                    System.out.println();
                    System.out.println("Invalid selection. Please choose a value from the list.");
                    return -2;
                }

                return selection;
            }
            catch( NumberFormatException nfe ) {
                // A non-number was entered that was not "a" or "q"
                System.out.println();
                System.out.println("Invalid selection. Please choose a value from the list.");
                return -2;
            }
        }
    }

    /*
     * Get the longest product name in the products[][] array.
     *
     * Returns:
     *   The length of the longest string in the PRODUCT field of the products[][] array
     */
    private static int getLongestProductName() {
        int tmp = 0;
        int max = 0;

        for( int i = 0; i < products.length; i++ ) {
            tmp = products[i][PRODUCT].length();

            if( tmp > max ) {
                max = tmp;
            }
        }

        return max;
    }
}
