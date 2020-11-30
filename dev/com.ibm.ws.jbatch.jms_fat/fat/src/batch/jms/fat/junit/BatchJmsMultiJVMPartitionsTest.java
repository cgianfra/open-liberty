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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import java.nio.file.Paths;
import java.util.Properties;

import javax.batch.runtime.BatchStatus;
import javax.json.JsonArray;
import javax.json.JsonObject;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.ibm.websphere.simplicity.log.Log;
import com.ibm.ws.jbatch.jms.internal.BatchJmsConstants;
import com.ibm.ws.jbatch.test.dbservlet.DbServletClient;

import batch.fat.util.BatchFatUtils;
import componenttest.annotation.AllowedFFDC;
import componenttest.custom.junit.runner.FATRunner;
import componenttest.custom.junit.runner.Mode;
import componenttest.custom.junit.runner.Mode.TestMode;
import componenttest.rules.repeater.JakartaEE9Action;
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
public class BatchJmsMultiJVMPartitionsTest {

    private static final LibertyServer messageEngineServer = LibertyServerFactory.getLibertyServer("BatchJmsMessageEngine");

    //batch jms endpoint server
    //This should run the partitions with partition number 0 and 1
    private static final LibertyServer endpointServer = LibertyServerFactory.getLibertyServer("BatchJmsEndpoint");

    //batch jms endpoint server
    //This should run the partitions with partitoin number 2
    private static final LibertyServer endpointServer2 = LibertyServerFactory.getLibertyServer("BatchJmsEndpoint2");

    //batch jms dispatcher server
    //This should run as dispatcher as well as endpoint for all top-level jobs
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

        if (JakartaEE9Action.isActive()) {
            JakartaEE9Action.transformApp(Paths.get(messageEngineServer.getServerRoot(), "dropins", "DbServletApp.war"));
            JakartaEE9Action.transformApp(Paths.get(dispatcherServer.getServerRoot(), "dropins", "BonusPayout.war"));
            JakartaEE9Action.transformApp(Paths.get(dispatcherServer.getServerRoot(), "dropins", "DbServletApp.war"));
            JakartaEE9Action.transformApp(Paths.get(dispatcherServer.getServerRoot(), "dropins", "jmsweb.war"));
            JakartaEE9Action.transformApp(Paths.get(dispatcherServer.getServerRoot(), "dropins", "SimpleBatchJob.war"));
            JakartaEE9Action.transformApp(Paths.get(endpointServer.getServerRoot(), "dropins", "BonusPayout.war"));
            JakartaEE9Action.transformApp(Paths.get(endpointServer.getServerRoot(), "dropins", "DbServletApp.war"));
            JakartaEE9Action.transformApp(Paths.get(endpointServer.getServerRoot(), "dropins", "SimpleBatchJob.war"));
            JakartaEE9Action.transformApp(Paths.get(endpointServer.getServerRoot(), "dropins", "SimpleBatchJobCopy.war"));
            JakartaEE9Action.transformApp(Paths.get(endpointServer2.getServerRoot(), "dropins", "BonusPayout.war"));
            JakartaEE9Action.transformApp(Paths.get(endpointServer2.getServerRoot(), "dropins", "DbServletApp.war"));
            JakartaEE9Action.transformApp(Paths.get(endpointServer2.getServerRoot(), "dropins", "SimpleBatchJob.war"));
        }

        //clean start server with Message Engine first
        messageEngineServer.startServer();
        String uploadMessage = messageEngineServer.waitForStringInLogUsingMark(serverStartedMsg, messageEngineServer.getMatchingLogFile("trace.log"));
        assertNotNull("Could not find CWWKF0011I (server start info message) in trace.log of " + messageEngineServer.getServerName(), uploadMessage);
        System.out.println("CGCG MultiJVMPartitionTest messageEngineServer port = " + messageEngineServer.getHttpDefaultPort());

        //clean start dispatcher
        dispatcherServer.startServer();
        uploadMessage = dispatcherServer.waitForStringInLogUsingMark(serverStartedMsg, dispatcherServer.getMatchingLogFile("trace.log"));
        assertNotNull("Could not find CWWKF0011I (server start info message) in trace.log of " + dispatcherServer.getServerName(), uploadMessage);
        System.out.println("CGCG MultiJVMPartitionTest dispatcherServer port = " + dispatcherServer.getHttpDefaultPort());
        assertEquals(Integer.getInteger("batch.dispatcher_1_HTTP_default").intValue(), dispatcherServer.getHttpDefaultPort());

