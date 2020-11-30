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

import static org.hamcrest.core.AnyOf.anyOf;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import javax.json.JsonArray;
import javax.json.JsonObject;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.ibm.ws.jbatch.jms.internal.BatchJmsConstants;

import batch.fat.util.BatchFatUtils;
import componenttest.annotation.AllowedFFDC;
import componenttest.custom.junit.runner.FATRunner;
import componenttest.custom.junit.runner.Mode;
import componenttest.custom.junit.runner.Mode.TestMode;
import componenttest.topology.impl.LibertyServer;
import componenttest.topology.impl.LibertyServerFactory;
import componenttest.topology.utils.HttpUtils;

/**
 * These test heavily depend on the trace logs. Any change in the logging level or runtime methods
 * might fail these tests.
 *
 * PLEASE UPDATE THESE TEST WITH CHANGE IN TRACE AND RUNTIME CODE RELATED TO LOCAL/JMS PARTITIONS
 */

@RunWith(FATRunner.class)
@Mode(TestMode.FULL)
public class BatchJmsMultiJVMPartitionsStopDispatcherTest {

    private static final LibertyServer messageEngineServer = LibertyServerFactory.getLibertyServer("BatchJmsMessageEngine");

    //batch jms end-point server
    //This should run the partitions with partition number 0 and 1
    private static final LibertyServer endpointServer = LibertyServerFactory.getLibertyServer("BatchJmsEndpoint");

