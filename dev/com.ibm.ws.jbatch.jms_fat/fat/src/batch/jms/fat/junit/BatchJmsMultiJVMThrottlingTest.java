/*
 * IBM Confidential
 *
 * OCO Source Materials
 *
 * WLP Copyright IBM Corp. 2016
 *
 * The source code for this program is not published or otherwise divested
 * of its trade secrets, irrespective of what has been deposited with the
 * U.S. Copyright Office.
 */
package batch.jms.fat.junit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Properties;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonStructure;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.ibm.websphere.simplicity.log.Log;

import batch.fat.util.BatchFatUtils;
import componenttest.annotation.AllowedFFDC;
import componenttest.custom.junit.runner.FATRunner;
import componenttest.custom.junit.runner.Mode;
import componenttest.custom.junit.runner.Mode.TestMode;
import componenttest.topology.impl.LibertyServer;
import componenttest.topology.impl.LibertyServerFactory;
import componenttest.topology.utils.HttpUtils;
import componenttest.topology.utils.HttpUtils.HTTPRequestMethod;

/**
 *
 */
@RunWith(FATRunner.class)
@Mode(TestMode.FULL)
public class BatchJmsMultiJVMThrottlingTest {

    private static final LibertyServer messageEngineServer = LibertyServerFactory.getLibertyServer("BatchJmsMessageEngine");

    //batch jms end-point server
    //This should run all the jobs
    private static final LibertyServer endpointServer = LibertyServerFactory.getLibertyServer("BatchJmsEndpoint");

    //batch jms end-point server
    //This should run all the partitions
    private static final LibertyServer endpointServer2 = LibertyServerFactory.getLibertyServer("BatchJmsEndpoint2");

    //batch jms dispatcher server
    //This should dispatch all jobs to be run
    private static final LibertyServer dispatcherServer = LibertyServerFactory.getLibertyServer("BatchJmsThrottlingDispatcher");

    // As defined in the server.xml
    protected final static String ADMIN_NAME = "bob";
    protected final static String ADMIN_PASSWORD = "bobpwd";

    /**
     * Startup the servers
     */
    @BeforeClass
    public static void beforeClass() throws Exception {

        HttpUtils.trustAllCertificates();
        String serverStartedMsg = "CWWKF0011I:.*";

        //set port in LibertyServer object because it doesn't return the correct port value if substitution is used in server.xml
        endpointServer.setServerConfigurationFile("ThrottlingEndpoint/server.xml");
        endpointServer2.setServerConfigurationFile("ThrottlingEndpoint2/server.xml");

        setports();

        //clean start server with Message Engine first
        messageEngineServer.startServer();
        String uploadMessage = messageEngineServer.waitForStringInLogUsingMark(serverStartedMsg, messageEngineServer.getMatchingLogFile("trace.log"));
        assertNotNull("Could not find CWWKF0011I (server start info message) in trace.log of " + messageEngineServer.getServerName(), uploadMessage);

        //clean start dispatcher
        dispatcherServer.startServer();
        uploadMessage = dispatcherServer.waitForStringInLogUsingMark(serverStartedMsg, dispatcherServer.getMatchingLogFile("trace.log"));
        assertNotNull("Could not find CWWKF0011I (server start info message) in trace.log of " + dispatcherServer.getServerName(), uploadMessage);
        assertEquals(Integer.getInteger("batch.dispatcher_1_HTTP_default").intValue(), dispatcherServer.getHttpDefaultPort());

        //clean start endpoint
        endpointServer.startServer();
        uploadMessage = endpointServer.waitForStringInLogUsingMark(serverStartedMsg, endpointServer.getMatchingLogFile("trace.log"));
        assertNotNull("Could not find CWWKF0011I (server start info message) in trace.log of " + endpointServer.getServerName(), uploadMessage);
        assertEquals(Integer.getInteger("batch.endpoint_1_HTTP_default").intValue(), endpointServer.getHttpDefaultPort());

        //clean start second endpoint
        endpointServer2.startServer();
        uploadMessage = endpointServer2.waitForStringInLogUsingMark(serverStartedMsg, endpointServer2.getMatchingLogFile("trace.log"));
        assertNotNull("Could not find CWWKF0011I (server start info message) in trace.log of " + endpointServer2.getServerName(), uploadMessage);
        assertEquals(Integer.getInteger("batch.endpoint_2_HTTP_default").intValue(), endpointServer2.getHttpDefaultPort());

    }