        //clean start endpoint
        endpointServer.startServer();
        uploadMessage = endpointServer.waitForStringInLogUsingMark(serverStartedMsg, endpointServer.getMatchingLogFile("trace.log"));
        assertNotNull("Could not find CWWKF0011I (server start info message) in trace.log of " + endpointServer.getServerName(), uploadMessage);
        System.out.println("CGCG MultiJVMPartitionTest endpointServer port = " + endpointServer.getHttpDefaultPort());
        assertEquals(Integer.getInteger("batch.endpoint_1_HTTP_default").intValue(), endpointServer.getHttpDefaultPort());

        //clean start second endpoint
        endpointServer2.startServer();
        uploadMessage = endpointServer2.waitForStringInLogUsingMark(serverStartedMsg, endpointServer2.getMatchingLogFile("trace.log"));
        assertNotNull("Could not find CWWKF0011I (server start info message) in trace.log of " + endpointServer2.getServerName(), uploadMessage);
        System.out.println("CGCG MultiJVMPartitionTest endpointServer2 port = " + endpointServer2.getHttpDefaultPort());
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
//    @AfterClass
//    public static void afterClass() throws Exception {
//        if (dispatcherServer != null && dispatcherServer.isStarted()) {
//            dispatcherServer.stopServer("CWSIJ0047E");
//        }
//
//        if (endpointServer != null && endpointServer.isStarted()) {
//            endpointServer.stopServer("CWSIJ0047E");
//        }
//
//        if (endpointServer2 != null && endpointServer2.isStarted()) {
//            endpointServer2.stopServer("CWSIJ0047E");
//        }
//
//        if (messageEngineServer != null && messageEngineServer.isStarted()) {
//            messageEngineServer.stopServer("CWSIJ0047E");
//        }
//    }

    /**
     * Submit a job to a rest interface.
     * Expecting BatchJmsDispatcher to put message on queue
     * Expecting BatchJmsDispatcher will pick up message and run the job
     * BatchJmsEndpoint will process partitions 0 and 1
     * BatchJmsEndpoint2 will process partitions after 2
     */

    @AllowedFFDC({ "com.ibm.wsspi.sib.core.exception.SIConnectionDroppedException" })
    @Test
    public void testJmsPartitionedJob() throws Exception {

        BatchRestUtils dispatcherUtils = new BatchRestUtils(dispatcherServer);

        //Submit job
        JsonObject jobInstance = dispatcherUtils.submitJob("SimpleBatchJob", "test_simplePartition");

        //Initialize Instance artifacts(jobInstance, jobInstanceId);
        long jobInstanceId = jobInstance.getJsonNumber("instanceId").longValue();

        //Initialize Execution artifacts(jobExecution, jobExecutionId);
        JsonObject jobExecution = dispatcherUtils.waitForFirstJobExecution(jobInstanceId);
        long jobExecutionId = jobExecution.getJsonNumber("executionId").longValue();

        Thread.sleep(2 * 1000);

        //Wait for JobInstance to Finish
        jobInstance = dispatcherUtils.waitForJobInstanceToFinish(jobInstance.getJsonNumber("instanceId").longValue(), 70);

        //Updating artifacts
        jobInstanceId = jobInstance.getJsonNumber("instanceId").longValue();
        jobExecution = dispatcherUtils.getOnlyJobExecution(jobInstanceId);
        jobExecutionId = jobExecution.getJsonNumber("executionId").longValue();

        //Checking
        assertTrue(BatchRestUtils.isDone(jobInstance));
        assertEquals("test_simplePartition", BatchRestUtils.getJobName(jobInstance));
        assertEquals(ADMIN_NAME, BatchRestUtils.getSubmitter(jobInstance));
        assertEquals("SimpleBatchJob#SimpleBatchJob.war", BatchRestUtils.getAppName(jobInstance));
        assertEquals("COMPLETED", jobInstance.getString("batchStatus"));

        //Get step executions
        JsonArray stepExecutions = dispatcherUtils.getStepExecutionFromExecutionIdAndStepName(jobExecutionId, "step1");

        //Setup Step Artifacts and checking
        assertEquals(stepExecutions.size(), 2); //0: step1, 1: _links

        JsonObject stepExecution = stepExecutions.getJsonObject(0);
        BatchFatUtils.checkPartitionsSizeAndStatusCompleted(stepExecution, 3);

        JsonArray partitions = stepExecution.getJsonArray("partitions");

        for (int i = 0; i < 3; i++) {
            assertFalse("restUrl was found to be empty", partitions.getJsonObject(i).getString("restUrl").equals(""));
            assertFalse("serverId was found to be empty", partitions.getJsonObject(i).getString("serverId").equals(""));
        }

        //PartitionNum 0 : EndpointServer
        //PartitionNum 1 : EndpointServer
        //PartitionNum 2 : EndpointServer2
        checkPartitionsExitStatus(stepExecution.getJsonArray("partitions"), new String[] { "BatchJmsEndpoint", "BatchJmsEndpoint", "BatchJmsEndpoint2" });

    }

