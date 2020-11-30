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
import com.ibm.ws.jbatch.test.FatUtils;
import com.ibm.ws.jbatch.test.dbservlet.DbServletClient;

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
 * Test job log events on a single server environment
 */
@RunWith(FATRunner.class)
@Mode(TestMode.FULL)
public class BatchJmsJobLogEventsSingleServerTest {

    private static final LibertyServer server = LibertyServerFactory.getLibertyServer("JobEventsSingleServer");

    BatchRestUtils serverUtils = new BatchRestUtils(server);

    /**
     * Startup the server
     */
    @BeforeClass
    public static void beforeClass() throws Exception {

        HttpUtils.trustAllCertificates();
        server.startServer();
        FatUtils.waitForStartupAndSsl(server);

        // Setup BonusPayout app tables
        new DbServletClient().setDataSourceJndi("jdbc/BonusPayoutDS").setDataSourceUser("user", "pass").setHostAndPort(server.getHostname(),
                                                                                                                       server.getHttpDefaultPort()).loadSql(server.pathToAutoFVTTestFiles
                                                                                                                                                            + "common/BonusPayout.derby.ddl",
                                                                                                                                                            "JBATCH",
                                                                                                                                                            "").executeUpdate();
    }

    /**
     * Clean up to do before each no test is ran
     *
     * @throws Exception
     */
    @Before
    public void beforeEachTest() throws Exception {
        //Clear the JobLogEventsMDB cache for the next test
        clearCache(server);
    }

    /**
     * Shutdown the server.
     */
    @AfterClass
    public static void afterClass() throws Exception {
        if (server != null && server.isStarted()) {
            server.stopServer(LibertyServer.DISABLE_FAILURE_CHECKING);
        }
    }

    /**
     * Tests job log events with a restart while partitions are running.
     *
     * An occasional FFDC when the 1st step is in the process of starting after the execution has started
     * and you try to do a get request on step execution data
     */
    @Test
    @Mode(TestMode.QUARANTINE)
    // Defect 225692
    public void testJobLogEventsWithPartitionsRestart() throws Exception {

        BatchRestUtils serverUtils = new BatchRestUtils(server);

        //Submit job
        JsonObject jobInstance = serverUtils.submitJob("SimpleBatchJob", "test_sleepy_partition");

        //Initialize Instance artifacts(jobInstance, jobInstanceId);
        long jobInstanceId = jobInstance.getJsonNumber("instanceId").longValue();

        //Initialize Execution artifacts(jobExecution, jobExecutionId);
        JsonObject jobExecution = serverUtils.waitForFirstJobExecution(jobInstanceId);
        long jobExecutionId = jobExecution.getJsonNumber("executionId").longValue();

        jobExecution = serverUtils.waitForJobExecutionToStart(jobExecutionId);

        JsonObject stepExecution1 = serverUtils.waitForStepInJobExecutionToStart(jobExecutionId, "step1");

        assertEquals("STARTED", stepExecution1.getString("batchStatus"));
        JsonArray partitions;

        //Check for 3 partitions to start running. Once they start, execute the stop
        for (int i = 0; i < 20; i++) {
            stepExecution1 = serverUtils.getStepExecutionFromExecutionIdAndStepName(jobExecutionId, "step1").getJsonObject(0);
            partitions = stepExecution1.getJsonArray("partitions");
            if (partitions.size() == 3) {
                break;
            }
        }
        assertEquals("STARTED", stepExecution1.getString("batchStatus"));

        //Stop the job
        jobExecution = serverUtils.stopJobExecutionAndWaitUntilDone(jobExecutionId);

        //Check batchStatus is STOPPED
        //Assuming job to be stopped in the given time.
        JsonArray stepExecutions = jobExecution.getJsonArray("stepExecutions");
        assertEquals(1, stepExecutions.size());
        JsonObject stepExecution = stepExecutions.getJsonObject(0);

        //Occasionally the job can get stuck in FAILED and do not want to fail the test because of that
        assumeTrue("STOPPED".equals(stepExecution.getString("batchStatus")));

        //Ensure that the TLJ has sent at least one job log event.
        int topLevelJobCount = getEventCount(server, "/namedCount/TopLevelJob");
        assertTrue(topLevelJobCount >= 1);

        //checking step1
        stepExecutions = serverUtils.getStepExecutionsFromExecutionId(jobExecutionId);
        assertEquals(2, stepExecutions.size());//step1 + _links
        stepExecution1 = serverUtils.getStepExecutionFromExecutionIdAndStepName(jobExecutionId, "step1").getJsonObject(0);

        partitions = stepExecution1.getJsonArray("partitions");
        BatchFatUtils.checkExpectedBatchStatus(partitions, 3);

        //PartitionNum 0 : EndpointServer
        //PartitionNum 1 : EndpointServer
        //PartitionNum 2 : EndpointServer2
        checkPartitionsExitStatus(stepExecution1.getJsonArray("partitions"), new String[] { "JobEventsSingleServer", "JobEventsSingleServer", "JobEventsSingleServer" });

        //Restart the job
        jobInstance = serverUtils.restartJobExecution(jobInstance, jobExecutionId, new Properties());

        Thread.sleep(2 * 1000);

        JsonArray jobExecutions = serverUtils.getJobExecutionsMostRecentFirst(jobInstanceId);

        assertEquals(2, jobExecutions.size());
        jobExecution = jobExecutions.getJsonObject(0);
        assertEquals("STARTED", jobExecution.getString("batchStatus"));
        jobExecutionId = jobExecution.getJsonNumber("executionId").longValue();

        jobInstance = serverUtils.waitForJobInstanceToFinish(jobInstanceId);

        //Get step Executions and check their size and status
        stepExecutions = serverUtils.getStepExecutionsFromExecutionId(jobExecutionId);

        //there will be no step1 partitions run because this is a RESTART_NORMAL as they were run earlier.
        //Only step2 partitions will run
        BatchFatUtils.checkPartitionsSizeAndStatusCompleted(stepExecutions, "step1", 0);
        BatchFatUtils.checkPartitionsSizeAndStatusCompleted(stepExecutions, "step2", 2);

        //There should be at least 1 more then the prior topLevelJobCount
        int topLevelJobCount2 = getEventCount(server, "/namedCount/TopLevelJob");
        assertTrue(topLevelJobCount2 >= topLevelJobCount + 1);
    }

