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
package batch.jms.fat.junit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;

import javax.batch.runtime.BatchStatus;
import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonNumber;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonStructure;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSession;

import org.junit.Assert;

import com.ibm.websphere.simplicity.ProgramOutput;
import com.ibm.websphere.simplicity.config.ConfigElementList;
import com.ibm.websphere.simplicity.config.DataSource;
import com.ibm.websphere.simplicity.config.DataSourceProperties;
import com.ibm.websphere.simplicity.config.DatabaseStore;
import com.ibm.websphere.simplicity.config.JdbcDriver;
import com.ibm.websphere.simplicity.config.ServerConfiguration;
import com.ibm.websphere.simplicity.log.Log;
import com.ibm.ws.common.internal.encoder.Base64Coder;
import com.ibm.ws.jbatch.test.dbservlet.DbServletClient;

import batch.fat.util.InstanceStateMirrorImage;
import componenttest.common.apiservices.Bootstrap;
import componenttest.common.apiservices.BootstrapProperty;
import componenttest.topology.impl.LibertyServer;
import componenttest.topology.utils.HttpUtils;
import componenttest.topology.utils.HttpUtils.HTTPRequestMethod;

/**
 * This class is copy from com.ibm.ws.jbatch.rest_fat bucket
 * TODO: some consolidation across all batch fat buckets to share utilities
 */
public class BatchRestUtils {

    private final static String LS = System.getProperty("line.separator");
    private final static Class<?> c = BatchRestUtils.class;
    private LibertyServer server = null;

    public static final String JOB_NAME_NOT_SET = "";

    public BatchRestUtils(LibertyServer server) {
        this.server = server;
    }

    public static final String MEDIA_TYPE_TEXT_HTML = "text/html;charset=UTF-8";

    public static final int POLLING_TIMEOUT_MILLISECONDS = 100000;
    public static final String MEDIA_TYPE_APPLICATION_JSON = "application/json; charset=UTF-8";
    public static final int HTTP_TIMEOUT = 30;

    public static final String EXECUTION_ID = "executionId";
    public static final String INSTANCE_ID = "instanceId";

    protected final static String DB_USER = "user";
    protected final static String DB_PASS = "pass";

    // Wait interval for methods that are doing the polling
    private static final int WAIT_FOR_JOB_TO_FINISH_SECONDS = 3 * 60;
    private static final int WAIT_FOR_JOB_TO_START_SECONDS = 60;
    private static final int WAIT_FOR_INSTANCE_STATE_SECONDS = 60;

    public static long execId(JsonObject obj) {
        JsonNumber num = obj.getJsonNumber(EXECUTION_ID);
        if (num == null) {
            throw new IllegalArgumentException("JsonObject: " + obj + " did not contain key = " + EXECUTION_ID);
        }
        return num.longValue();
    }

    public static long instanceId(JsonObject obj) {
        JsonNumber num = obj.getJsonNumber(INSTANCE_ID);
        if (num == null) {
            throw new IllegalArgumentException("JsonObject: " + obj + " did not contain key = " + INSTANCE_ID);
        }
        return num.longValue();
    }

    public static String getJobName(JsonObject obj) {
        return obj.getString("jobName");
    }

    public static String getSubmitter(JsonObject obj) {
        return obj.getString("submitter");
    }

    public static String getAppName(JsonObject obj) {
        return obj.getString("appName");
    }

    /**
     * helper for simple logging.
     */
    private static void log(String method, Object msg) {
        Log.info(BatchRestUtils.class, method, String.valueOf(msg));
    }

    public static JsonObject propertiesToJson(Properties props) {
        JsonObjectBuilder builder = Json.createObjectBuilder();

        for (Iterator iter = props.keySet().iterator(); iter.hasNext();) {
            String key = (String) iter.next();
            builder = builder.add(key, props.getProperty(key));
        }
        return builder.build();
    }

    /**
     * @return the instanceID of the submitted job.
     */
    public JsonObject submitJob(String appName, String jobXMLName) throws Exception {
        JsonObjectBuilder payloadBuilder = Json.createObjectBuilder().add("applicationName", appName).add("jobXMLName", jobXMLName);

        return submitJobWithParameters(appName, jobXMLName, payloadBuilder.build(), BatchRestUtils.buildHeaderMap());
    }

    /**
     * @return the instanceID of the submitted job.
     */
    public JsonObject submitJob(String appName, String jobXMLName, Properties jobParameters) throws Exception {
        JsonObjectBuilder payloadBuilder = Json.createObjectBuilder().add("applicationName", appName).add("jobXMLName", jobXMLName).add("jobParameters",
                                                                                                                                        propertiesToJson(jobParameters));

        return submitJobWithParameters(appName, jobXMLName, payloadBuilder.build(), BatchRestUtils.buildHeaderMap());
    }

    /**
     * @return the instanceID of the submitted job.
     */
    public JsonObject submitJobWithParameters(String appName, String jobXMLName, JsonObject jobSubmitPayload, Map headerMap) throws Exception {

        log("submitJob", "Request: jobSubmitPayload= " + jobSubmitPayload.toString());

        HttpURLConnection con = HttpUtils.getHttpConnection(buildURL("/ibm/api/batch/jobinstances"),
                                                            HttpURLConnection.HTTP_CREATED,
                                                            new int[0],
                                                            HTTP_TIMEOUT,
                                                            HTTPRequestMethod.POST,
                                                            headerMap,
                                                            new ByteArrayInputStream(jobSubmitPayload.toString().getBytes("UTF-8")));

        assertEquals(MEDIA_TYPE_APPLICATION_JSON, con.getHeaderField("Content-Type"));

        JsonObject jobInstance = Json.createReader(con.getInputStream()).readObject();

        log("submitJob", "Response: jsonResponse= " + jobInstance.toString());

        // We do enough checking of jobName so let's not bother enforcing the convention here.
        JsonNumber instanceId = jobInstance.getJsonNumber("instanceId"); // verifies this is a valid number
//        assertEquals("bob", jobInstance.getString("submitter"));

        String amcName = appName + "#" + appName + ".war";
        assertEquals(amcName, jobInstance.getString("appName"));

        return jobInstance;
    }

    /**
     * @param jobInstanceId
     * @param restartJobParms
     * @return jobInstance
     */
    public JsonObject restartJobInstance(long jobInstanceId, Properties restartJobParms) throws IOException {

        log("restartJobInstance", "Request: restart job instance " + jobInstanceId);

        JsonObject restartPayload = Json.createObjectBuilder().add("jobParameters", propertiesToJson(restartJobParms)).build();

        HttpURLConnection con = HttpUtils.getHttpConnection(buildURL("/ibm/api/batch/jobinstances/" + jobInstanceId + "?action=restart"),
                                                            HttpURLConnection.HTTP_OK,
                                                            new int[0],
                                                            HTTP_TIMEOUT,
                                                            HTTPRequestMethod.PUT,
                                                            BatchRestUtils.buildHeaderMap(),
                                                            new ByteArrayInputStream(restartPayload.toString().getBytes("UTF-8")));

        assertEquals(MEDIA_TYPE_APPLICATION_JSON, con.getHeaderField("Content-Type"));

        //OK not to close anything here?
        JsonObject jobInstance = Json.createReader(con.getInputStream()).readObject();

        log("restartJobInstance", "Response: jsonResponse= " + jobInstance.toString());

        // Verify new execution's instance matches original instance
        assertEquals(jobInstanceId, instanceId(jobInstance));

        return jobInstance;
    }

    /**
     * This hinges on an important detail of the URL invoked: that the executions are
     * sorted most-recent to least-recent. This should be documented and treated as
     * a normal, external API (i.e. the behavior should be stable and maintained, etc.).
     *
     * @param jobInstanceId
     * @return A sorted array of job executions
     * @throws IOException
     */
    public JsonArray getJobExecutionsMostRecentFirst(long jobInstanceId) throws IOException {
        // Get job executions for this jobinstance
        HttpURLConnection con = HttpUtils.getHttpConnection(buildURL("/ibm/api/batch/jobinstances/" + jobInstanceId + "/jobexecutions"),
                                                            HttpURLConnection.HTTP_OK,
                                                            new int[0],
                                                            10,
                                                            HTTPRequestMethod.GET,
                                                            BatchRestUtils.buildHeaderMap(),
                                                            null);

        assertEquals(MEDIA_TYPE_APPLICATION_JSON, con.getHeaderField("Content-Type"));

        // Read the response (an array of  job executions)
        JsonArray jsonArray = Json.createReader(con.getInputStream()).readArray();

        log("getJobExecutionsMostRecentFirst", "Response: jsonArray= " + jsonArray.toString());

        return jsonArray;
    }

    /**
     * Throws assertion failure if there is more or less than one job execution.
     *
     * @return the first job execution id associated with the given job instance
     */
    public JsonObject getOnlyJobExecution(long jobInstanceId) throws IOException {

        JsonArray jobExecutions = getJobExecutionsMostRecentFirst(jobInstanceId);

        // Verify the job execution record.
        assertEquals(1, jobExecutions.size());
        return jobExecutions.getJsonObject(0);
    }

    /**
     * @param jobInstance
     * @return execution id
     * @throws IOException
     */
    public long getMostRecentExecutionIdFromInstance(JsonObject jobInstance) throws IOException {
        long jobInstanceId = instanceId(jobInstance);

        JsonArray jobExecutions = getJobExecutionsMostRecentFirst(jobInstanceId);

        JsonObject mostRecent = jobExecutions.getJsonObject(0);

        return execId(mostRecent);

    }