    @Test
    // The logic in com.ibm.jbatch.container.controller.impl.PartitionedStepControllerImpl.waitForNextPartitionToFinish() is fragile
    // since we might not want to wait long enough from the top level for a response from  a remote partition, given that the job has been told to stop.
    // If this test fails reguarly then quarantine, it was useful to run it in development at least.
    //@Mode(TestMode.QUARANTINE)

    // If remote partition can't send back response.
    @AllowedFFDC({ "com.ibm.ws.sib.jfapchannel.JFapConnectionBrokenException", "javax.jms.JMSException",
                   "javax.jms.InvalidDestinationException", "javax.batch.operations.BatchRuntimeException" })
    public void testStopJobExecution() throws Exception {

        String method = "testStopJobExecution";

        BatchRestUtils dispatcherUtils = new BatchRestUtils(dispatcherServer);

        //Submit the job
        Properties props = new Properties();
        int numPartitions = 4;
        props.setProperty("numPartitions", Integer.toString(numPartitions));
        JsonObject jobInstance = dispatcherUtils.submitJob("SimpleBatchJob", "test_partition_chunk");

        long jobInstanceId = jobInstance.getJsonNumber("instanceId").longValue();
        JsonObject jobExecution = dispatcherUtils.waitForFirstJobExecution(jobInstanceId);
        long jobExecutionId = jobExecution.getJsonNumber("executionId").longValue();

        // TODO:  If this fails because step2 has COMPLETED then there's nothing wrong with the runtime, but the test logic needs to wait
        // or adjust to allow COMPLETED here.
        dispatcherUtils.waitForStepInJobExecutionToStart(jobExecutionId, "step2");

        // Give the partitions a chance to start
        Thread.sleep(3000);

        jobExecution = dispatcherUtils.stopJobExecutionAndWaitUntilDone(jobExecutionId);

        // Confirm job is STOPPED
        BatchStatus jobBS = BatchStatus.valueOf(jobExecution.getString("batchStatus"));
        // Assume we have plenty of time to stop, could allow COMPLETED if not.
        //assertTrue("Unexpected job execution batch status = " + jobBS, jobBS.equals(BatchStatus.STOPPED) || jobBS.equals(BatchStatus.COMPLETED));
        assertTrue("Unexpected job execution batch status = " + jobBS, jobBS.equals(BatchStatus.STOPPED));

        // Confirm step1 is COMPLETED
        JsonArray stepExecutions1 = dispatcherUtils.getStepExecutionFromExecutionIdAndStepName(jobExecutionId, "step1");
        assertEquals("COMPLETED", stepExecutions1.getJsonObject(0).getString("batchStatus"));

        // Give the partitions a chance to stop
        //Thread.sleep(3000);
        dispatcherUtils.waitForStepInJobExecutionToReachStatus(jobExecutionId, "step2", BatchStatus.STOPPED);

        // Confirm step2 is STOPPED
        JsonObject stepExecution2 = dispatcherUtils.getStepExecutionFromExecutionIdAndStepName(jobExecutionId, "step2").getJsonObject(0);
        BatchStatus step2BS = BatchStatus.valueOf(stepExecution2.getString("batchStatus"));
        // Assume we have plenty of time to stop, could allow STARTED or COMPLETED if not.
        //assertTrue("Unexpected step2 stepexecution status = " + step2BS, step2BS.equals(BatchStatus.STOPPED) || step2BS.equals(BatchStatus.COMPLETED));

        assertTrue("Unexpected step2 stepexecution status = " + step2BS, step2BS.equals(BatchStatus.STOPPED));

        // Check step2 partitions
        JsonArray partitionExecutions = stepExecution2.getJsonArray("partitions");

        assertFalse("Too many partitions: " + partitionExecutions.size(), partitionExecutions.size() > numPartitions);

        boolean notStopped = false;

        for (int i = 0; i < 30; i++) {

            log(method, "Checking status of partitions, attempt #" + i);
            for (Object partitionExecution : partitionExecutions.toArray()) {
                String batchStatusStr = ((JsonObject) partitionExecution).getString("batchStatus");
                log(method, "Partition #" + ((JsonObject) partitionExecution).getInt("partitionNumber") + " is " + batchStatusStr);

                if (!batchStatusStr.equals("STOPPED"))
                    notStopped = true;
                //assertTrue("Found partition with status = " + batchStatusStr, batchStatusStr.equals("STOPPED"));
            }

            if (notStopped) {
                Thread.sleep(1000);
                stepExecution2 = dispatcherUtils.getStepExecutionFromExecutionIdAndStepName(jobExecutionId, "step2").getJsonObject(0);
                partitionExecutions = stepExecution2.getJsonArray("partitions");
                notStopped = false;
            } else {
                log(method, "Found all partitions stopped");
                break;
            }
        }

        assertFalse("Timed out waiting for all partitions to stop", notStopped);

        /*
         * for (Object partitionExecution : partitionExecutions.toArray()) {
         * String batchStatusStr = ((JsonObject) partitionExecution).getString("batchStatus");
         * //assertTrue("Found partition with status = " + batchStatusStr, batchStatusStr.equals("STOPPED") || batchStatusStr.equals("COMPLETED"));
         * // We have plenty of time to stop.
         * assertTrue("Found partition with status = " + batchStatusStr, batchStatusStr.equals("STOPPED"));
         * }
         */
    }

