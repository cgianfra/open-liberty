/*
 * IBM Confidential
 *
 * OCO Source Materials
 *
 * WLP Copyright IBM Corp. 2014
 *
 * The source code for this program is not published or otherwise divested 
 * of its trade secrets, irrespective of what has been deposited with the 
 * U.S. Copyright Office.
 */
package batch.fat.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import java.util.Map;

import javax.json.JsonArray;

import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TestName;

import com.ibm.websphere.simplicity.log.Log;
import com.ibm.ws.common.internal.encoder.Base64Coder;
import com.ibm.ws.jbatch.test.dbservlet.DbServletClient;
import componenttest.topology.impl.LibertyServer;
import componenttest.topology.impl.LibertyServerFactory;
import componenttest.topology.utils.HttpUtils;
import componenttest.topology.utils.HttpUtils.HTTPRequestMethod;

/**
 *
 */
public abstract class BatchFATHelper {

    protected final String DFLT_CTX_ROOT = "batchFAT";
    public final static String DFLT_PERSISTENCE_DDL = "common/batch-derby.ddl";
    public final static String DFLT_SERVER_XML = "common/server.xml";
    public final static String DFLT_PERSISTENCE_JNDI = "jdbc/batch";
    public final static String DFLT_PERSISTENCE_SCHEMA = "JBATCH";
    public final static String DFLT_TABLE_PREFIX = "";

    public static final String APP_OUT1 = "APP.OUT1";

    public static final String SUCCESS_MESSAGE = "TEST PASSED";
    protected static final int TIMEOUT = 10000;

    private static String tmpDir = System.getProperty("java.io.tmpdir", "/tmp");

    protected static final LibertyServer server = LibertyServerFactory.getLibertyServer("batchFAT");

    public String _testName = "";

    protected final static String ADMIN_NAME = "bob";
    protected final static String ADMIN_PASSWORD = "bobpwd";

    //Instance fields
    private final Map<String, String> adminHeaderMap = Collections.singletonMap("Authorization", "Basic " + Base64Coder.base64Encode(ADMIN_NAME + ":" + ADMIN_PASSWORD));

    @Rule
    public TestName name = new TestName();

    @Before
    public void setTestName() throws Exception {
        _testName = name.getMethodName();
        Log.info(this.getClass(), _testName, "===== Starting test " + _testName + " =====");
    }

    protected String getTempFilePrefix(String filePrefix) {
        return tmpDir + java.io.File.separator + filePrefix;
    }

    protected String getContextRoot() {
        return DFLT_CTX_ROOT;
    }

    protected String test(String servlet) throws Exception {
        return test(servlet, null);
    }

    protected String test(String servlet, String urlParms) throws Exception {
        return test(server, "", _testName, servlet, urlParms);
    }

    protected String test(LibertyServer server, String appname, String testName, String servlet, String urlParms) throws Exception {
        String urlAppend = (urlParms == null ? "" : "?" + urlParms);

        URL url = new URL("http://" + server.getHostname() + ":" + server.getHttpDefaultPort() +
                          "/" + getContextRoot() + "/" + servlet + urlAppend);

        Log.info(this.getClass(), "test", "About to execute URL: " + url);

        String output = HttpUtils.getHttpResponseAsString(url);

        assertNotNull(output);
        assertNotNull(output.trim());
        assertTrue("' appname:'" + appname + "' output:'" + output + "' testName:'" + testName + "'", output.trim().contains(SUCCESS_MESSAGE));
        return output;
    }

    protected String testWithHttpAuthHeader(String servlet, String urlParms) throws Exception {
        return testWithHttpAuthHeader(server, "", _testName, servlet, urlParms);
    }

    protected String testWithHttpAuthHeader(LibertyServer server, String appname, String testName, String servlet, String urlParms) throws Exception {
        String urlAppend = (urlParms == null ? "" : "?" + urlParms);

        URL url = new URL("http://" + server.getHostname() + ":" + server.getHttpDefaultPort() +
                          "/" + getContextRoot() + "/" + servlet + urlAppend);

        Log.info(this.getClass(), "test", "About to execute URL: " + url);

        HttpURLConnection con = getConnection("/" + getContextRoot() + "/" + servlet + urlAppend,
                                              HttpURLConnection.HTTP_OK,
                                              HTTPRequestMethod.GET,
                                              null,
                                              adminHeaderMap);

        BufferedReader br = HttpUtils.getConnectionStream(con);

        StringBuilder response = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) {
            response.append(line);
        }

        br.close();

        String output = response.toString();

