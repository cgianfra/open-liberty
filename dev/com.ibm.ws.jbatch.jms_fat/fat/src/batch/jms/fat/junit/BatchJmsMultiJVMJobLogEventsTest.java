/*
 * IBM Confidential
 *
 * OCO Source Materials
 *
 * WLP Copyright IBM Corp. 2015
 *
 * The source code for this program is not published or otherwise divested
 * of its trade secrets, irrespective of what has been deposited with the
 * U.S. Copyright Office.
 */
package batch.jms.fat.junit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Properties;

import javax.batch.runtime.BatchStatus;
import javax.json.JsonArray;
import javax.json.JsonObject;

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
 * Test job log events on a multiple server environment
 */
@RunWith(FATRunner.class)
@Mode(TestMode.FULL)
public class BatchJmsMultiJVMJobLogEventsTest {

    private static final LibertyServer messageEngineServer = LibertyServerFactory.getLibertyServer("BatchJmsMessageEngine");

    //batch jms endpoint server
    //This should run the partitions with partition number 0 and 1
    private static final LibertyServer endpointServer = LibertyServerFactory.getLibertyServer("BatchJmsEndpoint");

    //batch jms endpoint server
    //This should run the partitions with partition number 2 and 3
    private static final LibertyServer endpointServer2 = LibertyServerFactory.getLibertyServer("BatchJmsEndpoint2");

    //batch jms dispatcher server
    //This should run as dispatcher as well as endpoint for all top-level jobs
    private static final LibertyServer dispatcherServer = LibertyServerFactory.getLibertyServer("BatchJmsJobLogEventsDispatcher");

    // As defined in the server.xml
    protected final static String ADMIN_NAME = "bob";
    protected final static String ADMIN_PASSWORD = "bobpwd";

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