    /**
     * Test that after purging job log events are still available
     */
    @Test
    public void testJobLogEventsAfterJobPurge() throws Exception {

        BatchRestUtils serverUtils = new BatchRestUtils(server);

        JsonObject jobInstance = serverUtils.submitJob("SimpleBatchJob", "test_simplePartition");
        long jobInstanceId = jobInstance.getJsonNumber("instanceId").longValue();
        jobInstance = serverUtils.waitForJobInstanceToFinish(jobInstanceId);
        assertEquals("COMPLETED", jobInstance.getString("batchStatus"));

        //Wait 2 second to ensure the job logger MDB application received the final JMS messages
        Thread.sleep(2 * 1000);

        //Purge the joblogs via the dispatcher.  This should work (response 200).
        HttpUtils.getHttpConnection(serverUtils.buildURL("/ibm/api/batch/jobinstances/" + jobInstanceId),
                                    HttpURLConnection.HTTP_OK,
                                    new int[0],
                                    10,
                                    HTTPRequestMethod.DELETE,
                                    BatchRestUtils.buildHeaderMap(),
                                    null);

        //Should be at least 1 job log part file
        int topLevelJobCount = getEventCount(server, "/namedCount/TopLevelJob");
        assertTrue(topLevelJobCount >= 1);
    }

    /**
     * Test split flow job log event creation
     */
    @Test
    public void testSplitFlowJobLogEventsAfterJobPurge() throws Exception {

        BatchRestUtils serverUtils = new BatchRestUtils(server);

        JsonObject jobInstance = serverUtils.submitJob("SimpleBatchJob", "test_simpleSplitFlow");
        long jobInstanceId = jobInstance.getJsonNumber("instanceId").longValue();
        jobInstance = serverUtils.waitForJobInstanceToFinish(jobInstanceId);
        assertEquals("COMPLETED", jobInstance.getString("batchStatus"));

        //Wait 2 second to ensure the job logger MDB application received the final JMS messages
        Thread.sleep(2 * 1000);

        //Should be at least 3 total: 1 for TLJ and 1 for each flow (flow1 and flow2)
        int topLevelJobCount = getEventCount(server, "/namedCount/TopLevelJob");
        int flowOneCount = getEventCount(server, "/namedCount/flow1");
        int flowTwoCount = getEventCount(server, "/namedCount/flow2");
        assertTrue(topLevelJobCount >= 1);
        assertTrue(flowOneCount >= 1);
        assertTrue(flowTwoCount >= 1);
    }

