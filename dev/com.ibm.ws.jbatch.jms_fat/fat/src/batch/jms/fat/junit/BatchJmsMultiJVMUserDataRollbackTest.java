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
import static org.junit.Assert.fail;

import java.util.Properties;

import javax.json.JsonArray;
import javax.json.JsonObject;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.ibm.ws.jbatch.test.dbservlet.DbServletClient;

import batch.fat.util.BatchFatUtils;
import componenttest.annotation.AllowedFFDC;
import componenttest.custom.junit.runner.FATRunner;
import componenttest.custom.junit.runner.Mode;
import componenttest.custom.junit.runner.Mode.TestMode;
import componenttest.topology.impl.LibertyServer;
import componenttest.topology.impl.LibertyServerFactory;
import componenttest.topology.utils.HttpUtils;

/**
 * These test depends on the trace logs. Any change in the logging level or runtime methods
 * might fail these tests.
 *
 * PLEASE UPDATE THESE TEST WITH CHANGE IN TRACE AND RUNTIME CODE RELATED TO LOCAL/JMS PARTITIONS
 */
@RunWith(FATRunner.class)
@Mode(TestMode.FULL)
public class BatchJmsMultiJVMUserDataRollbackTest {

    private static final LibertyServer messageEngineServer = LibertyServerFactory.getLibertyServer("BatchJmsMessageEngine");

    //batch jms end-point server
    private static final LibertyServer endpointServer = LibertyServerFactory.getLibertyServer("BatchJmsEndpoint");

    //batch jms end-point server
    private static final LibertyServer endpointServer2 = LibertyServerFactory.getLibertyServer("BatchJmsEndpoint2");

    //batch jms dispatcher server
    //This should run as dispatcher as well as end-point for all top-level jobs
    private static final LibertyServer dispatcherServer = LibertyServerFactory.getLibertyServer("BatchJmsDispatcher");

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