    /*
     * This tests that if the partitions of step1 are not completed, the step 2 must not be processed
     * stopping endpointServer2 will cause the partitionNum 2 to not be consumed
     */
    @Ignore("don't have this functionality yet")
    @Test
    public void testPartitionMessageTimeout() throws Exception {

        BatchFatUtils.stopServer(endpointServer2);

        BatchRestUtils dispatcherUtils = new BatchRestUtils(dispatcherServer);

        //Submit the job
        JsonObject jobInstance = dispatcherUtils.submitJob("SimpleBatchJob", "test_sleepy_partition");

        //Initialize Instance artifacts(jobInstance, jobInstanceId);
        long jobInstanceId = jobInstance.getJsonNumber("instanceId").longValue();

        //Initialize Execution artifacts(jobExecution, jobExecutionId);
        JsonObject jobExecution = dispatcherUtils.waitForFirstJobExecution(jobInstanceId);
        long jobExecutionId = jobExecution.getJsonNumber("executionId").longValue();

        Thread.sleep(2 * 1000);

        //Get StepExecutions
        dispatcherUtils.getStepExecutionFromExecutionIdAndStepName(jobExecutionId, "step1");
        Thread.sleep(20 * 1000);
        dispatcherUtils.getStepExecutionsFromExecutionId(jobExecutionId);

        //Wait for the message to timeout

        //TODO asserts to check timeout

        //Checking that step1 should be completed after the timeout
        JsonArray stepExecutions = dispatcherUtils.getStepExecutionsFromExecutionId(jobExecutionId);
        BatchFatUtils.checkPartitionsSizeAndStatusCompleted(stepExecutions, "step1", 2);

        BatchFatUtils.startServer(endpointServer2);

    }