    /**
     * @param server
     * @param executionId
     * @return
     * @throws IOException
     * @throws MalformedURLException
     * @throws
     */
    public JsonObject getJobExecutionFromExecutionId(long jobExecutionId) throws IOException {

        // Get job executions for this jobinstance
        HttpURLConnection con = HttpUtils.getHttpConnection(buildURL("/ibm/api/batch/jobexecutions/" + jobExecutionId),
                                                            HttpURLConnection.HTTP_OK,
                                                            new int[0],
                                                            10,
                                                            HTTPRequestMethod.GET,
                                                            BatchRestUtils.buildHeaderMap(),
                                                            null);

        assertEquals(MEDIA_TYPE_APPLICATION_JSON, con.getHeaderField("Content-Type"));

        // Read the response (a single job execution)
        JsonObject jsonResponse = Json.createReader(con.getInputStream()).readObject();

        return jsonResponse;
    }

    /**
     * Poll the job instance for status until it reaches one of the completed states.
     * Polls once a second. Times out after WAIT_FOR_JOB_TO_FINISH_SECONDS seconds.
     *
     * @return the job instance record (JSON)
     *
     * @throws IllegalStateException times out after WAIT_FOR_JOB_TO_FINISH_SECONDS seconds
     */
    public JsonObject waitForJobInstanceToFinish(long jobInstanceId) throws IOException, InterruptedException {

        return waitForJobInstanceToFinish(jobInstanceId, WAIT_FOR_JOB_TO_FINISH_SECONDS);
    }

    /**
     * Poll the job instance for status until it reaches one of the completed states.
     * Polls once a second. Times out after timeout_s seconds.
     *
     * @return the job instance record (JSON)
     *
     * @throws IllegalStateException times out after timeout_s seconds
     */
    public JsonObject waitForJobInstanceToFinish(long jobInstanceId, int timeout_s) throws IOException, InterruptedException {

        JsonObject jobInstance = null;

        for (int i = 0; i < timeout_s; ++i) {

            // Get jobinstance record
            log("waitForJobInstanceToFinish", "Retrieving status for job instance " + jobInstanceId);

            HttpURLConnection con = HttpUtils.getHttpConnection(buildURL("/ibm/api/batch/jobinstances/" + jobInstanceId),
                                                                HttpURLConnection.HTTP_OK,
                                                                new int[0],
                                                                10,
                                                                HTTPRequestMethod.GET,
                                                                BatchRestUtils.buildHeaderMap(),
                                                                null);

            assertEquals(MEDIA_TYPE_APPLICATION_JSON, con.getHeaderField("Content-Type"));

            jobInstance = Json.createReader(con.getInputStream()).readObject();

            log("waitForJobInstanceToFinish", "Response: jsonResponse= " + jobInstance.toString());

            if (BatchRestUtils.isDone(jobInstance)) {
                return jobInstance;
            } else {
                // Sleep a second then try again
                Thread.sleep(1 * 1000);
            }
        }

        throw new IllegalStateException("Timed out waiting for job instance " + jobInstanceId + " to finish.  Last status: " + jobInstance.toString());
    }

    /**
     * @return a URL to the target server
     */
    public URL buildURL(String path) throws MalformedURLException {
        return buildHttpsURL(server.getHostname(), server.getHttpDefaultSecurePort(), path);
    }

    /**
     * @return an https:// URL for the given host, port, and path.
     */
    public static URL buildHttpsURL(String host, int port, String path) throws MalformedURLException {
        URL retMe = new URL("https://" + host + ":" + port + path);
        log("buildHttpsURL", retMe.toString());
        return retMe;
    }

    /**
     * @return a URL to the target server using its IP address instead of hostname.
     */
    public URL buildURLUsingIPAddr(String path) throws MalformedURLException, UnknownHostException {
        return ("localhost".equals(server.getHostname())) ? buildHttpsURL(InetAddress.getLocalHost().getHostAddress(), server.getHttpDefaultSecurePort(), path) : buildURL(path);
    }

    /**
     * @return map of headers for rest api requests.
     */
    public static Map<String, String> buildHeaderMap() {
        Map<String, String> headers = new HashMap<String, String>();
        headers.put("Authorization", "Basic " + Base64Coder.base64Encode("bob:bobpwd"));
        headers.put("Content-Type", BatchRestUtils.MEDIA_TYPE_APPLICATION_JSON);
        return headers;
    }

    /**
     * @param jsonObject Can be either jobInstance/jobExecution/StepExecution
     *
     * @return true if batchStatus is any of STOPPED, FAILED, COMPLETED, ABANDONED.
     */
    public static boolean isDone(JsonObject jsonObject) {
        String batchStatus = jsonObject.getString("batchStatus");
        return ("STOPPED".equals(batchStatus)
                || "FAILED".equals(batchStatus)
                || "COMPLETED".equals(batchStatus)
                || "ABANDONED".equals(batchStatus));
    }

    /**
     * @param jobExecution
     *
     * @return true if jobExecution.batchStatus is any of STARTED, STOPPED, STOPPING, FAILED, COMPLETED, ABANDONED.
     */
    public static boolean hasStarted(JsonObject jobExecution) {
        String batchStatus = jobExecution.getString("batchStatus");
        return ("STARTED".equals(batchStatus)
                || "STOPPING".equals(batchStatus)
                || isDone(jobExecution));
    }

    /**
     * @param jobInstances
     *
     * @return true if all job instances in the array are in a completed state
     */
    public boolean areAllDone(JsonObject[] jobInstances) throws IOException {
        boolean retMe = true;
        for (JsonObject instance : jobInstances) {
            long instanceId = instance.getJsonNumber("instanceId").longValue();
            retMe = retMe && isDone(getJobInstance(instanceId));
        }
        return retMe;
    }

    public void changeDatabase() throws Exception {
        changeDatabase(server);
    }

    public static void changeDatabase(LibertyServer server) throws Exception {
        ServerConfiguration configuration = server.getServerConfiguration();

        // Change out the <jdbcDriver> references
        ConfigElementList<JdbcDriver> jdbcDriverList = configuration.getJdbcDrivers();
        Iterator<JdbcDriver> jdbcDriverListIterator = jdbcDriverList.iterator();

        while (jdbcDriverListIterator.hasNext()) {
            JdbcDriver jdbcDriver = jdbcDriverListIterator.next();

            jdbcDriver.updateJdbcDriverFromBootstrap(configuration);
        }

        // Change out the <dataSource> references
        ConfigElementList<DataSource> dataSourceList = configuration.getDataSources();
        Iterator<DataSource> dataSourceListIterator = dataSourceList.iterator();

        while (dataSourceListIterator.hasNext()) {
            DataSource dataSource = dataSourceListIterator.next();

            dataSource.updateDataSourceFromBootstrap(configuration);
        }

        server.updateServerConfiguration(configuration);
        if (server.isStarted()) {
            server.waitForConfigUpdateInLogUsingMark(null);
        }

        updateSchemaIfNecessary(server);
    }

    public void updateSchemaIfNecessary() throws Exception {
        updateSchemaIfNecessary(server);
    }

    /*
     * Change the schema value to user1 in case of a oracle database,
     * or dbuser1 in the case of a sql server database
     */
    public static void updateSchemaIfNecessary(LibertyServer server) throws Exception {

        ServerConfiguration config = server.getServerConfiguration();
        Bootstrap bs = Bootstrap.getInstance();
        String dbType = bs.getValue(BootstrapProperty.DB_VENDORNAME.getPropertyName());
        if (dbType != null && (dbType.equalsIgnoreCase("oracle") || dbType.equalsIgnoreCase("sqlserver"))) {

            String user1 = bs.getValue(BootstrapProperty.DB_USER1.getPropertyName());
            for (DatabaseStore ds : config.getDatabaseStores()) {
                ds.setSchema(user1);
            }
        }

        server.updateServerConfiguration(config);
        if (server.isStarted()) {
            server.waitForConfigUpdateInLogUsingMark(null);
        }
    }