    //batch jms end-point server
    //This should run the partitions with partition number 2 and 3
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
            endpointServer.stopServer("CWSIJ0047E");
        }

        if (endpointServer2 != null && endpointServer2.isStarted()) {
            endpointServer2.stopServer("CWSIJ0047E");
        }

        if (dispatcherServer != null && dispatcherServer.isStarted()) {
            dispatcherServer.stopServer("CWSIJ0047E");
        }

        if (messageEngineServer != null && messageEngineServer.isStarted()) {
            messageEngineServer.stopServer("CWSIJ0047E");
        }
    }

    /**
     * Submit a job to a rest interface.
     * Expecting BatchJmsDispatcher to put message on queue
     * Expecting BatchJmsDispatcher will pick up message and run the job
     * BatchJmsEndpoint will begin to process partitions 0 and 1
     * BatchJmsEndpoint2 will begin process partitions 2 and 3
     * Stop BatchJmsDispatcher
     * start BatchJmsDispatcher
     * Expecting all partitions to be marked STOPPED instead of COMPLETE (RTC defect 203063)
     *
     * Expecting FFDCs because the dispatcher is shutdown
     * while jobs are dispatch and running on executors.
     *
     *
     * @throws Exception
     */
    @Test
    @AllowedFFDC({ "com.ibm.ws.sib.jfapchannel.JFapConnectionBrokenException",
                   "javax.jms.InvalidDestinationException", "javax.batch.operations.BatchRuntimeException" })
    public void testJmsPartitionedMultiJVMStopDispatcherJob() throws Exception {

        BatchRestUtils dispatcherUtils = new BatchRestUtils(dispatcherServer);
        int expected_partitions = 4;

        //Submit job
        JsonObject jobInstance = dispatcherUtils.submitJob("SimpleBatchJob", "test_really_sleepy_chunk_partition_multiJvm");

        //Initialize Instance artifacts(jobInstance, jobInstanceId);
        long jobInstanceId = jobInstance.getJsonNumber("instanceId").longValue();

        //Initialize Execution artifacts(jobExecution, jobExecutionId);
        JsonObject jobExecution = dispatcherUtils.waitForFirstJobExecution(jobInstanceId);
        long jobExecutionId = jobExecution.getJsonNumber("executionId").longValue();

        Thread.sleep(2 * 1000);

        //Get the step execution once it has started
        JsonObject stepExecution1 = dispatcherUtils.waitForStepInJobExecutionToStart(jobExecutionId, "step1");

        //Fail vs Ignore test.  For now fail the test.
        assertEquals("STARTED", stepExecution1.getString("batchStatus"));
        //assumeTrue(stepExecution1.getString("batchStatus").equals("STARTED"));

        //Wait (maxTries * 2 seconds) for expected # of partitions or fail out.
        JsonArray partitions;
        int maxTries = 50;
        while (true) {
            stepExecution1 = dispatcherUtils.getStepExecutionFromExecutionIdAndStepName(jobExecutionId, "step1").getJsonObject(0);
            partitions = stepExecution1.getJsonArray("partitions");
            if (partitions.size() == expected_partitions)
                break;

            if (maxTries-- > 0)
                Thread.sleep(2 * 1000);
            else
                fail("Expected Partitions: " + Integer.toString(expected_partitions) + "Only Have: " + Integer.toString(partitions.size()));

        }

        checkNumOfMultiJvmtrueCheckInLog(dispatcherServer, 1);
        checkNumOfStartPartitionRequestInLog(dispatcherServer, expected_partitions);

        //Partition 0 and 1 should run on EndpointServer
        checkNumOfPartitionsInLog(endpointServer, 2);
        checkPartitionNumberPropNotEmptyInLog(endpointServer, 0);
        checkPartitionNumberPropNotEmptyInLog(endpointServer, 1);

        //Partition 2 and 3 should run on EndpointServer2
        checkNumOfPartitionsInLog(endpointServer2, 2);
        checkPartitionNumberPropNotEmptyInLog(endpointServer2, 2);
        checkPartitionNumberPropNotEmptyInLog(endpointServer2, 3);

        BatchFatUtils.stopServer(dispatcherServer);

        //Setup is done. Dispatcher shutdown needs time to propagate to executors.
        Thread.sleep(10 * 1000);

        BatchFatUtils.startServer(dispatcherServer);

        //Check the Execution.
        JsonArray jobExecutions = dispatcherUtils.getJobExecutionsMostRecentFirst(jobInstanceId);

        assertEquals(1, jobExecutions.size());
        jobExecution = jobExecutions.getJsonObject(0);
        jobExecutionId = jobExecution.getJsonNumber("executionId").longValue();

        //Get StepExecutions
        stepExecution1 = dispatcherUtils.getStepExecutionFromExecutionIdAndStepName(jobExecutionId, "step1").getJsonObject(0);

        //Blocking this out because seeing intermittently shift between FAILED AND STOPPED.
        //assertEquals("FAILED", stepExecution1.getString("batchStatus"));

        partitions = stepExecution1.getJsonArray("partitions");
        assertEquals(partitions.size(), expected_partitions);

        //Check all partitions moved to STOPPED and not COMPLETED
        for (int i = 0; i < expected_partitions; i++) {
            String partitionBatchStatus = partitions.getJsonObject(i).getString("batchStatus");

            // Though the test is looking for a STOPPED value, it is possible to get a STARTED or FAILED as well.
            // A STARTED can result if the JVM executing the partition gets swapped out and it doesn't have a chance to react to the stop yet.
            // A FAILED can result if the remote partition gets control right when it is sending back its results to the top-level which
            // has disappeared out from under it."
            assumeTrue(!("STARTED".equals(partitionBatchStatus) || "FAILED".equals(partitionBatchStatus)));

            // At least we're still blowing up if the partition ends up in COMPLETED.  I guess we could just test that it's not COMPLETED.
            // STOPPED or STOPPING is fine here.  On some slow platforms in test we fail here because the partition is still stopping.  Rather
            // than jump through hoops to try to catch exact timing, allow stopping also.
            assertThat(partitions.getJsonObject(i).getString("batchStatus"), anyOf(is("STOPPED"), is("STOPPING")));
        }

    }

    /*
     * checks the number of startPartition Requests in the server log
     *
     * @param server
     *
     * @param numStartPartition
     */
    private static void checkNumOfStartPartitionRequestInLog(LibertyServer server, int numStartPartition) throws Exception {
        assertEquals(numStartPartition, server.findStringsInLogsAndTraceUsingMark("BatchJmsDispatcher > startPartition Entry").size());

    }

    /*
     * checks the isMultiJvm true check count in the server log
     *
     * @param server
     *
     * @param numMultiJvmCheck
     */
    private static void checkNumOfMultiJvmtrueCheckInLog(LibertyServer server, int numMultiJvmCheck) throws Exception {
        assertEquals(numMultiJvmCheck, server.findStringsInLogsAndTraceUsingMark("isMultiJvm RETURN true").size());

    }

    /*
     * checks the partition Count in the server log
     *
     * @param server
     *
     * @param expectedPartitionCount
     */
    private static void checkNumOfPartitionsInLog(LibertyServer server, int expectedPartitionCount) throws Exception {
        assertEquals(expectedPartitionCount, server.findStringsInLogsAndTraceUsingMark("handleStartPartitionRequest Entry").size());

    }

    /*
     * checks no given partitionNumber jms property in the log
     *
     * @param server
     *
     * @param partitionNumber
     */
    private static void checkPartitionNumberPropNotEmptyInLog(LibertyServer server, int partitionNumber) throws Exception {
        assertFalse(server.findStringsInLogsAndTraceUsingMark(BatchJmsConstants.PROPERTY_NAME_PARTITION_NUM + ": " + partitionNumber).isEmpty());

    }

}