    /**
     * Set the port that test servers used because
     * LibertyServer does not return the actual value used if it is not default.
     */
    private static void setports() {
        dispatcherServer.setHttpDefaultPort(Integer.getInteger("batch.dispatcher_1_HTTP_default").intValue());
        dispatcherServer.setHttpDefaultSecurePort(Integer.getInteger("batch.dispatcher_1_HTTP_default.secure").intValue());

        endpointServer.setHttpDefaultPort(Integer.getInteger("batch.endpoint_1_HTTP_default").intValue());
        endpointServer.setHttpDefaultSecurePort(Integer.getInteger("batch.endpoint_1_HTTP_default.secure").intValue());

        endpointServer2.setHttpDefaultPort(Integer.getInteger("batch.endpoint_2_HTTP_default").intValue());
        endpointServer2.setHttpDefaultSecurePort(Integer.getInteger("batch.endpoint_2_HTTP_default.secure").intValue());
    }

    @Before
    public void beforeEachTest() throws Exception {

        if (!dispatcherServer.isStarted()) {
            BatchFatUtils.startServer(dispatcherServer);
        }

        if (!endpointServer.isStarted()) {
            BatchFatUtils.startServer(endpointServer);
        }

        if (!endpointServer2.isStarted()) {
            BatchFatUtils.startServer(endpointServer2);
        }
    }

    /**
     * Shutdown the server.
     */
    @AfterClass
    public static void afterClass() throws Exception {
        if (endpointServer != null && endpointServer.isStarted()) {
            endpointServer.stopServer("CWWKG0011W");
        }

        if (endpointServer2 != null && endpointServer2.isStarted()) {
            endpointServer2.stopServer("CWWKG0011W");
        }

        if (dispatcherServer != null && dispatcherServer.isStarted()) {
            dispatcherServer.stopServer("CWWKG0011W", "CWSIV0782W", "CWSIV0950E", "CWSIT0127E");
        }

        if (messageEngineServer != null && messageEngineServer.isStarted()) {
            messageEngineServer.stopServer();
        }
    }

    @Before
    public void before() throws Exception {
        clearStatus(dispatcherServer);
    }

    /**
     * Stop EndpointServer2 which runs all the partitions
     * Submit a job which will queue partitions on the queue
     * Bring the EndpointServer2 up to execute all partitions
     * Check if number of concurrently running partitions is less than or equal to expected value
     */
    @Test
    @AllowedFFDC({ "javax.resource.spi.UnavailableException" })
    public void testThrottlingPartitions() throws Exception {

        BatchFatUtils.stopServer(endpointServer2);

        BatchRestUtils dispatcherUtils = new BatchRestUtils(dispatcherServer);

        int numPartitions = 30;

        int numConcurrentPartitions = 2;
        Properties props = new Properties();

        props.put("numPartitions", numPartitions + "");
        props.put("sleep.time.seconds", "0");
        props.put("randomize.sleep.time", "true");

        //Submit job
        JsonObject jobInstance = dispatcherUtils.submitJob("SimpleBatchJob", "test_sleepy_partitioned_batchlet", props);

        long instanceId = jobInstance.getJsonNumber("instanceId").longValue();

        jobInstance = dispatcherUtils.waitForJobInstanceToStart(instanceId);

        BatchFatUtils.startServer(endpointServer2);

        //Poll for max of 900 seconds and wait for the job to finish
        for (int i = 0; i < 900; i++) {
            JsonArray runningPartitions = getPartitionsRunning(dispatcherServer);
            int numActivePartitions = runningPartitions.size();

            String assertionMessage = "Counting number of active partitions according to REST API." +
                                      " Found active # = " + numActivePartitions + ", expecting an upper bound of no more than max = " +
                                      numConcurrentPartitions;
            assertTrue(assertionMessage, numActivePartitions <= numConcurrentPartitions);
            jobInstance = dispatcherUtils.getJobInstance(instanceId);

            if (BatchRestUtils.isDone(jobInstance)) {
                break;
            }
            Thread.sleep(1 * 1000);
        }

        assertEquals("COMPLETED", jobInstance.getString("batchStatus"));

    }