    /**
     * @param server
     * @param longValue
     * @return
     * @throws IOException
     */
    public JsonObject getFinalJobExecution(long jobExecutionId) throws IOException {
        long curTime = System.currentTimeMillis();
        long elapsedTime = System.currentTimeMillis() - curTime;

        while (elapsedTime < POLLING_TIMEOUT_MILLISECONDS) {
            JsonObject exec = getJobExecutionFromExecutionId(jobExecutionId);
            if (isDone(exec)) {
                return exec;
            }
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        throw new IllegalStateException("Exceeed timeout without job reaching terminating status.");
    }

    /**
     * @param jobInstance
     * @return
     */
    public long getOnlyExecutionIdFromInstance(JsonObject jobInstance) throws IOException {

        long jobInstanceId = instanceId(jobInstance);
        JsonObject onlyJobExecution = getOnlyJobExecution(jobInstanceId);
        return onlyJobExecution.getJsonNumber("executionId").longValue();
    }

    /**
     * @param instanceId
     * @return the most recent JobExecution record
     */
    public JsonObject waitForJobInstanceToStart(long instanceId) throws IOException, InterruptedException {

        for (int i = 0; i < WAIT_FOR_JOB_TO_START_SECONDS; ++i) {
            JsonArray jobExecutions = getJobExecutionsMostRecentFirst(instanceId);
            if (jobExecutions.size() > 0 && BatchRestUtils.hasStarted(jobExecutions.getJsonObject(0))) {
                return jobExecutions.getJsonObject(0);
            } else {
                Thread.sleep(1 * 1000);
            }
        }

        throw new IllegalStateException("Timed out without for job instance " + instanceId + " to start.");
    }

    /**
     * See
     * {@link BatchRestUtils#waitForNthJobExecution(long, String, int)
     * which this method calls, with numExecsToWaitFor=1
     *
     * @param jobInstanceId
     * @param baseUrl
     *
     * @return the first job execution JSON obj.
     * @throws IllegalStateException if more than 1 executions exist at any point. Will also throw IllegalStateException if we exhaust the wait timeout without seeing the 1st
     *                                   execution.
     */
    public JsonObject waitForFirstJobExecution(long jobInstanceId) throws IOException, InterruptedException {
        return waitForNthJobExecution(jobInstanceId, 1);
    }

    /*
     * Waits for the Nth job execution (1-indexed) to be created by polling.
     *
     * @param jobInstanceId
     *
     * @param baseUrl
     *
     * @param numExecsToWaitFor - Starting at 1 (not zero)
     *
     * @return the most recent job execution JSON obj. This "first most recent" execution is the "Nth-ever execution" from earlier to later in time. It would be the (N-1)th restart
     * execution for N>=2.
     *
     * @throws IllegalStateException if more than N executions exist at any point. Will also throw IllegalStateException if we exhaust the wait timeout without seeing the Nth
     * execution.
     */
    public JsonObject waitForNthJobExecution(final long jobInstanceId, final int numExecsToWaitFor) throws IOException, InterruptedException {

        int NUM_TRIES = 30;
        JsonArray jobExecutions = null;
        String excMsg = null;

        log("waitForNthJobExecution", "Entering - jobInstanceId = " + jobInstanceId + ", numExecsToWaitFor = " + numExecsToWaitFor);

        for (int i = 0; i < NUM_TRIES; ++i) {
            jobExecutions = getJobExecutionsMostRecentFirst(jobInstanceId);
            int numExecs = jobExecutions.size();
            if (numExecs > numExecsToWaitFor) {
                excMsg = "Found: " + numExecs + ", jobExecutions, but was only looking for N = " + numExecsToWaitFor;
                log("waitForNthJobExecution", excMsg);
                throw new IllegalStateException(excMsg);
            } else if (jobExecutions.size() == numExecsToWaitFor) {
                JsonObject retVal = jobExecutions.getJsonObject(0);
                log("waitForNthJobExecution", "Found Nth exec #" + jobExecutions.size() + ", returning response: " + retVal);
                return retVal;
            } else {
                Thread.sleep(1 * 1000);
            }
        }

        excMsg = "waitForNthJobExecution timed out for jobInstanceId " + jobInstanceId + ", waiting for numExecsToWaitFor = " + numExecsToWaitFor + ", but only found = "
                 + jobExecutions.size();
        log("waitForNthJobExecution", excMsg);
        throw new IllegalStateException(excMsg);
    }

    /**
     * Stop a job
     *
     * @param instanceId
     * @return job execution
     * @throws IOException
     * @throws MalformedURLException
     * @throws ProtocolException
     */
    public boolean stopJob(long jobExecutionId) throws ProtocolException, MalformedURLException, IOException {
        log("stopJob", "Request: stop job exection " + jobExecutionId);

        HttpURLConnection con = getHttpConnection(buildURL("/ibm/api/batch/jobexecutions/" + jobExecutionId + "?action=stop"),
                                                  new int[] { HttpURLConnection.HTTP_OK, HttpURLConnection.HTTP_BAD_REQUEST },
                                                  HTTP_TIMEOUT,
                                                  HTTPRequestMethod.PUT,
                                                  BatchRestUtils.buildHeaderMap(),
                                                  null);

        try {
            BufferedReader br = HttpUtils.getConnectionStream(con);
            JsonObject jobExecution = Json.createReader(br).readObject();
            br.close();

            return true;
        } catch (IOException error) {
            BufferedReader br = HttpUtils.getErrorStream(con);
            br = HttpUtils.getErrorStream(con);

            String body = org.apache.commons.io.IOUtils.toString(br);

            br.close();

            log("stopJob", "Response: body= " + body);
            Assert.assertTrue("Actual:" + body, body.contains("is not currently running"));
            return false;
        }

    }

    /**
     * Stop a job
     *
     * @param instanceId
     * @return job execution
     * @throws IOException
     * @throws MalformedURLException
     * @throws ProtocolException
     */
    public boolean stopJob(URL url) throws ProtocolException, MalformedURLException, IOException {
        log("stopJob", "Request: stop job exection for url" + url.toString());

        HttpURLConnection con = getHttpConnection(url,
                                                  new int[] { HttpURLConnection.HTTP_OK, HttpURLConnection.HTTP_BAD_REQUEST },
                                                  HTTP_TIMEOUT,
                                                  HTTPRequestMethod.PUT,
                                                  BatchRestUtils.buildHeaderMap(),
                                                  null);

        try {
            BufferedReader br = HttpUtils.getConnectionStream(con);
            JsonObject jobExecution = Json.createReader(br).readObject();
            br.close();

            return true;
        } catch (IOException error) {
            BufferedReader br = HttpUtils.getErrorStream(con);
            br = HttpUtils.getErrorStream(con);

            String body = org.apache.commons.io.IOUtils.toString(br);

            br.close();

            log("stopJob", "Response: body= " + body);
            Assert.assertTrue("Actual:" + body, body.contains("is not currently running"));
            return false;
        }

    }

    /**
     * This is a modified version of HttpUtils.getHttpConnection() to allow method to specify more than one expected response code
     * This method is used by the stopJob.
     *
     * @param url
     * @param expectedResponseCode
     * @param allowedUnexpectedResponseCodes
     * @param connectionTimeout
     * @param requestMethod
     * @param headers
     * @param streamToWrite
     * @return
     * @throws IOException
     * @throws ProtocolException
     */
    private static HttpURLConnection getHttpConnection(URL url, int expectedResponseCode[], int connectionTimeout,
                                                       HTTPRequestMethod requestMethod, Map<String, String> headers,
                                                       InputStream streamToWrite) throws IOException, ProtocolException {
        Log.info(HttpUtils.class, "getHttpConnection", "Connecting to " + url.toExternalForm() + " expecting http response of " + Arrays.toString(expectedResponseCode) + " in "
                                                       + connectionTimeout
                                                       + " seconds.");

        long startTime = System.currentTimeMillis();
        int timeout = connectionTimeout * 1000; // this is bad practice because it could overflow but the connection timeout on a urlconnection has to fit in an int.
        boolean streamToWriteReset = true; // true for first pass, set after first use.

        int count = 0;
        HttpURLConnection con = null;
        try {
            do {
                // If we've tried and failed to connect
                if (con != null) {
                    // fail if surprised or if streamToWrite unable to retry
                    if ((expectedResponseCode != null
                         && !contains(expectedResponseCode, con.getResponseCode()))
                        || !streamToWriteReset) {
                        String msg = "Expected response " + Arrays.toString(expectedResponseCode) +
                                     ", received " + con.getResponseCode() +
                                     " (" + con.getResponseMessage() +
                                     ") while connecting to " + url;
                        if (!streamToWriteReset)
                            msg += ". Unable to reset streamToWrite";

                        String errorStream = read(con.getErrorStream());

                        // The Liberty 404 page is big and meaningless for our purposes.
                        if (con.getResponseCode() == HttpURLConnection.HTTP_NOT_FOUND &&
                            errorStream.contains("<title>Context Root Not Found</title>")) {
                            msg += ". Error title: Context Root Not Found";
                        } else {
                            msg += ". Error stream: " + errorStream;
                        }

                        AssertionError e = new AssertionError(msg);
                        Log.error(HttpUtils.class, "getHttpConnection", e);
                        throw e;
                    }

                    // fail when time's up
                    if (timeout <= (System.currentTimeMillis() - startTime)) {
                        String msg = "Expected response " + expectedResponseCode +
                                     " within " + connectionTimeout +
                                     " seconds, last received " + con.getResponseCode() +
                                     " (" + con.getResponseMessage() +
                                     ") while connecting to " + url;
                        AssertionError e = new AssertionError(msg);
                        Log.error(HttpUtils.class, "getHttpConnection", e, msg);
                        throw e;
                    }

                    // wait a second and try again
                    try {
                        Log.info(HttpUtils.class, "getHttpConnection",
                                 "Waiting 1s before retry " + count +
                                                                       " to connect to " + url +
                                                                       " due to response " + con.getResponseCode() + ": " + con.getResponseMessage());
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        //swallow the InterruptedException if there is one
                    } finally {
                    }
                }
                con = getHttpConnection(url, timeout, requestMethod);

                //Add additional headers, if any
                if (headers != null) {
                    Iterator<Entry<String, String>> entries = headers.entrySet().iterator();
                    while (entries.hasNext()) {
                        Entry<String, String> entry = entries.next();
                        con.setRequestProperty(entry.getKey(), entry.getValue());
                    }
                }

                //Write bytes if applicable
                if (streamToWrite != null) {
                    OutputStream out = con.getOutputStream();

                    byte[] buffer = new byte[1024];
                    int byteCount = 0;
                    while ((byteCount = streamToWrite.read(buffer)) != -1) {
                        out.write(buffer, 0, byteCount);
                    }
                    // if possible reset stream for retry
                    if (streamToWrite.markSupported()) {
                        streamToWrite.reset();
                    } else {
                        streamToWriteReset = false;
                    }
                }

                con.connect();
                count++;
            } while (!contains(expectedResponseCode, con.getResponseCode()));
            Log.info(HttpUtils.class, "getHttpConnection", "RC=" + con.getResponseCode() + ", Connection established");
            return con;
        } finally {
            Log.info(HttpUtils.class, "getHttpConnection", "Returning after " + count + " attempts to establish a connection.");
        }
    }

    private static String read(InputStream in) throws IOException {
        if (in == null) {
            return null;
        }
        InputStreamReader isr = new InputStreamReader(in);
        BufferedReader br = new BufferedReader(isr);
        StringBuilder builder = new StringBuilder();
        String line;
        while ((line = br.readLine()) != null) {
            builder.append(line);
            builder.append(LS);
        }
        return builder.toString();
    }

    /**
     * This gets an HttpURLConnection to the requested address
     *
     * @param url           The URL to get a connection to
     * @param requestMethod The HTTP request method (GET, POST, HEAD, OPTIONS, PUT, DELETE, TRACE)
     * @return
     * @throws IOException
     * @throws ProtocolException
     */
    private static HttpURLConnection getHttpConnection(URL url, int timeout, HTTPRequestMethod requestMethod) throws IOException, ProtocolException {
        HttpURLConnection con = (HttpURLConnection) url.openConnection();
        con.setDoInput(true);
        con.setDoOutput(true);
        con.setUseCaches(false);
        con.setRequestMethod(requestMethod.toString());
        con.setConnectTimeout(timeout);

        if (con instanceof HttpsURLConnection) {
            ((HttpsURLConnection) con).setHostnameVerifier(new HostnameVerifier() {
                @Override
                public boolean verify(String hostname, SSLSession session) {
                    return true;
                }
            });
        }

        return con;
    }

    /**
     * Return true if haystack contains needle.
     *
     * @param haystack the array to search
     * @param needle   the value to find
     * @return true if the value was found
     */
    private static boolean contains(int[] haystack, int needle) {
        for (int value : haystack) {
            if (needle == value) {
                return true;
            }
        }
        return false;
    }

    /**
     * Run sql directly to db
     *
     * @param server
     * @param dataSourceJndi
     * @param sql
     * @return
     * @throws Exception
     */
    public static String executeSql(LibertyServer server, String dataSourceJndi, String sql) throws Exception {

        String userName = "user";
        String password = "pass";

        ServerConfiguration configuration = server.getServerConfiguration();
        ConfigElementList<DataSource> dataSourcesList = configuration.getDataSources();
        Iterator<DataSource> dataSourcesListIterator = dataSourcesList.iterator();

        while (dataSourcesListIterator.hasNext()) {
            DataSource dataSource = dataSourcesListIterator.next();

            if (dataSource.getJndiName().equals(dataSourceJndi)) {
                Set<DataSourceProperties> dataSourcePropertiesList = dataSource.getDataSourceProperties();
                Iterator<DataSourceProperties> dataSourcePropertiesListIterator = dataSourcePropertiesList.iterator();

                while (dataSourcePropertiesListIterator.hasNext()) {
                    DataSourceProperties dataSourceProperties = dataSourcePropertiesListIterator.next();
                    userName = dataSourceProperties.getUser();
                    password = dataSourceProperties.getPassword();
                    break;
                }
            }

            if (!userName.equals("user"))
                break;
        }

        return new DbServletClient().setDataSourceJndi(dataSourceJndi).setDataSourceUser(userName, password).setHostAndPort(server.getHostname(),
                                                                                                                            server.getHttpDefaultPort()).setSql(sql).executeQuery();
    }

    /**
     * @param appName
     * @param jobName
     * @param string
     * @return
     */
    public JsonObject submitJob(String appName, String jobXMLName, Map headerMap) throws Exception {
        JsonObjectBuilder payloadBuilder = Json.createObjectBuilder().add("applicationName", appName).add("jobXMLName", jobXMLName);

        return submitJobWithParameters(appName, jobXMLName, payloadBuilder.build(), headerMap);
    }

    /**
     * @return
     */
    public static Map<String, String> buildHeaderMapJane() {
        Map<String, String> headers = new HashMap<String, String>();
        headers.put("Authorization", "Basic " + Base64Coder.base64Encode("jane:janepwd"));
        headers.put("Content-Type", BatchRestUtils.MEDIA_TYPE_APPLICATION_JSON);
        return headers;
    }

    /**
     * @return
     */
    public static Map<String, String> buildHeaderMapChuck() {
        Map<String, String> headers = new HashMap<String, String>();
        headers.put("Authorization", "Basic " + Base64Coder.base64Encode("chuck:chuckpwd"));
        headers.put("Content-Type", BatchRestUtils.MEDIA_TYPE_APPLICATION_JSON);
        return headers;
    }

    /**
     * Wait for job instance state to become a final state
     *
     * @param instanceId
     */
    public JsonObject waitForFinalJobInstanceState(long jobInstanceId) throws IOException, InterruptedException {

        JsonObject jobInstance = null;
        for (int i = 0; i < WAIT_FOR_INSTANCE_STATE_SECONDS; ++i) {

            // Get jobinstance record
            log("waitForFinalJobInstanceState", " job instance " + jobInstanceId);

            HttpURLConnection con = HttpUtils.getHttpConnection(buildURL("/ibm/api/batch/jobinstances/" + jobInstanceId),
                                                                HttpURLConnection.HTTP_OK,
                                                                new int[0],
                                                                10,
                                                                HTTPRequestMethod.GET,
                                                                BatchRestUtils.buildHeaderMap(),
                                                                null);

            assertEquals(MEDIA_TYPE_APPLICATION_JSON, con.getHeaderField("Content-Type"));

            jobInstance = Json.createReader(con.getInputStream()).readObject();

            log("waitForFinalJobInstanceState", "Response: jsonResponse= " + jobInstance.toString());

            if (BatchRestUtils.isFinalInstanceState(jobInstance)) {
                return jobInstance;
            } else {
                // Sleep a second then try again
                Thread.sleep(1 * 1000);
            }
        }

        throw new RuntimeException("Timed out waiting for job instance " + jobInstanceId + " to finish.  Last instance state: " + jobInstance.toString());
    }

    /**
     * Wait for job instance state to become a final state
     *
     * @param instanceId
     * @param waitTime
     */
    public JsonObject waitForFinalJobInstanceState(long jobInstanceId, int waitTime) throws IOException, InterruptedException {

        JsonObject jobInstance = null;
        for (int i = 0; i < waitTime; ++i) {

            // Get jobinstance record
            log("waitForFinalJobInstanceState", " job instance " + jobInstanceId);

            HttpURLConnection con = HttpUtils.getHttpConnection(buildURL("/ibm/api/batch/jobinstances/" + jobInstanceId),
                                                                HttpURLConnection.HTTP_OK,
                                                                new int[0],
                                                                10,
                                                                HTTPRequestMethod.GET,
                                                                BatchRestUtils.buildHeaderMap(),
                                                                null);

            assertEquals(MEDIA_TYPE_APPLICATION_JSON, con.getHeaderField("Content-Type"));

            jobInstance = Json.createReader(con.getInputStream()).readObject();

            log("waitForFinalJobInstanceState", "Response: jsonResponse= " + jobInstance.toString());

            if (BatchRestUtils.isFinalInstanceState(jobInstance)) {
                return jobInstance;
            } else {
                // Sleep a second then try again
                Thread.sleep(1 * 1000);
            }
        }

        throw new RuntimeException("Timed out waiting for job instance " + jobInstanceId + " to finish.  Last instance state: " + jobInstance.toString());
    }

    /**
     * @param jobInstance
     * @return
     */
    private static boolean isFinalInstanceState(JsonObject jobInstance) {
        String instanceState = jobInstance.getString("instanceState");
        return (InstanceStateMirrorImage.STOPPED.toString().equals(instanceState)
                || InstanceStateMirrorImage.FAILED.toString().equals(instanceState)
                || InstanceStateMirrorImage.COMPLETED.toString().equals(instanceState)
                || InstanceStateMirrorImage.ABANDONED.toString().equals(instanceState));
    }

    /**
     * Asserts that actualJobName is equal to one of two things:
     * 1) the expectedJobName parm
     * or
     * 2) the not set value of "" (empty string)
     *
     * @param expectedJobName
     * @param actualJobName
     */
    public static void assertJobNamePossiblySet(String expectedJobName, String actualJobName) {
        // I'm sure there's a more elegant JUnit API here.
        if (!actualJobName.equals(JOB_NAME_NOT_SET)) {
            assertEquals(expectedJobName, actualJobName);
        }
    }

    /**
     * Submit a purge request
     *
     * @param jobInstanceId
     * @throws Exception
     */
    public void purgeJobInstance(long jobInstanceId, int expectedResponse) throws Exception {
        String method = "purgeJobInstance";

        // Purge the job instance.
        HttpURLConnection // Attempt to purge the now-deleted job instance.
        con = getConnection("/ibm/api/batch/jobinstances/" + jobInstanceId,
                            expectedResponse,
                            HTTPRequestMethod.DELETE,
                            null,
                            BatchRestUtils.buildHeaderMap());

        if (con.getResponseCode() == HttpURLConnection.HTTP_OK) {
            logReaderContents(method, "Successful purge response: ", HttpUtils.getConnectionStream(con));
        }
    }

    // Default call, expect HTTP_OK
    public void purgeJobInstance(long jobInstanceId) throws Exception {
        purgeJobInstance(jobInstanceId, HttpURLConnection.HTTP_OK);
    }

    private void logReaderContents(String method, String prefix, BufferedReader reader) throws IOException {
        StringBuilder sb = new StringBuilder();
        String line;
        String nl = "";
        while ((line = reader.readLine()) != null) {
            sb.append(nl);
            sb.append(line);
            nl = System.getProperty("line.separator");
        }
        log(method, prefix + sb.toString());
    }

    private String getPort() {
        return Integer.toString(server.getHttpDefaultSecurePort());
    }

    private URL getURL(String path) throws MalformedURLException {
        URL myURL = new URL("https://localhost:" + getPort() + path);
        return myURL;
    }

    protected HttpURLConnection getConnection(String path, int expectedResponseCode, HTTPRequestMethod method, InputStream streamToWrite,
                                              Map<String, String> map) throws IOException {
        return HttpUtils.getHttpConnection(getURL(path), expectedResponseCode, new int[0], HTTP_TIMEOUT, method, map, streamToWrite);
    }

    /**
     * @param jobInstanceId
     */
    public void purgeJobInstanceFromDBOnly(long jobInstanceId) throws Exception {
        String method = "purgeJobInstanceFromDBOnly";

        // Purge the job instance.
        HttpURLConnection con = getConnection("/ibm/api/batch/jobinstances/" + jobInstanceId + "?purgeJobStoreOnly=true",
                                              HttpURLConnection.HTTP_OK,
                                              HTTPRequestMethod.DELETE,
                                              null,
                                              BatchRestUtils.buildHeaderMap());

        logReaderContents(method, "Successful purge response: ", HttpUtils.getConnectionStream(con));

    }

    /**
     * @param jobInstanceId
     * @param jobExecutionId
     * @param restartJobParms
     * @return jobInstance
     */
    public void restartJobExecutionExpectHttpConflict(long jobInstanceId, long jobExecutionId, Properties restartJobParms) throws IOException {
        JsonObject restartPayload = Json.createObjectBuilder().add("jobParameters", propertiesToJson(restartJobParms)).build();

        HttpURLConnection con = HttpUtils.getHttpConnection(buildURL("/ibm/api/batch/jobexecutions/" + jobExecutionId + "?action=restart"),
                                                            HttpURLConnection.HTTP_CONFLICT,
                                                            new int[0],
                                                            10,
                                                            HTTPRequestMethod.PUT,
                                                            BatchRestUtils.buildHeaderMap(),
                                                            new ByteArrayInputStream(restartPayload.toString().getBytes("UTF-8")));

        assertEquals(MEDIA_TYPE_TEXT_HTML, con.getHeaderField("Content-Type"));

    }

    /**
     * @return a non-empty JsonArray of jobexecutions
     */
    /*
     * public JsonArray waitForFirstJobExecution(long jobInstanceId) throws IOException, InterruptedException {
     *
     * for (int i = 0; i < 30; ++i) {
     * JsonArray jobExecutions = getJobExecutionsMostRecentFirst(jobInstanceId);
     *
     * if (jobExecutions.isEmpty()) {
     * Thread.sleep(1 * 1000);
     * } else {
     * return jobExecutions;
     * }
     * }
     *
     * throw new RuntimeException("waitForFirstJobExecution timed out for jobInstanceId " + jobInstanceId);
     * }
     */

    /**
     * Poll the job execution for status until its batchStatus changes to STARTED.
     * Polls once a second. Times out after 30 seconds.
     *
     * @return the job execution record (JSON)
     *
     * @throws RuntimeException times out after 30 seconds
     */
    public JsonObject waitForJobExecutionToStart(long jobExecutionId) throws IOException, InterruptedException {

        JsonObject jobExecution = null;

        for (int i = 0; i < 30; ++i) {

            // Get jobexecution record
            log("waitForJobExecutionToStart", "Retrieving status for job instance " + jobExecutionId);

            HttpURLConnection con = HttpUtils.getHttpConnection(buildURL("/ibm/api/batch/jobexecutions/" + jobExecutionId),
                                                                HttpURLConnection.HTTP_OK,
                                                                new int[0],
                                                                10,
                                                                HTTPRequestMethod.GET,
                                                                BatchRestUtils.buildHeaderMap(),
                                                                null);

            assertEquals(MEDIA_TYPE_APPLICATION_JSON, con.getHeaderField("Content-Type"));

            jobExecution = Json.createReader(con.getInputStream()).readObject();

            log("waitForJobExecutionToStart", "Response: jsonResponse= " + jobExecution.toString());

            if (jobExecution.getString("batchStatus").equals(BatchStatus.STARTED.toString())) {
                return jobExecution;
            } else {
                // Sleep a second then try again
                Thread.sleep(1 * 1000);
            }
        }

        throw new IllegalStateException("Timed out waiting for job execution " + jobExecutionId + " to start.  Last status: " + jobExecution.toString());
    }

    /**
     * @param jobInstance
     * @param jobExecutionId
     * @param restartJobParms
     * @return jobInstance
     */
    public JsonObject restartJobExecution(JsonObject jobInstance, long jobExecutionId, Properties restartJobParms) throws IOException {

        long jobInstanceId = jobInstance.getJsonNumber("instanceId").longValue();

        JsonObject restartPayload = Json.createObjectBuilder().add("jobParameters", propertiesToJson(restartJobParms)).build();

        HttpURLConnection con = HttpUtils.getHttpConnection(buildURL("/ibm/api/batch/jobexecutions/" + jobExecutionId + "?action=restart"),
                                                            HttpURLConnection.HTTP_OK,
                                                            new int[0],
                                                            10,
                                                            HTTPRequestMethod.PUT,
                                                            BatchRestUtils.buildHeaderMap(),
                                                            new ByteArrayInputStream(restartPayload.toString().getBytes("UTF-8")));

        assertEquals(MEDIA_TYPE_APPLICATION_JSON, con.getHeaderField("Content-Type"));

        //OK not to close anything here?
        jobInstance = Json.createReader(con.getInputStream()).readObject();

        log("restartJobInstance", "Response: jsonResponse= " + jobInstance.toString());

        // Verify new execution's instance matches original instance
        assertEquals(jobInstanceId, jobInstance.getJsonNumber("instanceId").longValue());

        return jobInstance;
    }

    /**
     * Expects response code 409 instead of 200
     *
     * @param jobInstanceId
     * @param restartJobParms
     * @return jobInstance
     */
    public void restartJobInstanceExpectHttpConflict(long jobInstanceId, Properties restartJobParms) throws IOException {

        JsonObject restartPayload = Json.createObjectBuilder().add("jobParameters", propertiesToJson(restartJobParms)).build();

        HttpURLConnection con = HttpUtils.getHttpConnection(buildURL("/ibm/api/batch/jobinstances/" + jobInstanceId + "?action=restart"),
                                                            HttpURLConnection.HTTP_CONFLICT,
                                                            new int[0],
                                                            10,
                                                            HTTPRequestMethod.PUT,
                                                            BatchRestUtils.buildHeaderMap(),
                                                            new ByteArrayInputStream(restartPayload.toString().getBytes("UTF-8")));

        System.out.println(con.getHeaderField("Content-Type"));
        assertEquals(MEDIA_TYPE_TEXT_HTML, con.getHeaderField("Content-Type"));

    }

    /**
     * Poll the job execution for status until its batchStatus changes to STOPPED, COMPLETED, ABANDONED or FAILED
     * Polls once a second. Times out after 30 seconds.
     *
     * @param jobExecutionId
     * @param timeout_s      timeout value in seconds (Default 30)
     *
     * @return the job execution record (JSON)
     *
     */

    public JsonObject stopJobExecutionAndWaitUntilDone(long jobExecutionId, long... timeout_s) throws IOException, InterruptedException {
        long timeout = 30;

        if (timeout_s.length == 1) {
            timeout = timeout_s[0];
        }

        HttpURLConnection con = HttpUtils.getHttpConnection(buildURL("/ibm/api/batch/jobexecutions/" + jobExecutionId + "?action=stop"),
                                                            HttpURLConnection.HTTP_OK,
                                                            new int[0],
                                                            10,
                                                            HTTPRequestMethod.PUT,
                                                            BatchRestUtils.buildHeaderMap(),
                                                            null);

        assertEquals(MEDIA_TYPE_APPLICATION_JSON, con.getHeaderField("Content-Type"));

        //OK not to close anything here?
        JsonObject jobExecution = Json.createReader(con.getInputStream()).readObject();

        log("stopJobExecutionAndWaitUntilDone", "Response: jsonResponse= " + jobExecution.toString());

        assertEquals(jobExecutionId, jobExecution.getJsonNumber("executionId").longValue());

        for (int i = 0; i < timeout; ++i) {

            // Get jobExecution record
            log("stopJobExecutionAndWaitUntilDone", "Retrieving status for job execution " + jobExecutionId);

            con = HttpUtils.getHttpConnection(buildURL("/ibm/api/batch/jobexecutions/" + jobExecutionId),
                                              HttpURLConnection.HTTP_OK,
                                              new int[0],
                                              10,
                                              HTTPRequestMethod.GET,
                                              BatchRestUtils.buildHeaderMap(),
                                              null);

            assertEquals(MEDIA_TYPE_APPLICATION_JSON, con.getHeaderField("Content-Type"));

            jobExecution = Json.createReader(con.getInputStream()).readObject();

            log("stopJobExecutionAndWaitUntilDone", "Response: jsonResponse= " + jobExecution.toString());

            BatchStatus batchStatus = BatchStatus.valueOf(jobExecution.getString("batchStatus"));
            if (batchStatus.equals(BatchStatus.STOPPED) || batchStatus.equals(BatchStatus.COMPLETED) || batchStatus.equals(BatchStatus.FAILED)
                || batchStatus.equals(BatchStatus.ABANDONED)) {
                return jobExecution;
            } else {
                Thread.sleep(1 * 1000);
            }
        }
        return jobExecution;
    }

    /**
     * @param server
     * @param executionId
     * @return
     * @throws IOException
     * @throws MalformedURLException
     * @throws
     */
    public JsonArray getStepExecutionFromExecutionIdAndStepName(long jobExecutionId, String stepName) throws IOException {

        // Get job executions for this step
        HttpURLConnection con = HttpUtils.getHttpConnection(buildURL("/ibm/api/batch/jobexecutions/" + jobExecutionId + "/stepexecutions/" + stepName),
                                                            HttpURLConnection.HTTP_OK,
                                                            new int[0],
                                                            10,
                                                            HTTPRequestMethod.GET,
                                                            BatchRestUtils.buildHeaderMap(),
                                                            null);

        assertEquals(MEDIA_TYPE_APPLICATION_JSON, con.getHeaderField("Content-Type"));

        // Read the response (a single job execution)
        JsonArray jsonResponse = Json.createReader(con.getInputStream()).readArray();

        log("getStepExecutionFromExecutionIdAndStepName", "Response: " + jsonResponse);

        return jsonResponse;
    }

    /*
     * @param jobInstanceId
     *
     * @returns jobInstance
     */
    public JsonObject getJobInstance(long jobInstanceId) throws IOException {

        // Get job executions for this step
        HttpURLConnection con = HttpUtils.getHttpConnection(buildURL("/ibm/api/batch/jobinstances/" + jobInstanceId),
                                                            HttpURLConnection.HTTP_OK,
                                                            new int[0],
                                                            10,
                                                            HTTPRequestMethod.GET,
                                                            BatchRestUtils.buildHeaderMap(),
                                                            null);

        assertEquals(MEDIA_TYPE_APPLICATION_JSON, con.getHeaderField("Content-Type"));

        // Read the response (a single job execution)
        JsonObject jsonResponse = Json.createReader(con.getInputStream()).readObject();

        log("getJobInstance", "Response: " + jsonResponse);

        return jsonResponse;
    }

    /**
     * @param server
     * @param executionId
     * @return
     * @throws IOException
     * @throws MalformedURLException
     * @throws
     */
    public JsonArray getStepExecutionsFromExecutionId(long jobExecutionId) throws IOException {

        // Get job executions for this step
        HttpURLConnection con = HttpUtils.getHttpConnection(buildURL("/ibm/api/batch/jobexecutions/" + jobExecutionId + "/stepexecutions/"),
                                                            HttpURLConnection.HTTP_OK,
                                                            new int[0],
                                                            10,
                                                            HTTPRequestMethod.GET,
                                                            BatchRestUtils.buildHeaderMap(),
                                                            null);

        assertEquals(MEDIA_TYPE_APPLICATION_JSON, con.getHeaderField("Content-Type"));

        // Read the response (a single job execution)
        JsonArray jsonResponse = Json.createReader(con.getInputStream()).readArray();

        log("getStepExecutionsFromExecutionId", "Response: " + jsonResponse);

        return jsonResponse;
    }

    /**
     * @param server
     * @param executionId
     * @return
     * @throws IOException
     * @throws MalformedURLException
     * @throws
     */
    public JsonArray getStepExecutionFromStepExecutionId(long stepExecutionId) throws IOException {

        // Get job executions for this step
        HttpURLConnection con = HttpUtils.getHttpConnection(buildURL("/ibm/api/batch/stepexecutions/" + stepExecutionId),
                                                            HttpURLConnection.HTTP_OK,
                                                            new int[0],
                                                            10,
                                                            HTTPRequestMethod.GET,
                                                            BatchRestUtils.buildHeaderMap(),
                                                            null);

        assertEquals(MEDIA_TYPE_APPLICATION_JSON, con.getHeaderField("Content-Type"));

        // Read the response (a single job execution)
        JsonArray jsonResponse = Json.createReader(con.getInputStream()).readArray();

        log("getStepExecutionFromStepExecutionId", "Response: " + jsonResponse);

        return jsonResponse;
    }

    /**
     * Waits for step to reach a terminating batch status, (one of COMPLETED, STOPPED, ABANDONED, FAILED)
     *
     * @throws InterruptedException
     * @param server
     * @param executionId
     * @return
     * @throws IOException
     * @throws MalformedURLException
     * @throws
     */
    public JsonObject waitForStepExecutionToBeDone(long stepExecutionId) throws IOException, InterruptedException {

        JsonObject stepExecution = Json.createObjectBuilder().build();
        for (int i = 0; i < 30; i++) {

            // Get job executions for this step
            HttpURLConnection con = HttpUtils.getHttpConnection(buildURL("/ibm/api/batch/stepexecutions/" + stepExecutionId),
                                                                HttpURLConnection.HTTP_OK,
                                                                new int[0],
                                                                10,
                                                                HTTPRequestMethod.GET,
                                                                BatchRestUtils.buildHeaderMap(),
                                                                null);

            assertEquals(MEDIA_TYPE_APPLICATION_JSON, con.getHeaderField("Content-Type"));

            // Read the response (a single job execution)
            stepExecution = Json.createReader(con.getInputStream()).readArray().getJsonObject(0);

            log("waitForStepExecutionToComplete", "Response: " + stepExecution);

            if (isDone(stepExecution)) {
                return stepExecution;
            } else {
                Thread.sleep(1 * 1000);
            }
        }
        throw new IllegalStateException("Timed out waiting for step execution " + stepExecutionId + " to finish.  Last state: " + stepExecution.toString());
    }

    /**
     * @throws InterruptedException
     * @param server
     * @param executionId
     * @return
     * @throws IOException
     * @throws MalformedURLException
     * @throws
     */
    public JsonObject waitForStepExecutionToStart(long stepExecutionId) throws IOException, InterruptedException {

        JsonObject stepExecution = Json.createObjectBuilder().build();
        for (int i = 0; i < 30; i++) {

            // Get job executions for this step
            HttpURLConnection con = HttpUtils.getHttpConnection(buildURL("/ibm/api/batch/stepexecutions/" + stepExecutionId),
                                                                HttpURLConnection.HTTP_OK,
                                                                new int[0],
                                                                10,
                                                                HTTPRequestMethod.GET,
                                                                BatchRestUtils.buildHeaderMap(),
                                                                null);

            assertEquals(MEDIA_TYPE_APPLICATION_JSON, con.getHeaderField("Content-Type"));

            // Read the response (a single job execution)
            stepExecution = Json.createReader(con.getInputStream()).readArray().getJsonObject(0);

            log("waitForStepExecutionToStart", "Response: " + stepExecution);

            if (stepExecution.getString("batchStatus").equals(BatchStatus.STARTED.toString())) {
                return stepExecution;
            } else {
                Thread.sleep(1 * 1000);
            }
        }
        throw new IllegalStateException("Timed out waiting for step to start.  Last state: " + stepExecution.toString());
    }

    // Doesn't require step execution to exist to start waiting
    public JsonObject waitForStepInJobExecutionToStart(long jobExecutionId, String stepName) throws IOException, InterruptedException {
        return waitForStepInJobExecutionToReachStatus(jobExecutionId, stepName, BatchStatus.STARTED);
    }

    // Doesn't require step execution to exist to start waiting
    public JsonObject waitForStepInJobExecutionToComplete(long jobExecutionId, String stepName, int secondsToWait) throws IOException, InterruptedException {
        JsonObject stepExecution = waitForStepInJobExecutionToFinish(jobExecutionId, stepName, secondsToWait);
        String batchStatus = stepExecution.getString("batchStatus");
        if (batchStatus.equals(BatchStatus.COMPLETED.toString())) {
            return stepExecution;
        } else {
            throw new IllegalStateException("Expected COMPLETED state, found terminating state of: " + batchStatus);
        }
    }

    // Doesn't require step execution to exist to start waiting
    public JsonObject waitForStepInJobExecutionToComplete(long jobExecutionId, String stepName) throws IOException, InterruptedException {
        return waitForStepInJobExecutionToComplete(jobExecutionId, stepName, 75);
    }

    // Doesn't require step execution to exist to start waiting
    public JsonObject waitForStepInJobExecutionToReachStatus(long jobExecutionId, String stepName, BatchStatus batchStatus) throws IOException, InterruptedException {

        JsonObject stepExecution = Json.createObjectBuilder().build();
        int[] possibleResponse = new int[1];
        possibleResponse[0] = HttpURLConnection.HTTP_INTERNAL_ERROR;
        for (int i = 0; i < 75; i++) {

            log("waitForStepInJobExecutionToReachStatus", "Begin loop for status = " + batchStatus);

            // Get job executions for this step
            HttpURLConnection con = HttpUtils.getHttpConnection(buildURL("/ibm/api/batch/jobexecutions/" + jobExecutionId + "/stepexecutions"),
                                                                HttpURLConnection.HTTP_OK,
                                                                possibleResponse,
                                                                10,
                                                                HTTPRequestMethod.GET,
                                                                BatchRestUtils.buildHeaderMap(),
                                                                null);

            //An internal server error could occur if no step executions have started yet. Wait 1 second and try again.
            if (con.getResponseCode() == HttpURLConnection.HTTP_OK) {
                assertEquals(MEDIA_TYPE_APPLICATION_JSON, con.getHeaderField("Content-Type"));

                // Read the response (all step executions in the given job execution id)
                JsonArray jsonResponse = Json.createReader(con.getInputStream()).readArray();
                log("waitForStepInJobExecutionToReachStatus", "OK Response: " + jsonResponse);

                for (int j = 0; j < jsonResponse.size(); j++) {
                    stepExecution = jsonResponse.getJsonObject(j);
                    if (stepExecution.getJsonString("stepName") != null &&
                        stepExecution.getString("stepName").equals(stepName) &&
                        stepExecution.getString("batchStatus").equals(batchStatus.toString())) {
                        return stepExecution;
                    }
                }
                log("waitForStepInJobExecutionToReachStatus", "Didn't see expected status: " + batchStatus);
            }
            Thread.sleep(1 * 1000);
        }
        throw new IllegalStateException("Timed out waiting for step to reach status: " + batchStatus + ".  Last state: " + stepExecution.toString());
    }

    public JsonObject waitForStepInJobExecutionToFinish(long jobExecutionId, String stepName, int secondsToWait) throws IOException, InterruptedException {
        JsonObject stepExecution = Json.createObjectBuilder().build();
        int[] possibleResponse = new int[1];
        possibleResponse[0] = HttpURLConnection.HTTP_INTERNAL_ERROR;
        for (int i = 0; i < secondsToWait; i++) {

            log("waitForStepInJobExecutionToFinish", "Begin loop");

            // Get job executions for this step
            HttpURLConnection con = HttpUtils.getHttpConnection(buildURL("/ibm/api/batch/jobexecutions/" + jobExecutionId + "/stepexecutions"),
                                                                HttpURLConnection.HTTP_OK,
                                                                possibleResponse,
                                                                10,
                                                                HTTPRequestMethod.GET,
                                                                BatchRestUtils.buildHeaderMap(),
                                                                null);

            //An internal server error could occur if no step executions have started yet. Wait 1 second and try again.
            if (con.getResponseCode() == HttpURLConnection.HTTP_OK) {
                assertEquals(MEDIA_TYPE_APPLICATION_JSON, con.getHeaderField("Content-Type"));

                // Read the response (all step executions in the given job execution id)
                JsonArray jsonResponse = Json.createReader(con.getInputStream()).readArray();
                log("waitForStepInJobExecutionToFinish", "OK Response: " + jsonResponse);

                for (int j = 0; j < jsonResponse.size(); j++) {
                    stepExecution = jsonResponse.getJsonObject(j);
                    if (stepExecution.getJsonString("stepName") != null &&
                        stepExecution.getString("stepName").equals(stepName) &&
                        isDone(stepExecution)) {
                        return stepExecution;
                    }
                }
                log("waitForStepInJobExecutionToFinish", "Didn't see terminating status");
            }
            Thread.sleep(1 * 1000);
        }
        throw new IllegalStateException("Timed out waiting for step to reach final status. ast state: " + stepExecution.toString());
    }

    public String executeSqlUpdate(LibertyServer server, String dataSourceJndi, String sql) throws Exception {

        String userName = "user";
        String password = "pass";

        ServerConfiguration configuration = server.getServerConfiguration();
        ConfigElementList<DataSource> dataSourcesList = configuration.getDataSources();
        Iterator<DataSource> dataSourcesListIterator = dataSourcesList.iterator();

        while (dataSourcesListIterator.hasNext()) {
            DataSource dataSource = dataSourcesListIterator.next();

            if (dataSource.getJndiName().equals(dataSourceJndi)) {
                Set<DataSourceProperties> dataSourcePropertiesList = dataSource.getDataSourceProperties();
                Iterator<DataSourceProperties> dataSourcePropertiesListIterator = dataSourcePropertiesList.iterator();

                while (dataSourcePropertiesListIterator.hasNext()) {
                    DataSourceProperties dataSourceProperties = dataSourcePropertiesListIterator.next();
                    userName = dataSourceProperties.getUser();
                    password = dataSourceProperties.getPassword();
                    break;
                }
            }

            if (!userName.equals("user"))
                break;
        }

        HttpURLConnection conn = new DbServletClient().setDataSourceJndi(dataSourceJndi).setDataSourceUser(userName,
                                                                                                           password).setHostAndPort(server.getHostname(),
                                                                                                                                    server.getHttpDefaultPort()).setSql(sql).executeUpdate();

        BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        String retVal = br.readLine();
        br.close();

        return retVal;
    }

    /**
     * Call the batch api /ibm/api/batch/{path}
     *
     * @param server
     * @param path
     * @return JsonStructure
     * @throws Exception
     */
    public JsonStructure getBatchApi(String path) throws Exception {

        JsonStructure struct = null;

        HttpURLConnection con = HttpUtils.getHttpConnection(buildHttpsURL(server.getHostname(), server.getHttpDefaultSecurePort(), "/ibm/api/batch" + path),
                                                            HttpURLConnection.HTTP_OK,
                                                            new int[0],
                                                            10,
                                                            HTTPRequestMethod.GET,
                                                            BatchRestUtils.buildHeaderMap(),
                                                            null);

        struct = Json.createReader(con.getInputStream()).read();

        log("getBatchApi", "Response: jsonResponse= " + struct.toString());

        return struct;
    }

    /**
     * Submit a local job log purge request
     *
     * @param jobInstanceId
     * @throws Exception
     */
    public void purgeJobInstances(long startInstanceId, long endInstanceId) throws Exception {
        String method = "purgeJobInstances";

        HttpURLConnection con = HttpUtils.getHttpConnection(buildURL("/ibm/api/batch/v2/jobinstances?jobInstanceId="
                                                                     + startInstanceId + ":" + endInstanceId),
                                                            HttpURLConnection.HTTP_OK,
                                                            new int[0],
                                                            10,
                                                            HTTPRequestMethod.DELETE,
                                                            BatchRestUtils.buildHeaderMap(),
                                                            null);

        logReaderContents(method, "Successful purge response: ", HttpUtils.getConnectionStream(con));
    }

    /**
     * Checks the File System for the given jobInstance and jobName after a purge
     *
     * @param jobInstanceId
     * @param jobName
     * @param method
     */
    public void checkFileSystemEntriesRemoved(long jobInstanceId, String jobName, String method) {
        File joblogsDir = new File(server.getServerRoot() + File.separator + "logs" + File.separator + "joblogs");
        File instanceDir = new File(joblogsDir, jobName + File.separator
                                                + new SimpleDateFormat("yyyy-MM-dd").format(new Date()) + File.separator
                                                + "instance." + jobInstanceId);

        assertTrue("Job log directory remained after purge: " + instanceDir.getAbsolutePath(), !instanceDir.exists());
    }

    /**
     * Checks the File System for the given jobInstance and jobName after a purge
     *
     * @param jobInstanceId
     * @param jobName
     * @param method
     */
    public void checkFileSystemEntriesExist(long jobInstanceId, String jobName, String method) {
        File joblogsDir = new File(server.getServerRoot() + File.separator + "logs" + File.separator + "joblogs");
        File instanceDir = new File(joblogsDir, jobName + File.separator
                                                + new SimpleDateFormat("yyyy-MM-dd").format(new Date()) + File.separator
                                                + "instance." + jobInstanceId);

        assertTrue("Job log directory was not found: " + instanceDir.getAbsolutePath(), instanceDir.exists());
    }

    /**
     * Checks the DB for the given jobInstance and jobName after a purge
     *
     * @param jobInstanceId
     * @param jobName
     * @param method
     * @throws Exception
     */
    public void checkDBEntriesRemoved(long jobInstanceId, String jobName, String method) throws Exception {
        // This breaks when using custom port properties so we're hardcoding the schema for now
        // String schema = server.getServerConfiguration().getDatabaseStores().get(0).getSchema();
        String schema = "JBATCH";

        String queryInstance = "SELECT JOBINSTANCEID,jobname FROM " + schema + ".JOBINSTANCE WHERE JOBINSTANCEID = " + jobInstanceId;
        String queryExecution = "SELECT FK_JOBINSTANCEID,batchstatus FROM " + schema + ".JOBEXECUTION WHERE FK_JOBINSTANCEID = " + jobInstanceId;
        String queryStepExecution = "SELECT B.BATCHSTATUS FROM " + schema + ".JOBEXECUTION A INNER JOIN " + schema + ".STEPTHREADEXECUTION  B "
                                    + "ON A.JOBEXECID = B.FK_JOBEXECID WHERE A.FK_JOBINSTANCEID = " + jobInstanceId;

        //String response = executeSql(server, "jdbc/batch", queryInstance);
        String response = new DbServletClient().setDataSourceJndi("jdbc/BonusPayoutDS").setDataSourceUser(DB_USER,
                                                                                                          DB_PASS).setHostAndPort(server.getHostname(),
                                                                                                                                  server.getHttpDefaultPort()).setSql(queryInstance).executeQuery();
        assertTrue("Job instance data remained in the database after purge, response was: " + response, response.isEmpty());

        //response = executeSql(server, "jdbc/batch", queryExecution);
        response = new DbServletClient().setDataSourceJndi("jdbc/BonusPayoutDS").setDataSourceUser(DB_USER,
                                                                                                   DB_PASS).setHostAndPort(server.getHostname(),
                                                                                                                           server.getHttpDefaultPort()).setSql(queryExecution).executeQuery();
        assertTrue("Job execution data remained in the database after purge, response was: " + response, response.isEmpty());

        //response = executeSql(server, "jdbc/batch", queryStepExecution);
        response = new DbServletClient().setDataSourceJndi("jdbc/BonusPayoutDS").setDataSourceUser(DB_USER,
                                                                                                   DB_PASS).setHostAndPort(server.getHostname(),
                                                                                                                           server.getHttpDefaultPort()).setSql(queryStepExecution).executeQuery();
        assertTrue("Step execution data remained in the database after purge, response was: " + response, response.isEmpty());
    }

    /**
     * Checks the DB for the given jobInstance and jobName after a purge
     *
     * @param jobInstanceId
     * @param jobName
     * @param method
     * @throws Exception
     */
    public void checkDBEntriesExist(long jobInstanceId, String jobName, String method) throws Exception {

        // This breaks when using custom port properties so we're hardcoding the schema for now
        // String schema = server.getServerConfiguration().getDatabaseStores().get(0).getSchema();
        String schema = "JBATCH";

        String queryInstance = "SELECT JOBINSTANCEID,jobname FROM " + schema + ".JOBINSTANCE WHERE JOBINSTANCEID = " + jobInstanceId;
        String queryExecution = "SELECT FK_JOBINSTANCEID,batchstatus FROM " + schema + ".JOBEXECUTION WHERE FK_JOBINSTANCEID = " + jobInstanceId;
        String queryStepExecution = "SELECT B.BATCHSTATUS FROM " + schema + ".JOBEXECUTION A INNER JOIN " + schema + ".STEPTHREADEXECUTION  B "
                                    + "ON A.JOBEXECID = B.FK_JOBEXECID WHERE A.FK_JOBINSTANCEID = " + jobInstanceId;

        //String response = executeSql(server, "jdbc/batch", queryInstance);
        String response = new DbServletClient().setDataSourceJndi("jdbc/BonusPayoutDS").setDataSourceUser(DB_USER,
                                                                                                          DB_PASS).setHostAndPort(server.getHostname(),
                                                                                                                                  server.getHttpDefaultPort()).setSql(queryInstance).executeQuery();
        assertTrue("Job instance data not found in database, response was: " + response, !response.isEmpty());

        //response = executeSql(server, "jdbc/batch", queryExecution);
        response = new DbServletClient().setDataSourceJndi("jdbc/BonusPayoutDS").setDataSourceUser(DB_USER,
                                                                                                   DB_PASS).setHostAndPort(server.getHostname(),
                                                                                                                           server.getHttpDefaultPort()).setSql(queryExecution).executeQuery();
        assertTrue("Job execution data not found in database, response was: " + response, !response.isEmpty());

        //response = executeSql(server, "jdbc/batch", queryStepExecution);
        response = new DbServletClient().setDataSourceJndi("jdbc/BonusPayoutDS").setDataSourceUser(DB_USER,
                                                                                                   DB_PASS).setHostAndPort(server.getHostname(),
                                                                                                                           server.getHttpDefaultPort()).setSql(queryStepExecution).executeQuery();
        assertTrue("Step execution data not found in database, response was: " + response, !response.isEmpty());
    }

    public static void exchangeCertificates(LibertyServer server1, LibertyServer server2) throws Exception {

        String server1keystore = server1.getServerRoot() + "/resources/security/key.p12";
        String server2keystore = server2.getServerRoot() + "/resources/security/key.p12";
        String server1certfile = server1.getServerRoot() + "/resources/security/liberty.crt";
        String server2certfile = server2.getServerRoot() + "/resources/security/liberty.crt";

        // Generate a certificate file from server 1
        log("exchangeCertificates", "running keytool command - generate first server certificate file");
        log("exchangeCertificates", System.getProperty("java.home") + "/bin/keytool" + " -export" +
                                    " -alias " + "default " +
                                    " -keystore " + server1keystore +
                                    " -file " + server1certfile +
                                    " -storepass " + "Liberty" +
                                    " -storetype " + "PKCS12" +
                                    " -noprompt");

        ProgramOutput po = server1.getMachine().execute(System.getProperty("java.home") + "/bin/keytool",
                                                        new String[] { "-export",
                                                                       "-alias", "default",
                                                                       "-keystore", server1keystore,
                                                                       "-file", server1certfile,
                                                                       "-storepass", "Liberty",
                                                                       "-storetype", "PKCS12",
                                                                       "-noprompt" },
                                                        server1.getServerRoot(),
                                                        null);

        log("exchangeCertificates", "RC: " + po.getReturnCode());
        log("exchangeCertificates", "stdout:\n" + po.getStdout());
        log("exchangeCertificates", "stderr:\n" + po.getStderr());

        // Generate a certificate file from server 2
        log("exchangeCertificates", "running keytool command - generate second server certificate file");
        log("exchangeCertificates", System.getProperty("java.home") + "/bin/keytool" + " -export" +
                                    " -alias " + "default" +
                                    " -keystore " + server2keystore +
                                    " -file " + server2certfile +
                                    " -storepass " + "Liberty" +
                                    " -storetype " + "PKCS12" +
                                    " -noprompt");

        po = server2.getMachine().execute(System.getProperty("java.home") + "/bin/keytool",
                                          new String[] { "-export",
                                                         "-alias", "default",
                                                         "-keystore", server2keystore,
                                                         "-file", server2certfile,
                                                         "-storepass", "Liberty",
                                                         "-storetype", "PKCS12",
                                                         "-noprompt" },
                                          server2.getServerRoot(),
                                          null);

        log("exchangeCertificates", "RC: " + po.getReturnCode());
        log("exchangeCertificates", "stdout:\n" + po.getStdout());
        log("exchangeCertificates", "stderr:\n" + po.getStderr());

        // Import the certificate from server 2 to server 1
        log("exchangeCertificates", "running keytool command - copy server 1 certificate to server 2 keystore");
        log("exchangeCertificates", System.getProperty("java.home") + "/bin/keytool " + " -import" +
                                    " -keystore " + server1keystore +
                                    " -file " + server2certfile +
                                    " -alias " + server2.getServerName() +
                                    " -storepass " + "Liberty" +
                                    " -storetype " + "PKCS12" +
                                    " -noprompt");

        po = server1.getMachine().execute(System.getProperty("java.home") + "/bin/keytool",
                                          new String[] { "-import",
                                                         "-keystore", server1keystore,
                                                         "-file", server2certfile,
                                                         "-alias", server2.getServerName(),
                                                         "-storepass", "Liberty",
                                                         "-storetype", "PKCS12",
                                                         "-noprompt" },
                                          server1.getServerRoot(),
                                          null);

        log("addClientCertificateToServerKeystore", "RC: " + po.getReturnCode());
        log("addClientCertificateToServerKeystore", "stdout:\n" + po.getStdout());
        log("addClientCertificateToServerKeystore", "stderr:\n" + po.getStderr());

        // Import the certificate from server 1 to server 2
        log("exchangeCertificates", "running keytool command - copy server 2 certificate to server 1 keystore");
        log("exchangeCertificates", System.getProperty("java.home") + "/bin/keytool" + " -import" +
                                    " -keystore " + server2keystore +
                                    " -file " + server1certfile +
                                    " -alias " + server1.getServerName() +
                                    " -storepass " + "Liberty" +
                                    " -storetype " + "PKCS12" +
                                    " -noprompt");

        po = server2.getMachine().execute(System.getProperty("java.home") + "/bin/keytool",
                                          new String[] { "-import",
                                                         "-keystore", server2keystore,
                                                         "-file", server1certfile,
                                                         "-alias", server1.getServerName(),
                                                         "-storepass", "Liberty",
                                                         "-storetype", "PKCS12",
                                                         "-noprompt" },
                                          server2.getServerRoot(),
                                          null);

        log("addClientCertificateToServerKeystore", "RC: " + po.getReturnCode());
        log("addClientCertificateToServerKeystore", "stdout:\n" + po.getStdout());
        log("addClientCertificateToServerKeystore", "stderr:\n" + po.getStderr());
    }

    public String getDatabaseTablePrefix() throws Exception {

        String tablePrefix = server.getServerConfiguration().getDatabaseStores().get(0).getTablePrefix();

        return (tablePrefix != null) ? tablePrefix : "";
    }

    public String getDatabaseSchema() throws Exception {

        String schema = server.getServerConfiguration().getDatabaseStores().get(0).getSchema();

        return (schema != null) ? schema : null;
    }
}