    /*
     * This tests that if a stop has been issued, the messages in the queue
     * should not be processed
     *
     * Stop endpointServer2, the message with PartitionNum 2 will never be consumed
     */
    @Ignore("don't have this functionality yet")
    @Test
    public void testDiscardMessagesAfterStopThenRestart() throws Exception {

        BatchFatUtils.stopServer(endpointServer2);
        BatchFatUtils.stopServer(endpointServer);
        BatchRestUtils dispatcherUtils = new BatchRestUtils(dispatcherServer);

        //submit the job
        JsonObject jobInstance = dispatcherUtils.submitJob("SimpleBatchJob", "test_simplePartition");
        long jobInstanceId = jobInstance.getJsonNumber("instanceId").longValue();
        JsonObject JobExecution = dispatcherUtils.waitForFirstJobExecution(jobInstanceId);
        long jobExecutionId = dispatcherUtils.getMostRecentExecutionIdFromInstance(jobInstance);

        //Let the partitions be dispatched and 0 and 1 to be consumed
        Thread.sleep(3 * 1000);

        //Check stepExecutions. Step2 should not start since step1 is not finished
        JsonArray stepExecutions = dispatcherUtils.getStepExecutionsFromExecutionId(jobExecutionId);
        assertEquals(2, stepExecutions.size());
        JsonObject stepExecution1 = stepExecutions.getJsonObject(0);
        assertEquals("STARTED", stepExecution1.getString("batchStatus"));

        //get partitions
        JsonArray partitions = stepExecution1.getJsonArray("partitions");

        //Stop the job
        JsonObject jobExecution = dispatcherUtils.stopJobExecutionAndWaitUntilDone(jobExecutionId);

        assertEquals("STOPPED", jobExecution.getString("batchStatus"));

        //Start endpoint2
        BatchFatUtils.startServer(endpointServer2);
        BatchFatUtils.startServer(endpointServer);

        //Wait for it to consume the messages
        Thread.sleep(10 * 1000);

//        //Check if partition discarded or not
//        checkStoppedMessageInLog(endpointServer, jobExecutionId, 2);
//        checkStoppedMessageInLog(endpointServer2, jobExecutionId, 1);

        //Reset the mark to end of trace.log
        BatchFatUtils.setMarkToEndOfTraceForAllServers(dispatcherServer, endpointServer, endpointServer2);

        //Restart the job
        Properties props = new Properties();
        jobInstance = dispatcherUtils.restartJobExecution(jobInstance, jobExecutionId, props);

        //Wait for the job to complete
        Thread.sleep(20 * 1000);

//        jobExecution = dispatcherUtils.waitForJobInstanceToStart(jobInstanceId);
//
//        jobInstance = dispatcherUtils.waitForJobInstanceToFinish(jobInstanceId);
//
//        jobExecutionId = jobExecution.getInt("executionId");

        jobInstance = dispatcherUtils.getJobInstance(jobInstanceId);
        JsonArray jobExecutions = dispatcherUtils.getJobExecutionsMostRecentFirst(jobInstanceId);
        jobExecution = dispatcherUtils.getJobExecutionFromExecutionId(jobExecutions.getJsonObject(0).getJsonNumber("executionId").longValue());

        //Checking
        assertTrue(BatchRestUtils.isDone(jobInstance));
        assertEquals("test_simplePartition", BatchRestUtils.getJobName(jobInstance));
        assertEquals(ADMIN_NAME, BatchRestUtils.getSubmitter(jobInstance));
        assertEquals("SimpleBatchJob#SimpleBatchJob.war", BatchRestUtils.getAppName(jobInstance));
        assertEquals("COMPLETED", jobInstance.getString("batchStatus"));

        //Get step executions
        stepExecutions = dispatcherUtils.getStepExecutionFromExecutionIdAndStepName(jobExecutionId, "step1");

        //Setup Step Artifacts and checking
        assertEquals(stepExecutions.size(), 2); //0: step1, 1: _links
        BatchFatUtils.checkPartitionsSizeAndStatusCompleted(stepExecutions, "step1", 3);

        //Dispatcher should dispatch all partitions since it is running the top job
        checkNumOfMultiJvmtrueCheckInLog(dispatcherServer, 1); //1 Top level + 3 subJob Partitions
        checkNumOfStartPartitionRequestInLog(dispatcherServer, 3);

        //Partition 0 and 1 should run on EndpointServer
        checkNumOfPartitionsInLog(endpointServer, 2);
        checkPartitionNumberPropNotEmptyInLog(endpointServer, 0);
        checkPartitionNumberPropNotEmptyInLog(endpointServer, 1);

        //Partition 2 should run on EndpointServer2
        checkNumOfPartitionsInLog(endpointServer2, 1);
        checkPartitionNumberPropNotEmptyInLog(endpointServer2, 2);

    }