        //Clear the JobLogEventsMDB cache for the next test
        clearCache(dispatcherServer);
    }

    /**
     * Startup the servers
     */
    @BeforeClass
    public static void beforeClass() throws Exception {

        HttpUtils.trustAllCertificates();
        String serverStartedMsg = "CWWKF0011I:.*";

        //set port in LibertyServer object because it doesn't return the correct port value if substitution is used in server.xml
        endpointServer.setServerConfigurationFile("MultiJvmPartitionsEndpointEvents/server.xml");
        endpointServer2.setServerConfigurationFile("MultiJvmPartitionsEndpoint2Events/server.xml");

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

    /**
     * Shutdown the server.
     */
    @AfterClass
    public static void afterClass() throws Exception {

        if (endpointServer != null && endpointServer.isStarted()) {
            endpointServer.stopServer(LibertyServer.DISABLE_FAILURE_CHECKING);
        }

        if (endpointServer2 != null && endpointServer2.isStarted()) {
            endpointServer2.stopServer(LibertyServer.DISABLE_FAILURE_CHECKING);
        }

        if (dispatcherServer != null && dispatcherServer.isStarted()) {
            dispatcherServer.stopServer(LibertyServer.DISABLE_FAILURE_CHECKING);
        }

        if (messageEngineServer != null && messageEngineServer.isStarted()) {
            messageEngineServer.stopServer(LibertyServer.DISABLE_FAILURE_CHECKING);
        }

    }

    /**
     * Tests job log events with a restart while partitions are running on multiple servers
     *
     * Expected FFDCs because when the dispatcher(which runs the TLJ)
     * is shutdown,
     * it stops listening to the reply from the partition executor
     */
    @Test
    @AllowedFFDC({ "com.ibm.ws.sib.jfapchannel.JFapConnectionBrokenException",
                   "javax.jms.InvalidDestinationException",
                   "javax.batch.operations.BatchRuntimeException",
                   "com.ibm.wsspi.sib.core.exception.SITemporaryDestinationNotFoundException" })
    public void testJobLogEventsWithMultiJvmPartitionsRestart() throws Exception {

        BatchRestUtils dispatcherUtils = new BatchRestUtils(dispatcherServer);

        //Submit job
        JsonObject jobInstance = dispatcherUtils.submitJob("SimpleBatchJob", "test_sleepy_partition");

        //Initialize Instance artifacts(jobInstance, jobInstanceId);
        long jobInstanceId = jobInstance.getJsonNumber("instanceId").longValue();

        //Initialize Execution artifacts(jobExecution, jobExecutionId);
        JsonObject jobExecution = dispatcherUtils.waitForFirstJobExecution(jobInstanceId);
        long jobExecutionId = jobExecution.getJsonNumber("executionId").longValue();

        jobExecution = dispatcherUtils.waitForJobExecutionToStart(jobExecutionId);
        JsonObject stepExecution1 = dispatcherUtils.waitForStepInJobExecutionToStart(jobExecutionId, "step1");

        assertEquals("STARTED", stepExecution1.getString("batchStatus"));
        JsonArray partitions;

        //Check for 3 partitions to start running. Once they start, execute the stop
        for (int i = 0; i < 20; i++) {
            stepExecution1 = dispatcherUtils.getStepExecutionFromExecutionIdAndStepName(jobExecutionId, "step1").getJsonObject(0);
            partitions = stepExecution1.getJsonArray("partitions");
            if (partitions.size() == 3) {
                break;
            }
        }
        assertEquals("STARTED", stepExecution1.getString("batchStatus"));

        //Stop the job
        jobExecution = dispatcherUtils.stopJobExecutionAndWaitUntilDone(jobExecutionId);

        //Check batchStatus is STOPPED
        //Assuming job to be stopped in the given time.
        JsonArray stepExecutions = jobExecution.getJsonArray("stepExecutions");
        assertEquals(1, stepExecutions.size());
        JsonObject stepExecution = stepExecutions.getJsonObject(0);

        //Occasionally the job can get stuck in FAILED and do not want to fail the test because of that
        assumeTrue("STOPPED".equals(stepExecution.getString("batchStatus")));

        //Ensure that the TLJ and the 3 partitions have sent at least one job log event each.
        //This means that all partitions have "COMPLETED" since there is not a partitioned "STOPPED" status
        int topLevelJobCount = getLogPartsCount(dispatcherServer, "/namedCount/TopLevelJob");
        int partitionZeroCount = getLogPartsCount(dispatcherServer, "/namedCount/0");
        int partitionOneCount = getLogPartsCount(dispatcherServer, "/namedCount/1");
        int partitionTwoCount = getLogPartsCount(dispatcherServer, "/namedCount/2");

        for (int i = 0; i < 20; i++) {
            if (topLevelJobCount >= 1 && partitionZeroCount >= 1 && partitionOneCount >= 1 && partitionTwoCount >= 1) {
                break;
            } else {
                Thread.sleep(2 * 1000);

                topLevelJobCount = getLogPartsCount(dispatcherServer, "/namedCount/TopLevelJob");
                partitionZeroCount = getLogPartsCount(dispatcherServer, "/namedCount/0");
                partitionOneCount = getLogPartsCount(dispatcherServer, "/namedCount/1");
                partitionTwoCount = getLogPartsCount(dispatcherServer, "/namedCount/2");
            }
        }

        Thread.sleep(2 * 1000);

        //checking step1
        stepExecutions = dispatcherUtils.getStepExecutionsFromExecutionId(jobExecutionId);
        assertEquals(2, stepExecutions.size());//step1 + _links
        stepExecution1 = dispatcherUtils.getStepExecutionFromExecutionIdAndStepName(jobExecutionId, "step1").getJsonObject(0);

        partitions = stepExecution1.getJsonArray("partitions");
        BatchFatUtils.checkExpectedBatchStatus(partitions, 3);

        //PartitionNum 0 : EndpointServer
        //PartitionNum 1 : EndpointServer
        //PartitionNum 2 : EndpointServer2
        checkPartitionsExitStatus(stepExecution1.getJsonArray("partitions"), new String[] { "BatchJmsEndpoint", "BatchJmsEndpoint", "BatchJmsEndpoint2" });

        //Restart the job
        jobInstance = dispatcherUtils.restartJobExecution(jobInstance, jobExecutionId, new Properties());

        Thread.sleep(2 * 1000);

        JsonArray jobExecutions = dispatcherUtils.getJobExecutionsMostRecentFirst(jobInstanceId);

        assertEquals(2, jobExecutions.size());
        jobExecution = jobExecutions.getJsonObject(0);
        assertEquals("STARTED", jobExecution.getString("batchStatus"));
        jobExecutionId = jobExecution.getJsonNumber("executionId").longValue();

        jobInstance = dispatcherUtils.waitForJobInstanceToFinish(jobInstanceId);

        //Get step Executions and check their size and status
        stepExecutions = dispatcherUtils.getStepExecutionsFromExecutionId(jobExecutionId);

        //there will be no step1 partitions run because this is a RESTART_NORMAL as they were run earlier.
        //Only step2 partitions will run
        BatchFatUtils.checkPartitionsSizeAndStatusCompleted(stepExecutions, "step1", 0);
        BatchFatUtils.checkPartitionsSizeAndStatusCompleted(stepExecutions, "step2", 2);

        //Should be at least 7 total: 2 for TLJ, 2 for partitions 0 and 1, and 1 for partition 2 (step 2 only allows 2 partitions)
        topLevelJobCount = getLogPartsCount(dispatcherServer, "/namedCount/TopLevelJob");
        partitionZeroCount = getLogPartsCount(dispatcherServer, "/namedCount/0");
        partitionOneCount = getLogPartsCount(dispatcherServer, "/namedCount/1");
        partitionTwoCount = getLogPartsCount(dispatcherServer, "/namedCount/2");
        assertTrue(topLevelJobCount >= 2);
        assertTrue(partitionZeroCount >= 2);
        assertTrue(partitionOneCount >= 2);
        assertTrue(partitionTwoCount >= 1);
    }

    /**
     * Test that after purging partition job logs the job log events are still available
     */
    @Test
    public void testPartitionJobLogEventsAfterJobPurge() throws Exception {

        BatchRestUtils dispatcherUtils = new BatchRestUtils(dispatcherServer);

        JsonObject jobInstance = dispatcherUtils.submitJob("SimpleBatchJob", "test_simplePartition");
        long jobInstanceId = jobInstance.getJsonNumber("instanceId").longValue();
        jobInstance = dispatcherUtils.waitForJobInstanceToFinish(jobInstanceId);
        assertEquals("COMPLETED", jobInstance.getString("batchStatus"));

        //Wait 2 second to ensure the job logger MDB application received the final JMS messages
        Thread.sleep(2 * 1000);

        //Purge the joblogs via the dispatcher.  This should work (response 200).
        HttpUtils.getHttpConnection(dispatcherUtils.buildURL("/ibm/api/batch/jobinstances/" + jobInstanceId),
                                    HttpURLConnection.HTTP_OK,
                                    new int[0],
                                    10,
                                    HTTPRequestMethod.DELETE,
                                    BatchRestUtils.buildHeaderMap(),
                                    null);

        //Should be at least 4 total: 1 for TLJ, 1 for partitions 0, 1 and 2
        int topLevelJobCount = getLogPartsCount(dispatcherServer, "/namedCount/TopLevelJob");
        int partitionZeroCount = getLogPartsCount(dispatcherServer, "/namedCount/0");
        int partitionOneCount = getLogPartsCount(dispatcherServer, "/namedCount/1");
        int partitionTwoCount = getLogPartsCount(dispatcherServer, "/namedCount/2");
        assertTrue(topLevelJobCount >= 1);
        assertTrue(partitionZeroCount >= 1);
        assertTrue(partitionOneCount >= 1);
        assertTrue(partitionTwoCount >= 1);
    }

    /**
     * Test split flow job log event creation
     */
    @Test
    public void testSplitFlowJobLogEventsAfterJobPurge() throws Exception {

        BatchRestUtils dispatcherUtils = new BatchRestUtils(dispatcherServer);

        JsonObject jobInstance = dispatcherUtils.submitJob("SimpleBatchJob", "test_simpleSplitFlow");
        long jobInstanceId = jobInstance.getJsonNumber("instanceId").longValue();
        jobInstance = dispatcherUtils.waitForJobInstanceToFinish(jobInstanceId);
        assertEquals("COMPLETED", jobInstance.getString("batchStatus"));

        //Wait 2 second to ensure the job logger MDB application received the final JMS messages
        Thread.sleep(2 * 1000);

        //Should be at least 3 total: 1 for TLJ and 1 for each flow (flow1 and flow2)
        int topLevelJobCount = getLogPartsCount(dispatcherServer, "/namedCount/TopLevelJob");
        int flowOneCount = getLogPartsCount(dispatcherServer, "/namedCount/flow1");
        int flowTwoCount = getLogPartsCount(dispatcherServer, "/namedCount/flow2");
        assertTrue(topLevelJobCount >= 1);
        assertTrue(flowOneCount >= 1);
        assertTrue(flowTwoCount >= 1);
    }

    /**
     * Test that large job log parts will still send job log event messages
     */
    @Test
    public void testLargeJobLogEventPart() throws Exception {

        BatchRestUtils dispatcherUtils = new BatchRestUtils(dispatcherServer);

        Properties props = new Properties();
        props.put("log.lines.to.print", "10000");

        JsonObject jobInstance = dispatcherUtils.submitJob("SimpleBatchJob", "test_log_control_batchlet", props);
        long jobInstanceId = jobInstance.getJsonNumber("instanceId").longValue();
        jobInstance = dispatcherUtils.waitForJobInstanceToFinish(jobInstanceId);

        assertEquals("COMPLETED", jobInstance.getString("batchStatus"));

        //Wait 2 second to ensure the job logger MDB application received the final JMS messages
        Thread.sleep(2 * 1000);

        //Should be at least 4 total: 1 for TLJ, 1 for partitions 0, 1 and 2
        int topLevelJobCount = getLogPartsCount(dispatcherServer, "/namedCount/TopLevelJob");
        int partitionZeroCount = getLogPartsCount(dispatcherServer, "/namedCount/0");
        int partitionOneCount = getLogPartsCount(dispatcherServer, "/namedCount/1");
        int partitionTwoCount = getLogPartsCount(dispatcherServer, "/namedCount/2");
        assertTrue(topLevelJobCount > 0);
        assertTrue(partitionZeroCount > 0);
        assertTrue(partitionOneCount > 0);
        assertTrue(partitionTwoCount > 0);
    }

    /**
     * Test that after a job fails job log events are sent out
     *
     * Expected FFDC's since the job is force failing
     */
    @Test
    @AllowedFFDC({ "com.ibm.jbatch.container.exception.BatchContainerRuntimeException", "java.lang.RuntimeException" })
    public void testFailedJobLogEvent() throws Exception {

        BatchRestUtils dispatcherUtils = new BatchRestUtils(dispatcherServer);

        Properties props = new Properties();
        props.put("force.failure", "true");

        JsonObject jobInstance = dispatcherUtils.submitJob("SimpleBatchJob", "test_log_control_batchlet", props);
        long jobInstanceId = jobInstance.getJsonNumber("instanceId").longValue();
        jobInstance = dispatcherUtils.waitForJobInstanceToFinish(jobInstanceId);

        assertEquals("FAILED", jobInstance.getString("batchStatus"));

        //Wait 2 second to ensure the job logger MDB application received the final JMS messages
        Thread.sleep(2 * 1000);

        //Should be at least 4 total: 1 for TLJ, 1 for partitions 0, 1 and 2
        int topLevelJobCount = getLogPartsCount(dispatcherServer, "/namedCount/TopLevelJob");
        int partitionZeroCount = getLogPartsCount(dispatcherServer, "/namedCount/0");
        int partitionOneCount = getLogPartsCount(dispatcherServer, "/namedCount/1");
        int partitionTwoCount = getLogPartsCount(dispatcherServer, "/namedCount/2");
        assertTrue(topLevelJobCount >= 1);
        assertTrue(partitionZeroCount >= 1);
        assertTrue(partitionOneCount >= 1);
        assertTrue(partitionTwoCount >= 1);
    }

    /**
     * Tests that a job with partitioned chunks produces the proper job log events
     */
    @Test
    public void testJobLogEventsWithJmsPartitionedChunks() throws Exception {

        BatchRestUtils dispatcherUtils = new BatchRestUtils(dispatcherServer);

        Properties props = new Properties();
        props.put("numRecords", "1000");
        props.put("numValidationPartitions", "4");
        props.put("chunkSize", "100");

        //submit the job
        JsonObject jobInstance = dispatcherUtils.submitJob("BonusPayout", "BonusPayoutJob.partitioned", props);

        long jobInstanceId = jobInstance.getJsonNumber("instanceId").longValue();
        JsonObject jobExecution = dispatcherUtils.waitForFirstJobExecution(jobInstanceId);
        long jobExecutionId = jobExecution.getJsonNumber("executionId").longValue();

        jobExecution = dispatcherUtils.waitForJobExecutionToStart(jobExecutionId);

        //wait for job to finish executing
        jobInstance = dispatcherUtils.waitForJobInstanceToFinish(jobInstanceId);

        //Wait 2 second to ensure the job logger MDB application received the final JMS messages
        Thread.sleep(2 * 1000);

        //Since there are more records it is tough to predict part size for each log type.
        //Check to make sure there is at least one for the TLJ and each of the 4 partitions
        int topLevelJobCount = getLogPartsCount(dispatcherServer, "/namedCount/TopLevelJob");
        int partitionZeroCount = getLogPartsCount(dispatcherServer, "/namedCount/0");
        int partitionOneCount = getLogPartsCount(dispatcherServer, "/namedCount/1");
        int partitionTwoCount = getLogPartsCount(dispatcherServer, "/namedCount/2");
        int partitionThreeCount = getLogPartsCount(dispatcherServer, "/namedCount/3");
        assertTrue(topLevelJobCount > 0);
        assertTrue(partitionZeroCount > 0);
        assertTrue(partitionOneCount > 0);
        assertTrue(partitionTwoCount > 0);
        assertTrue(partitionThreeCount > 0);

        assertEquals("COMPLETED", jobInstance.getString("batchStatus"));

        jobExecution = dispatcherUtils.getJobExecutionFromExecutionId(jobExecutionId);
        assertEquals("COMPLETED", jobExecution.getString("batchStatus"));

        JsonArray stepExecutions = dispatcherUtils.getStepExecutionsFromExecutionId(jobExecutionId);
        assertEquals(4, stepExecutions.size());

        JsonObject generateStep = stepExecutions.getJsonObject(0);
        assertEquals("generate", generateStep.getString("stepName"));
        assertEquals("COMPLETED", generateStep.getString("batchStatus"));

        JsonObject addBonusStep = stepExecutions.getJsonObject(1);
        assertEquals("addBonus", addBonusStep.getString("stepName"));
        assertEquals("COMPLETED", addBonusStep.getString("batchStatus"));

        JsonObject validationStep = stepExecutions.getJsonObject(2);
        assertEquals("validation", validationStep.getString("stepName"));
        assertEquals("COMPLETED", validationStep.getString("batchStatus"));

        JsonArray validationPartitions = validationStep.getJsonArray("partitions");

        BatchFatUtils.checkExpectedBatchStatus(validationPartitions, 4, BatchStatus.COMPLETED);
    }

    /**
     * Method to verify exitStatus for each partition
     *
     * @param list of partitions returned in the JSON response
     * @param list of exitStatus for each partitionNumber starting from partitionNumber : 0
     */
    private void checkPartitionsExitStatus(JsonArray partitions, String[] exitStatusList) {
        for (int i = 0; i < exitStatusList.length; i++) {
            assertEquals(exitStatusList[i], partitions.getJsonObject(i).getString("exitStatus"));
        }
    }

    /**
     * Method to communicate with the MDB application to get info about the job log events
     *
     * @param server that the JobLogEventsMDB application is running on
     * @param path
     * @return
     * @throws Exception
     */
    public int getLogPartsCount(LibertyServer server, String path) throws Exception {
        return Integer.parseInt(jobLogEventsMDBClient(server, path));
    }

    /**
     * Clears out the job log events cache for the next FAT test
     *
     * @param server that the JobLogEventsMDB application is running on
     * @throws Exception
     */
    public void clearCache(LibertyServer server) throws Exception {

        HttpURLConnection con = HttpUtils.getHttpConnection(buildHttpsURL(server.getHostname(), server.getHttpDefaultSecurePort(), "/JobLogEventsMDB/clear"),
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
     * Call the JobLogEventsMDB app to get info about the job log parts
     *
     * @param server
     * @param path
     * @return String
     * @throws Exception
     */
    public String jobLogEventsMDBClient(LibertyServer server, String path) throws Exception {

        HttpURLConnection con = HttpUtils.getHttpConnection(buildHttpsURL(server.getHostname(), server.getHttpDefaultSecurePort(), "/JobLogEventsMDB" + path),
                                                            HttpURLConnection.HTTP_OK,
                                                            new int[0],
                                                            10,
                                                            HTTPRequestMethod.GET,
                                                            BatchRestUtils.buildHeaderMap(),
                                                            null);

        String result = null;
        StringBuffer sb = new StringBuffer();
        InputStream is = null;

        try {
            is = new BufferedInputStream(con.getInputStream());
            BufferedReader br = new BufferedReader(new InputStreamReader(is));
            String inputLine = "";
            while ((inputLine = br.readLine()) != null) {
                sb.append(inputLine);
            }
            result = sb.toString();
        } catch (Exception e) {
            log("JobLogEventsMDBClient", "Error reading InputStream");
            result = null;
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                    log("JobLogEventsMDBClient", "Error closing InputStream");
                }
            }
        }

        con.disconnect();

        log("JobLogEventsMDBClient", "Response: job log parts for the " + path + " path is: " + result);

        return result;
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
        Log.info(BatchJmsMultiJVMJobLogEventsTest.class, method, String.valueOf(msg));
    }

}