        //Many tests look at the trace.log file for method entries and should have an accurate count.
        //Hence setting mark before each test is run
        BatchFatUtils.setMarkToEndOfTraceForAllServers(dispatcherServer, endpointServer, endpointServer2);
    }

    /**
     * Startup the servers
     */
    @BeforeClass
    public static void beforeClass() throws Exception {

        HttpUtils.trustAllCertificates();
        String serverStartedMsg = "CWWKF0011I:.*";

        //set port in LibertyServer object because it doesn't return the correct port value if substitution is used in server.xml

        dispatcherServer.setServerConfigurationFile("MultiJvmPartitionsDispatcher/server.xml");
        endpointServer.setServerConfigurationFile("MultiJvmPartitionsEndpoint/server.xml");
        endpointServer2.setServerConfigurationFile("MultiJvmPartitionsEndpoint2/server.xml");

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

        // Setup BonusPayout app tables
        new DbServletClient().setDataSourceJndi("jdbc/BonusPayoutDS").setDataSourceUser("user",
                                                                                        "pass").setHostAndPort(endpointServer2.getHostname(),
                                                                                                               endpointServer2.getHttpDefaultPort()).loadSql(endpointServer2.pathToAutoFVTTestFiles
                                                                                                                                                             + "common/BonusPayout.derby.ddl",
                                                                                                                                                             "JBATCH",
                                                                                                                                                             "").executeUpdate();
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
            endpointServer.stopServer();
        }

        if (endpointServer2 != null && endpointServer2.isStarted()) {
            endpointServer2.stopServer();
        }

        if (dispatcherServer != null && dispatcherServer.isStarted()) {
            dispatcherServer.stopServer();
        }

        if (messageEngineServer != null && messageEngineServer.isStarted()) {
            messageEngineServer.stopServer("CWSIC2019E", "CWSIJ0047E");
        }
    }

    /**
     * Submit a BonusPayout job to a rest interface.
     * Ensure that the persistent user data is initialized
     * Ensure that the validation step is in STARTED state
     * Stop BatchJmsExecutor
     * Start BatchJmsExecutor
     * Ensure that if the job is in STARTED state that it is stopped
     * Restart the job execution
     * Ensure the validation step and the job finish in the COMPLETED state
     *
     * Expecting FFDCs because an executor is shutdown
     * while jobs are dispatch and running on it.
     *
     *
     * @throws Exception
     */
    @Test
    @AllowedFFDC({ "com.ibm.ws.sib.jfapchannel.JFapConnectionBrokenException",
                   "javax.jms.InvalidDestinationException", "javax.batch.operations.BatchRuntimeException",
                   "com.ibm.jbatch.container.exception.BatchContainerRuntimeException" })
    public void testJmsMultiJVMUserDataRollbackTest() throws Exception {

        BatchRestUtils dispatcherUtils = new BatchRestUtils(dispatcherServer);
        String stepName = "validation";
        int expected_partitions = 3;
        long TIMEOUT_VALUE = 30;

        //Submit a bonus payout job with a chunk size of 10 and 5000 records
        Properties props = new Properties();
        props.put("chunkSize", "10");
        props.put("numRecords", "5000");
        props.put("numValidationPartitions", "3");

        //Initialize Instance artifacts(jobInstance, jobInstanceId)
        JsonObject jobInstance = dispatcherUtils.submitJob("BonusPayout", "BonusPayoutJob.partitioned", props);
        long jobInstanceId = jobInstance.getJsonNumber("instanceId").longValue();

        //Initialize Execution artifacts(jobExecution, jobExecutionId);
        JsonObject jobExecution = dispatcherUtils.waitForFirstJobExecution(jobInstanceId);
        long jobExecutionId = jobExecution.getJsonNumber("executionId").longValue();

        jobExecution = dispatcherUtils.waitForJobExecutionToStart(jobExecutionId);

        dispatcherUtils.waitForStepInJobExecutionToComplete(jobExecutionId, "generate");
        dispatcherUtils.waitForStepInJobExecutionToComplete(jobExecutionId, "addBonus");

        //Wait for the validation step to start
        JsonObject stepExecution = dispatcherUtils.waitForStepInJobExecutionToStart(jobExecutionId, stepName);

        //Wait (maxTries * 1 seconds) for expected # of partitions and for the first partition to perform a commit or fail out.
        JsonArray partitions;
        int maxTries = 90;
        int commitCount = 0;
        while (true) {
            stepExecution = dispatcherUtils.getStepExecutionFromExecutionIdAndStepName(jobExecutionId, "validation").getJsonObject(0);
            partitions = stepExecution.getJsonArray("partitions");
            if (partitions.size() == expected_partitions) {
                //get the commit count from the metrics to ensure that the persistent user data is initialized
                commitCount = Integer.parseInt(partitions.getJsonObject(0).getJsonObject("metrics").getString("COMMIT_COUNT"));
                if (commitCount > 0) {
                    Assert.assertNotSame("COMPLETED", stepExecution.getString("batchStatus"));
                    break;
                }
            }
            // If 90 seconds seems like a long time to wait, keep in mind we have a remote partition dispatch here, and there might need to
            // be some initalization in DS and DB connections on the partition executor before the partition actually starts processing records.
            if (maxTries-- > 0) {
                Thread.sleep(1 * 1000);
            } else {
                fail("Expected Partitions: " + expected_partitions + ", but we Have: " + Integer.toString(partitions.size()) +
                     " partitions. Expecting commitCount > 0, we have commitCount: " + commitCount);
            }
        }

        BatchFatUtils.stopServer(endpointServer);

        Thread.sleep(10 * 1000);

        BatchFatUtils.startServer(endpointServer);

        //Job could be in STARTED state or FAILED state; need to stop a STARTED job before restarting
        jobInstance = dispatcherUtils.getJobInstance(jobInstanceId);
        if (jobInstance.getString("batchStatus").equals("STARTED")) {
            jobInstance = dispatcherUtils.stopJobExecutionAndWaitUntilDone(jobExecutionId, TIMEOUT_VALUE);
        }

        //Restart the Job execution
        jobInstance = dispatcherUtils.restartJobExecution(jobInstance, jobExecutionId, props);
        assertEquals(jobInstanceId, BatchRestUtils.instanceId(jobInstance));

        //Wait until job is started before getting the latest execution info
        dispatcherUtils.waitForJobInstanceToStart(jobInstanceId);

        //Get the new execution ID after the job is restarted
        jobExecution = dispatcherUtils.getJobExecutionsMostRecentFirst(jobInstanceId).getJsonObject(0);
        jobExecutionId = jobExecution.getJsonNumber("executionId").longValue();

        //wait for job to finish executing
        jobInstance = dispatcherUtils.waitForJobInstanceToFinish(jobInstanceId);

        //Sometimes funky stuff happens after a restart, we might need to relax this check.
        assertTrue(jobInstance.getString("batchStatus").equals("COMPLETED"));

        JsonArray stepExecutions = dispatcherUtils.getStepExecutionFromExecutionIdAndStepName(jobExecutionId, stepName);

        //Verify that the validation step is in the COMPLETED state
        JsonObject validationStep = stepExecutions.getJsonObject(0);
        assertEquals("validation", validationStep.getString("stepName"));
        assertEquals("COMPLETED", validationStep.getString("batchStatus"));
    }
}