    /**
     * This tests that if a job has been restarted after a server failure/restart that
     * the outdated messages on the queue are not processed.
     *
     * @throws Exception
     */
    @Test
    // If remote partition can't send back response.
    @AllowedFFDC({ "com.ibm.ws.sib.jfapchannel.JFapConnectionBrokenException",
                   "javax.jms.InvalidDestinationException",
                   "javax.batch.operations.BatchRuntimeException",
                   "com.ibm.wsspi.sib.core.exception.SITemporaryDestinationNotFoundException",
                   "javax.resource.spi.UnavailableException",
    })
    public void testDiscardMessagesAfterRestart() throws Exception {

        BatchRestUtils dispatcherUtils = new BatchRestUtils(dispatcherServer);

        // Stop the endpoints
        BatchFatUtils.stopServer(endpointServer2);
        BatchFatUtils.stopServer(endpointServer);

        //Submit the job to the dispatcher
        JsonObject jobInstance = dispatcherUtils.submitJob("SimpleBatchJob", "test_simplePartition");
        long jobInstanceId = jobInstance.getJsonNumber("instanceId").longValue();
        jobInstance = dispatcherUtils.waitForJobInstanceToStart(jobInstanceId);

        //Wait for the job to dispatch Partitions
        Thread.sleep(10 * 1000);
        // Get the execution id
        long jobExecutionId = dispatcherUtils.getMostRecentExecutionIdFromInstance(jobInstance);

        // Check stepExecutions. Step2 should not start since step1 is not finished
        JsonArray stepExecutions = dispatcherUtils.getStepExecutionsFromExecutionId(jobExecutionId);
        assertEquals(2, stepExecutions.size());
        JsonObject stepExecution1 = stepExecutions.getJsonObject(0);
        assertEquals("STARTED", stepExecution1.getString("batchStatus"));
        assertEquals("step1", stepExecution1.getString("stepName"));

        // Stop the job
        dispatcherUtils.stopJob(jobExecutionId);

        // Wait for it to stop
        jobInstance = dispatcherUtils.waitForJobInstanceToFinish(jobInstanceId);

        // Check batchStatus is STOPPING
        JsonObject jobExecution = dispatcherUtils.getJobExecutionFromExecutionId(jobExecutionId);
        assertEquals("STOPPED", jobExecution.getString("batchStatus"));

        // Restart the job - Should be old messages on the queue that are out of date!
        dispatcherUtils.restartJobExecution(jobInstance, jobExecutionId, new Properties());
        jobExecution = dispatcherUtils.getJobExecutionFromExecutionId(jobExecutionId);

        // Bring the Executor servers back up one by one and check the discard
        // message in the trace.log
        BatchFatUtils.startServer(endpointServer);
        BatchFatUtils.startServer(endpointServer2);
        Thread.sleep(30 * 1000);

        checkDiscardedMessageInLog(endpointServer, jobExecutionId, 2);

        checkDiscardedMessageInLog(endpointServer2, jobExecutionId, 1);
    }