    /**
     * Stop EndpointServer which runs all the jobs
     * Submit multiple job which will be waitin on the queue
     * Bring up the EndpointServer to execute all the jobs
     * Check if number of concurrently running jobs is less than or equal to expected value
     */
    @Test
    /*
     * Strictly speaking, we shouldn't need this allowance for the test below. It itself
     * doesn't stop and servers mid-test. But currently, we are seeing somewhat commonly that
     * some messages are left over from earlier tests (like testThrottlingPartitions) are first
     * being noticed during the running of this test, AND, since, generally speaking, we ignore
     * this set of FFDC(s) categorically in a bunch of other JMS tests.... let's just make less
     * noise for now and ignore them here too.
     */
    @AllowedFFDC({ "com.ibm.ws.sib.jfapchannel.JFapConnectionBrokenException",
                   "javax.jms.InvalidDestinationException",
                   "javax.resource.spi.UnavailableException",
                   "javax.batch.operations.BatchRuntimeException",
                   "com.ibm.wsspi.sib.core.exception.SITemporaryDestinationNotFoundException",
                   "com.ibm.websphere.sib.exception.SIResourceException",
                   "com.ibm.wsspi.channelfw.exception.InvalidChainNameException" })
    public void testThrottlingJobs() throws Exception {

        BatchRestUtils dispatcherUtils = new BatchRestUtils(dispatcherServer);

        BatchFatUtils.stopServer(endpointServer);

        int numJobs = 4;
        int numConcurrentJobs = 1;
        Properties props = new Properties();

        props.put("numPartitions", "3");
        props.put("sleep.time.seconds", "2");
        props.put("randomize.sleep.time", "true");

        JsonObject[] jobInstances = new JsonObject[numJobs];

        //Submit jobs - keep this # small or we will hit concurrent JAXB unmarshalling issues
        for (int i = 0; i < numJobs; i++) {
            jobInstances[i] = dispatcherUtils.submitJob("SimpleBatchJob", "test_sleepy_partitioned_batchlet", props);
        }

        BatchFatUtils.startServer(endpointServer);

        waitForSomeJobToStart(dispatcherServer);

        // At this point, the jobs would have started.
        // So, runningJobs.size should return 0 only when all the jobs end.
        for (int i = 0; i < 200; i++) {
            JsonArray runningJobs = getJobsRunning(dispatcherServer);
            int numActiveJobs = runningJobs.size();
            String assertionMessage = "Counting number of active jobs according to REST API." +
                                      " Found active # = " + numActiveJobs + ", expecting an upper bound of no more than max = " +
                                      numConcurrentJobs;
            assertTrue(assertionMessage, numActiveJobs <= numConcurrentJobs);
            if ((numActiveJobs == 0) && dispatcherUtils.areAllDone(jobInstances)) {
                break;
            }
            Thread.sleep(1 * 1000);
        }

        for (int i = 0; i < numJobs; i++) {
            assertEquals("COMPLETED", dispatcherUtils.getJobInstance(jobInstances[i].getJsonNumber("instanceId").longValue()).getString("batchStatus"));
        }

    }

    private JsonArray waitForSomeJobToStart(LibertyServer server) throws Exception {
        JsonArray startedInstances;
        for (int i = 0; i < 60; i++) {
            startedInstances = (JsonArray) throttlingMDBClient(server, "/jobs/started");

            if (startedInstances.size() != 0)
                return startedInstances;
            else {
                Thread.sleep(1 * 1000);
            }
        }
        throw new RuntimeException("Timed out waiting for jobs to start");
    }

    private JsonArray getJobsRunning(LibertyServer server) throws Exception {
        return (JsonArray) throttlingMDBClient(server, "/jobs/running");
    }

    public JsonArray getPartitionsRunning(LibertyServer server) throws Exception {
        return (JsonArray) throttlingMDBClient(server, "/partitions/running");
    }

    public void clearStatus(LibertyServer server) throws Exception {

        HttpURLConnection con = HttpUtils.getHttpConnection(buildHttpsURL(server.getHostname(), server.getHttpDefaultSecurePort(), "/ThrottlingMDB/status"),
                                                            HttpURLConnection.HTTP_OK,
                                                            new int[0],
                                                            10,
                                                            HTTPRequestMethod.DELETE,
                                                            BatchRestUtils.buildHeaderMap(),
                                                            null);
        con.connect();
        con.disconnect();
    }

    /**
     * Call the ThrottlingMDB app and get status of jobs and partitions
     *
     * @param server
     * @param path
     * @return JsonStructure
     * @throws Exception
     */
    public JsonStructure throttlingMDBClient(LibertyServer server, String path) throws Exception {

        JsonStructure struct = null;

        HttpURLConnection con = HttpUtils.getHttpConnection(buildHttpsURL(server.getHostname(), server.getHttpDefaultSecurePort(), "/ThrottlingMDB/status" + path),
                                                            HttpURLConnection.HTTP_OK,
                                                            new int[0],
                                                            10,
                                                            HTTPRequestMethod.GET,
                                                            BatchRestUtils.buildHeaderMap(),
                                                            null);

        struct = Json.createReader(con.getInputStream()).read();

        log("throttlingMDBClient", "Response: jsonResponse= " + struct.toString());

        return struct;
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
     * helper for simple logging.
     */
    private static void log(String method, Object msg) {
        Log.info(BatchRestUtils.class, method, String.valueOf(msg));
    }
}