        assertNotNull(output);
        assertNotNull(output.trim());
        assertTrue("' appname:'" + appname + "' output:'" + output + "' testName:'" + testName + "'", output.trim().contains(SUCCESS_MESSAGE));
        return output;
    }

    private static long MYTIMEOUT = 20000;

    protected void setConfigWaitForAppStartBeforeTest(String config, String... msgs) throws Exception {
        setConfig(server, config, _testName, msgs);
        server.waitForStringInLog("CWWKF0011I", MYTIMEOUT);
    }

    protected static void setConfigWaitForAppStartBeforeClass(String config, Class testClass, String... msgs) throws Exception {
        Log.info(testClass, "setConfigWaitForAppStartBeforeClass", "Setting server.xml to: " + config);
        setConfig(server, config, testClass.getSimpleName(), msgs);
        server.waitForStringInLog("CWWKF0011I", MYTIMEOUT);
    }

    /**
     * Apply the config file, restart the server, and wait for the msgs
     * to show up in the log.
     */
    protected static void setConfig(LibertyServer server, String config, String testName, String... msgs) throws Exception {
        server.setServerConfigurationFile(config);
        server.startServer(testName + ".log");
        for (String m : msgs) {
            String s = server.waitForStringInLog(m, TIMEOUT);
            assertNotNull("Message " + m + " was not found in server log", s);
        }
    }

    /**
     * Execute the given SQL against the given dataSource (identified by its jndi)
     * 
     */
    public static void executeSql(String dataSourceJndi, String sql) throws IOException {
        new DbServletClient()
                        .setDataSourceJndi(dataSourceJndi)
                        .setDataSourceUser("user", "pass")
                        .setHostAndPort(server.getHostname(), server.getHttpDefaultPort())
                        .setSql(sql)
                        .executeUpdate();
    }

    /**
     * Execute the given SQL against the given dataSource (identified by its jndi)
     * 
     */
    public static void loadAndExecuteSql(String dataSourceJndi,
                                         String fileName,
                                         String schema,
                                         String tablePrefix) throws IOException {
        new DbServletClient()
                        .setDataSourceJndi(dataSourceJndi)
                        .setDataSourceUser("user", "pass")
                        .setHostAndPort(server.getHostname(), server.getHttpDefaultPort())
                        .loadSql(server.pathToAutoFVTTestFiles + fileName, schema, tablePrefix)
                        .executeUpdate();
    }

    /**
     * @return SQL for CREATEing the input table and INSERTing some values.
     *         This table is used by the Chunk tests.
     */
    public static String getChunkInTableSql() {

        String[] inputVals = { "AAA", "BB", "C", "DDDD", "EEE", "FF", "G", "HHHHH", "IIII", "JJJ", "KK", "L" };

        StringBuilder retMe = new StringBuilder();
        retMe.append("CREATE TABLE APP.INTABLE("
                     + "id BIGINT NOT NULL GENERATED ALWAYS AS IDENTITY (START WITH 1, INCREMENT BY 1) CONSTRAINT APP.INTABLE_PK PRIMARY KEY,"
                     + "name VARCHAR(512));");

        for (String inputVal : inputVals) {
            retMe.append("INSERT INTO APP.INTABLE (name) VALUES('" + inputVal + "');");
        }

        return retMe.toString();
    }

    /**
     * @return SQL for CREATEing the output table. This table is used by Chunk tests.
     */
    public static String getChunkOutTableSql(String tableName) {
        return "CREATE TABLE " + tableName
               + "(name VARCHAR(512) CONSTRAINT " + tableName + "_PK PRIMARY KEY,"
               + "lettercount BIGINT);";
    }

    protected static void restartServerAndWaitForAppStart() throws Exception {
        //restart server
        if (server != null && server.isStarted()) {
            server.restartServer();
        }
        server.waitForStringInLog("CWWKF0011I", MYTIMEOUT);
    }

    protected static void createDefaultRuntimeTables() throws Exception {
        loadAndExecuteSql(DFLT_PERSISTENCE_JNDI,
                          DFLT_PERSISTENCE_DDL,
                          DFLT_PERSISTENCE_SCHEMA,
                          DFLT_TABLE_PREFIX);
    }

    private static String getPort() {
        return System.getProperty("HTTP_default.secure", "8020");
    }

    private static URL getURL(String path) throws MalformedURLException {
        URL myURL = new URL("https://localhost:" + getPort() + path);
        System.out.println("Built URL: " + myURL.toString());
        return myURL;
    }

    protected static HttpURLConnection getConnection(String path, int expectedResponseCode, HTTPRequestMethod method, InputStream streamToWrite, Map<String, String> map) throws IOException {
        return HttpUtils.getHttpConnection(getURL(path), expectedResponseCode, new int[0], TIMEOUT, method, map, streamToWrite);
    }

    /**
     * Method to verify exitStatus for each partition
     * 
     * @param partitions list of partitions returned in the JSON response
     * 
     * @param exitStatusList list of exitStatus for each partitionNumber starting from partitionNumber : 0
     */
    public static void checkPartitionsExitStatus(JsonArray partitions, String... exitStatusList) {
        for (int i = 0; i < exitStatusList.length; i++) {

            assertEquals(exitStatusList[i], partitions.getJsonObject(i).getString("exitStatus"));

        }
    }

}