    /*
     * Tests stop then restart while partitions are running on multiple servers
     *
     * Allowed FFDCs because when the TLJ is stopped in the dispatcher,
     * it stops listening to the reply from the partition executor
     */
    @Test
    @AllowedFFDC({ "com.ibm.ws.sib.jfapchannel.JFapConnectionBrokenException",
                   "com.ibm.ws.sib.jfapchannel.JFapHeartbeatTimeoutException" })
    public void testMultiJvmPartitionsRestart() throws Exception {

        BatchRestUtils dispatcherUtils = new BatchRestUtils(dispatcherServer);

        //Submit job
        JsonObject jobInstance = dispatcherUtils.submitJob("SimpleBatchJob", "test_sleepy_partition");

        //Initialize Instance artifacts(jobInstance, jobInstanceId);
        long jobInstanceId = jobInstance.getJsonNumber("instanceId").longValue();

        //Initialize Execution artifacts(jobExecution, jobExecutionId);
        JsonObject jobExecution = dispatcherUtils.waitForFirstJobExecution(jobInstanceId);
        long jobExecutionId = jobExecution.getJsonNumber("executionId").longValue();

        //Wait for step1 to get created and started
        JsonObject stepExecution1 = dispatcherUtils.waitForStepInJobExecutionToReachStatus(jobExecutionId, "step1", BatchStatus.STARTED);

        JsonArray partitions;

        //Check for 3 partitions to start running. Once they start, execute the stop
        for (int i = 0; i < 30; i++) {
            stepExecution1 = dispatcherUtils.getStepExecutionFromExecutionIdAndStepName(jobExecutionId, "step1").getJsonObject(0);
            partitions = stepExecution1.getJsonArray("partitions");
            if (partitions.size() == 3) {
                break;
            }
            Thread.sleep(1000);
        }

        //Stop the job
        jobExecution = dispatcherUtils.stopJobExecutionAndWaitUntilDone(jobExecutionId);

        assertEquals("Checking job exec batch status after stop", "STOPPED", jobExecution.getString("batchStatus"));

        JsonArray stepExecutions = jobExecution.getJsonArray("stepExecutions");
        assertEquals(1, stepExecutions.size());
        JsonObject stepExecution = stepExecutions.getJsonObject(0);
        assertEquals("Checking step exec batch status after stop", "STOPPED", stepExecution.getString("batchStatus"));

        //Allow enough time to get all the running partitions to complete
        //Because we don't have stop functioning properly for batchlet partitions
        //TODO remove waiting when partitions stop is implemented and
        //change COMPLETED to STOPPED for partition status
        Thread.sleep(20 * 1000);

        //checking step1
        stepExecutions = dispatcherUtils.getStepExecutionsFromExecutionId(jobExecutionId);
        assertEquals(2, stepExecutions.size());//step1 + _links
        stepExecution1 = dispatcherUtils.getStepExecutionFromExecutionIdAndStepName(jobExecutionId, "step1").getJsonObject(0);

        assertEquals("STOPPED", stepExecution1.getString("batchStatus"));
        partitions = stepExecution1.getJsonArray("partitions");
        BatchFatUtils.checkExpectedBatchStatus(partitions, 3);

        //No step2 because the job was stopped before step2 started

        //Dispatcher should dispatch all partitions since it is running the top job
        checkNumOfMultiJvmtrueCheckInLog(dispatcherServer, 1); //step1 + 3 partitions
        checkNumOfStartPartitionRequestInLog(dispatcherServer, 3);

        //PartitionNum 0 : EndpointServer
        //PartitionNum 1 : EndpointServer
        //PartitionNum 2 : EndpointServer2
        checkPartitionsExitStatus(stepExecution1.getJsonArray("partitions"), new String[] { "BatchJmsEndpoint", "BatchJmsEndpoint", "BatchJmsEndpoint2" });

        //Reset the mark to end of trace.log
        BatchFatUtils.setMarkToEndOfTraceForAllServers(dispatcherServer, endpointServer, endpointServer2);

        //Restart the job
        jobInstance = dispatcherUtils.restartJobExecution(jobInstance, jobExecutionId, new Properties());

        Thread.sleep(2 * 1000);

        JsonArray jobExecutions = dispatcherUtils.getJobExecutionsMostRecentFirst(jobInstanceId);

        assertEquals(2, jobExecutions.size());
        jobExecution = jobExecutions.getJsonObject(0);

        //Too many timing issues trying to sort out started, and its not really a necessary check here.
        //If there's a problem, we'll see it when we wait for the instance to finish (if we don't start
        //, then that check will timeout and fail the test)
        //assertEquals("STARTED", jobExecution.getString("batchStatus"));
        jobExecutionId = jobExecution.getJsonNumber("executionId").longValue();

        jobInstance = dispatcherUtils.waitForJobInstanceToFinish(jobInstanceId);

        //Get step Executions and check their size and status
        stepExecutions = dispatcherUtils.getStepExecutionsFromExecutionId(jobExecutionId);

        //there will be no step1 partitions run because this is a RESTART_NORMAL as they were run earlier.
        //Only step2 partitions will run
        BatchFatUtils.checkPartitionsSizeAndStatusCompleted(stepExecutions, "step1", 0);
        BatchFatUtils.checkPartitionsSizeAndStatusCompleted(stepExecutions, "step2", 2);

        //Dispatcher should dispatch all partitions since it is running the top job

        //PartitionNum 0 : EndpointServer
        //PartitionNum 1 : EndpointServer
        //PartitionNum 2 : DispatcherServer

        JsonObject step2Execution = dispatcherUtils.getStepExecutionFromExecutionIdAndStepName(jobExecutionId, "step2").getJsonObject(0);
        checkPartitionsExitStatus(step2Execution.getJsonArray("partitions"), new String[] { "BatchJmsEndpoint", "BatchJmsEndpoint" });

        // Prevents previous FFDCs from being detected for these servers.
        BatchFatUtils.restartServer(endpointServer);
        BatchFatUtils.restartServer(endpointServer2);
    }

    /*
     * Tests the partitions are not run on multiple servers if disabled from the job properties
     */
    @Test
    public void testMultiJvmPartitionsDisabled() throws Exception {

        BatchRestUtils dispatcherUtils = new BatchRestUtils(dispatcherServer);

        //Submit job
        JsonObject jobInstance = dispatcherUtils.submitJob("SimpleBatchJob", "test_simplePartition_multiJvm_disabled");

        //Initialize Instance artifacts(jobInstance, jobInstanceId);
        long jobInstanceId = jobInstance.getJsonNumber("instanceId").longValue();

        jobInstance = dispatcherUtils.waitForJobInstanceToFinish(jobInstanceId);
        long jobExecutionId = dispatcherUtils.getOnlyExecutionIdFromInstance(jobInstance);

        dispatcherUtils.getStepExecutionsFromExecutionId(jobExecutionId);

        JsonArray stepExecutions = dispatcherUtils.getStepExecutionsFromExecutionId(jobExecutionId);
        BatchFatUtils.checkPartitionsSizeAndStatusCompleted(stepExecutions, "step1", 3);

        //Checking
        assertTrue(BatchRestUtils.isDone(jobInstance));
        assertEquals("test_simplePartition_multiJvm_disabled", BatchRestUtils.getJobName(jobInstance));
        assertEquals(ADMIN_NAME, BatchRestUtils.getSubmitter(jobInstance));
        assertEquals("SimpleBatchJob#SimpleBatchJob.war", BatchRestUtils.getAppName(jobInstance));
        assertEquals("COMPLETED", jobInstance.getString("batchStatus"));

        JsonArray linkArray = jobInstance.getJsonArray("_links");
        assertNotNull(linkArray);

        JsonArray partitions = stepExecutions.getJsonObject(0).getJsonArray("partitions");
        for (int i = 0; i < 3; i++) {
            assertTrue("restUrl was not empty", partitions.getJsonObject(i).getString("restUrl").equals(""));
            assertTrue("serverId was not empty", partitions.getJsonObject(i).getString("serverId").equals(""));
        }

        //PartitionNum 0 : DispatcherServer
        //PartitionNum 1 : DispatcherServer
        //PartitionNum 2 : DispatcherServer
        checkPartitionsExitStatus(stepExecutions.getJsonObject(0).getJsonArray("partitions"), new String[] { "BatchJmsDispatcher", "BatchJmsDispatcher", "BatchJmsDispatcher" });

    }