    /**
     * Test that large job log parts will still send job log event messages
     */
    @Test
    public void testLargeJobLogEventPart() throws Exception {

        BatchRestUtils serverUtils = new BatchRestUtils(server);

        Properties props = new Properties();
        props.put("log.lines.to.print", "10000");

        JsonObject jobInstance = serverUtils.submitJob("SimpleBatchJob", "test_log_control_batchlet", props);
        long jobInstanceId = jobInstance.getJsonNumber("instanceId").longValue();
        jobInstance = serverUtils.waitForJobInstanceToFinish(jobInstanceId);

        assertEquals("COMPLETED", jobInstance.getString("batchStatus"));

        //Wait 2 second to ensure the job logger MDB application received the final JMS messages
        Thread.sleep(2 * 1000);

        //Should be at least 1 job log part
        int topLevelJobCount = getEventCount(server, "/namedCount/TopLevelJob");
        assertTrue(topLevelJobCount >= 1);

    }

    /**
     * Test that after a job fails job log events are sent out
     *
     * Expected FFDC's since the job is force failing
     */
    @Test
    @AllowedFFDC({ "com.ibm.jbatch.container.exception.BatchContainerRuntimeException", "java.lang.RuntimeException" })
    public void testFailedJobLogEvent() throws Exception {

        BatchRestUtils serverUtils = new BatchRestUtils(server);

        Properties props = new Properties();
        props.put("force.failure", "true");

        JsonObject jobInstance = serverUtils.submitJob("SimpleBatchJob", "test_log_control_batchlet", props);
        long jobInstanceId = jobInstance.getJsonNumber("instanceId").longValue();
        jobInstance = serverUtils.waitForJobInstanceToFinish(jobInstanceId);

        assertEquals("FAILED", jobInstance.getString("batchStatus"));

        //Wait 2 second to ensure the job logger MDB application received the final JMS messages
        Thread.sleep(2 * 1000);

        //Should be at least 1 part file
        int topLevelJobCount = getEventCount(server, "/namedCount/TopLevelJob");
        assertTrue(topLevelJobCount >= 1);

    }

    /**
     * Tests that a job with partitioned chunks produces job log events
     */
    @Test
    public void testJobLogEventsWithJmsPartitionedChunks() throws Exception {

        BatchRestUtils serverUtils = new BatchRestUtils(server);

        Properties props = new Properties();
        props.put("numRecords", "1000");
        props.put("numValidationPartitions", "4");
        props.put("chunkSize", "100");

        //submit the job
        JsonObject jobInstance = serverUtils.submitJob("BonusPayout", "BonusPayoutJob.partitioned", props);

        long jobInstanceId = jobInstance.getJsonNumber("instanceId").longValue();
        JsonObject jobExecution = serverUtils.waitForFirstJobExecution(jobInstanceId);
        long jobExecutionId = jobExecution.getJsonNumber("executionId").longValue();

        jobExecution = serverUtils.waitForJobExecutionToStart(jobExecutionId);

        //wait for job to finish executing
        jobInstance = serverUtils.waitForJobInstanceToFinish(jobInstanceId);

        //Wait 2 second to ensure the job logger MDB application received the final JMS messages
        Thread.sleep(2 * 1000);

        //Check to make sure there is at least one part file
        int topLevelJobCount = getEventCount(server, "/namedCount/TopLevelJob");
        assertTrue(topLevelJobCount >= 1);

        assertEquals("COMPLETED", jobInstance.getString("batchStatus"));

        jobExecution = serverUtils.getJobExecutionFromExecutionId(jobExecutionId);
        assertEquals("COMPLETED", jobExecution.getString("batchStatus"));

        JsonArray stepExecutions = serverUtils.getStepExecutionsFromExecutionId(jobExecutionId);
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

    /*
     * Check for checkpoint events.
     *
     * In this check the MDB application checks for a stepExecutionID
     *
     * @throws Exception
     */
    @Test
    public void testReceiveEventsCheckpoint() throws Exception {

        Properties jobParameters = new Properties();
        jobParameters.setProperty("dsJNDI", "jdbc/BonusPayoutDS");
        JsonObject jobInstanceBonusPayout = serverUtils.submitJob("BonusPayout", "BonusPayoutJob", jobParameters);

        JsonObject jobInstanceBonusPayout2 = serverUtils.waitForJobInstanceToFinish(jobInstanceBonusPayout.getJsonNumber("instanceId").longValue());
        assertEquals(BatchStatus.COMPLETED, BatchStatus.valueOf(jobInstanceBonusPayout2.getString("batchStatus")));

        int checkpointEventCount = getEventCount(server, "/namedCount/stepExecutionIdFromCheckpoint");
        assertTrue(checkpointEventCount > 0);

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
    public int getEventCount(LibertyServer server, String path) throws Exception {
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
        Log.info(BatchJmsJobLogEventsSingleServerTest.class, method, String.valueOf(msg));
    }

}