    /*
     * This tests running a job with partitioned chunks
     */
    @Test
    public void testJmsPartitionedBonusPayoutJobPartitioned() throws Exception {

        BatchRestUtils dispatcherUtils = new BatchRestUtils(dispatcherServer);

        Properties props = new Properties();
        props.put("numRecords", "503");
        props.put("numValidationPartitions", "3");
        props.put("chunkSize", "17");

        //Adding this to gererate a fileNotFoundException on Validation partition running on a remote executor
        props.put("generateFileNameRoot", ".");

        //submit the job
        JsonObject jobInstance = dispatcherUtils.submitJob("BonusPayout", "BonusPayoutJob.partitioned.collector", props);

        long jobInstanceId = jobInstance.getJsonNumber("instanceId").longValue();
        JsonObject jobExecution = dispatcherUtils.waitForFirstJobExecution(jobInstanceId);
        long jobExecutionId = jobExecution.getJsonNumber("executionId").longValue();

        jobExecution = dispatcherUtils.waitForJobExecutionToStart(jobExecutionId);

        //wait for job to finish executing
        jobInstance = dispatcherUtils.waitForJobInstanceToFinish(jobInstanceId);
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

        BatchFatUtils.checkExpectedBatchStatus(validationPartitions, 3, BatchStatus.COMPLETED);

        //Dispatcher should dispatch all partitions since it is running the top job
        checkNumOfMultiJvmtrueCheckInLog(dispatcherServer, 1);

        checkNumOfStartPartitionRequestInLog(dispatcherServer, 3);

        //Partition 0 and 1 should run on EndpointServer
        checkNumOfPartitionsInLog(endpointServer, 2);
        checkPartitionNumberPropNotEmptyInLog(endpointServer, 0);
        checkPartitionNumberPropNotEmptyInLog(endpointServer, 1);

        //Partition 2 should run on EndpointServer2
        checkNumOfPartitionsInLog(endpointServer2, 1);
        checkPartitionNumberPropNotEmptyInLog(endpointServer2, 2);

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

    /*
     * checks for outdated JobExecution discard message in the log
     *
     * @param server
     *
     * @param stepName
     */
    private static void checkDiscardedMessageInLog(LibertyServer server, long jobExecutionId, int expectedCount) throws Exception {
        assertEquals(expectedCount,
                     server.findStringsInLogsAndTraceUsingMark(" discarding message with Top Level Execution Id = " + jobExecutionId
                                                               + " since it was not the newest Execution").size());
    }

    /**
     * Issues a message if the assumption is NOT true.
     */
    private void myAssumeTrue(boolean isItTrue, String msg) {

        if (!isItTrue) {
            log("ASSUMPTION FAILED", msg);
        }

        assumeTrue(isItTrue);
    }

    /**
     * helper for simple logging.
     */
    private void log(String method, String msg) {
        Log.info(this.getClass(), method, msg);
    }

    /*
     * Method to verify exitStatus for each partition
     *
     * @param list of partitions returned in the JSON response
     *
     * @param list of exitStatus for each partitionNumber starting from partitionNumber : 0
     */
    public void checkPartitionsExitStatus(JsonArray partitions, String[] exitStatusList) {
        for (int i = 0; i < exitStatusList.length; i++) {

            assertEquals(exitStatusList[i], partitions.getJsonObject(i).getString("exitStatus"));

        }
    }

}
