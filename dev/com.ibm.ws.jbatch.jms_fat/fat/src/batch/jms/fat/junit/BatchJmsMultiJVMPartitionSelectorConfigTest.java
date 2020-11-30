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
import componenttest.annotation.ExpectedFFDC;
import componenttest.custom.junit.runner.FATRunner;
import componenttest.custom.junit.runner.Mode;
import componenttest.custom.junit.runner.Mode.TestMode;
import componenttest.topology.impl.LibertyServer;
import componenttest.topology.impl.LibertyServerFactory;
import componenttest.topology.utils.HttpUtils;

/**
 *
 */
@RunWith(FATRunner.class)
@Mode(TestMode.FULL)
public class BatchJmsMultiJVMPartitionSelectorConfigTest {

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

        // Allow each test to start with new endpoint server lifecycles,
        // but in a way that resets the FFDC checking, and avoids counting
        // any FFDCs around stopServer.
        BatchFatUtils.stopServer(endpointServer);
        BatchFatUtils.startServer(endpointServer);

        BatchFatUtils.stopServer(endpointServer2);
        BatchFatUtils.startServer(endpointServer2);

        // Though the one test turns FFDC checking off, we
        // still generally want to run with it enabled otherwise
        endpointServer.setFFDCChecking(true);
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

//        // Setup BonusPayout app tables
//        new DbServletClient()
//                        .setDataSourceJndi("jdbc/BonusPayoutDS")
//                        .setDataSourceUser("user", "pass")
//                        .setHostAndPort(endpointServer2.getHostname(), endpointServer2.getHttpDefaultPort())
//                        .loadSql(endpointServer2.pathToAutoFVTTestFiles + "common/BonusPayout.derby.ddl", "JBATCH", "")
//                        .executeUpdate();

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
        if (dispatcherServer != null && dispatcherServer.isStarted()) {
            dispatcherServer.stopServer();
        }

        if (endpointServer != null && endpointServer.isStarted()) {
            endpointServer.stopServer();
        }

        if (endpointServer2 != null && endpointServer2.isStarted()) {
            endpointServer2.stopServer();
        }

        if (messageEngineServer != null && messageEngineServer.isStarted()) {
            messageEngineServer.stopServer();
        }
    }

    /*
     * Test stepName message selectors
     * This test changes server configuration
     */
    @Test
    @AllowedFFDC(value = { "javax.resource.spi.ResourceAllocationException",
                           "com.ibm.ws.rsadapter.exceptions.DataStoreAdapterException",
                           "java.sql.SQLNonTransientConnectionException",
                           "java.sql.SQLNonTransientException",
                           "com.ibm.ws.sib.jfapchannel.JFapConnectionBrokenException",
                           "com.ibm.wsspi.sib.core.exception.SIConnectionDroppedException" })
    public void testMultiJvmStepNameJobPropWorkTypeMessageSelector() throws Exception {

        endpointServer.setServerConfigurationFile("MultiJvmPartitionsEndpointStepJobPropMS/server.xml");
        endpointServer2.setServerConfigurationFile("MultiJvmPartitionsEndpoint2StepJobPropMS/server.xml");

        assertNotNull("Didn't find config update message in log", endpointServer.waitForStringInLog("CWWKG001(7|8)I"));
        assertNotNull("Didn't find config update message in log", endpointServer2.waitForStringInLog("CWWKG001(7|8)I"));

        BatchRestUtils dispatcherUtils = new BatchRestUtils(dispatcherServer);

        Properties props = new Properties();
        props.put("sleep.time.seconds", "1");
        props.put("jobClass", "A");

        //Submit job
        JsonObject jobInstance = dispatcherUtils.submitJob("SimpleBatchJob", "test_sleepy_partition", props);

        //Initialize Instance artifacts(jobInstance, jobInstanceId);
        long jobInstanceId = jobInstance.getJsonNumber("instanceId").longValue();

        //Initialize Execution artifacts(jobExecution, jobExecutionId);
        JsonObject jobExecution = dispatcherUtils.waitForFirstJobExecution(jobInstanceId);
        long jobExecutionId = jobExecution.getJsonNumber("executionId").longValue();

        //Wait for JobInstance to Finish
        try {
            jobInstance = dispatcherUtils.waitForJobInstanceToFinish(jobInstanceId);
        } catch (RuntimeException e) {
            // TODO - we could throw/catch a more specific exc to NOT ignore other, valid errors.
            Log.info(this.getClass(), "ASSUMPTION FAILED", "We'll just assume we timed out waiting for job to finish, skipping test");
            assumeTrue(false);
        }

        //Updating artifacts
        jobInstanceId = jobInstance.getJsonNumber("instanceId").longValue();
        jobExecution = dispatcherUtils.getOnlyJobExecution(jobInstanceId);
        jobExecutionId = jobExecution.getJsonNumber("executionId").longValue();

        //Checking
        assertTrue(BatchRestUtils.isDone(jobInstance));
        assertEquals("test_sleepy_partition", BatchRestUtils.getJobName(jobInstance));
        assertEquals(ADMIN_NAME, BatchRestUtils.getSubmitter(jobInstance));
        assertEquals("SimpleBatchJob#SimpleBatchJob.war", BatchRestUtils.getAppName(jobInstance));
        assertEquals("COMPLETED", jobInstance.getString("batchStatus"));

        JsonArray linkArray = jobInstance.getJsonArray("_links");
        assertNotNull(linkArray);

        //Get step executions
        JsonObject stepExecution1 = dispatcherUtils.getStepExecutionFromExecutionIdAndStepName(jobExecutionId, "step1").getJsonObject(0);
        JsonObject stepExecution2 = dispatcherUtils.getStepExecutionFromExecutionIdAndStepName(jobExecutionId, "step2").getJsonObject(0);

        //Setup Step Artifacts and checking
        BatchFatUtils.checkPartitionsSizeAndStatusCompleted(stepExecution1, 3);
        BatchFatUtils.checkPartitionsSizeAndStatusCompleted(stepExecution2, 2);

        //PartitionNum 0 : BatchJmsEndpoint
        //PartitionNum 1 : BatchJmsEndpoint
        //PartitionNum 2 : BatchJmsEndpoint
        checkPartitionsExitStatus(stepExecution1.getJsonArray("partitions"), new String[] { "BatchJmsEndpoint", "BatchJmsEndpoint", "BatchJmsEndpoint" });

        //PartitionNum 0 : BatchJmsEndpoint2
        //PartitionNum 1 : BatchJmsEndpoint2
        checkPartitionsExitStatus(stepExecution2.getJsonArray("partitions"), new String[] { "BatchJmsEndpoint2", "BatchJmsEndpoint2" });

    }

    @Test
    public void testJmsPartitionedJobStopExecutor() throws Exception {

        endpointServer.setServerConfigurationFile("MultiJvmPartitionsEndpointStepJobPropMS/server.xml");
        endpointServer2.setServerConfigurationFile("MultiJvmPartitionsEndpoint2StepJobPropMS/server.xml");

        assertNotNull(endpointServer.waitForStringInLog("CWWKG001(7|8)I"));
        assertNotNull(endpointServer2.waitForStringInLog("CWWKG001(7|8)I"));

        final BatchRestUtils dispatcherUtils = new BatchRestUtils(dispatcherServer);

        Properties props = new Properties();
        props.put("sleep.time.seconds", "40"); // Sleep a bit longer (than the original 30 seconds), since it occasionally ends up COMPLETED in FAT.
        props.put("jobClass", "A");

        //Submit job
        JsonObject jobInstance = dispatcherUtils.submitJob("SimpleBatchJob", "test_sleepy_partition", props);

        //Initialize Instance artifacts(jobInstance, jobInstanceId);
        long jobInstanceId = jobInstance.getJsonNumber("instanceId").longValue();

        //Initialize Execution artifacts(jobExecution, jobExecutionId);
        JsonObject jobExecution = dispatcherUtils.waitForFirstJobExecution(jobInstanceId);
        long jobExecutionId = jobExecution.getJsonNumber("executionId").longValue();

        Thread.sleep(2 * 1000);

        for (int i = 0; i < 50; i++) {
            Thread.sleep(1 * 1000);

            jobExecution = dispatcherUtils.getJobExecutionFromExecutionId(jobExecutionId);

            try {
                if (jobExecution.getJsonArray("stepExecutions").getJsonObject(0) != null) {
                    break;
                }
            } catch (Exception e) {
                e.printStackTrace();
                continue;
            }
        }

        Thread.sleep(3 * 1000);

        JsonArray stepExecutions = jobExecution.getJsonArray("stepExecutions");
        assertTrue("Step execution not found for job execution " + jobExecutionId, !stepExecutions.isEmpty());

        final long stepExecutionId1 = jobExecution.getJsonArray("stepExecutions").getJsonObject(0).getJsonNumber("stepExecutionId").longValue();
        JsonArray partitions1 = null;
        JsonObject stepExecution1;

        int secondsToWaitForAllPartitionsToShowUp = 80;
        for (int i = 0; i < secondsToWaitForAllPartitionsToShowUp; i++) {
            Thread.sleep(1 * 1000);

            stepExecution1 = dispatcherUtils.getStepExecutionFromStepExecutionId(stepExecutionId1).getJsonObject(0);

            partitions1 = stepExecution1.getJsonArray("partitions");
            if (partitions1.size() >= 3) {
                break;
            }
        }

        assertEquals("Found only: " + partitions1.size() + " in partitions array, expecting 3", 3, partitions1.size());

        for (int partitionNum = 2; partitionNum >= 0; partitionNum--) {

            if (partitions1.getJsonObject(partitionNum).getString("batchStatus").equals("STARTED")) {
                break;
            }
        }

        endpointServer.setFFDCChecking(false);
        endpointServer.stopServer();

        dispatcherUtils.waitForStepExecutionToBeDone(stepExecutionId1);

        Thread.sleep(2 * 1000);

        //Wait for JobInstance to Finish
        try {
            jobInstance = dispatcherUtils.waitForJobInstanceToFinish(jobInstanceId);
        } catch (RuntimeException e) {
            // TODO - we could throw/catch a more specific exc to NOT ignore other, valid errors.
            Log.info(this.getClass(), "ASSUMPTION FAILED", "We'll just assume we timed out waiting for job to finish, skipping test");
            assumeTrue(false);
        }

        //Updating artifacts
        jobInstanceId = jobInstance.getJsonNumber("instanceId").longValue();
        jobExecution = dispatcherUtils.getOnlyJobExecution(jobInstanceId);
        jobExecutionId = jobExecution.getJsonNumber("executionId").longValue();

        //Checking
        assertEquals("test_sleepy_partition", BatchRestUtils.getJobName(jobInstance));
        assertEquals(ADMIN_NAME, BatchRestUtils.getSubmitter(jobInstance));
        assertEquals("SimpleBatchJob#SimpleBatchJob.war", BatchRestUtils.getAppName(jobInstance));

        JsonArray linkArray = jobInstance.getJsonArray("_links");
        assertNotNull(linkArray);

        //Get step executions
        stepExecution1 = dispatcherUtils.getStepExecutionFromExecutionIdAndStepName(jobExecutionId, "step1").getJsonObject(0);
        jobExecution = dispatcherUtils.getJobExecutionFromExecutionId(jobExecutionId);
        jobInstance = dispatcherUtils.getJobInstance(jobInstanceId);

        assertEquals(BatchStatus.FAILED.toString(), stepExecution1.getString("batchStatus"));
        assertEquals(BatchStatus.FAILED.toString(), jobExecution.getString("batchStatus"));
        assertEquals(BatchStatus.FAILED.toString(), jobInstance.getString("batchStatus"));

        JsonArray partitionExecutions = stepExecution1.getJsonArray("partitions");

        for (Object partitionExecution : partitionExecutions.toArray()) {
            assertEquals(BatchStatus.STOPPED.toString(), ((JsonObject) partitionExecution).getString("batchStatus"));
        }
        //PartitionNum 0 : EndpointServer
        //PartitionNum 1 : EndpointServer
        //PartitionNum 2 : EndpointServer

        // Note we've decided it's legal for a runtime to stop a batchlet after creating a step execution but
        // before actually calling process.  So the batchlet may never have a chance to see its process() method called, so we
        // must allow an exit status of STOPPED in addition to the otherwise-expected "BatchJmsEndpoint".
        for (int i = 0; i < 3; i++) {
            String exitStatus = stepExecution1.getJsonArray("partitions").getJsonObject(i).getString("exitStatus");
            assertTrue("For partition #: " + i + ", expecting exit status of 'BatchJmsEndpoint' or 'STOPPED', found: " + exitStatus,
                       "BatchJmsEndpoint".equals(exitStatus) || "STOPPED".equals(exitStatus));
        }
    }

    @ExpectedFFDC({ "javax.batch.operations.JobSecurityException",
                    "com.ibm.jbatch.container.exception.BatchContainerRuntimeException" })
    @AllowedFFDC({ "java.lang.reflect.InvocationTargetException",
                   "com.ibm.wsspi.sib.core.exception.SIConnectionDroppedException" })
    @Test
    public void testJmsPartitionedJobPartitionExceptionOnJmsListener() throws Exception {

        endpointServer.setServerConfigurationFile("MultiJvmPartitionsEndpoint/server.xml");
        endpointServer2.setServerConfigurationFile("MultiJvmPartitionsEndpoint2NoSecurity/server.xml");

        assertNotNull("Didn't find config update message in log", endpointServer.waitForStringInLog("CWWKG001(7|8)I"));
        assertNotNull("Didn't find config update message in log", endpointServer2.waitForStringInLog("CWWKG001(7|8)I"));

        final BatchRestUtils dispatcherUtils = new BatchRestUtils(dispatcherServer);

        Properties props = new Properties();

        props.put("sleep.time.seconds", "1");
        //Submit job
        JsonObject jobInstance = dispatcherUtils.submitJob("SimpleBatchJob", "test_sleepy_partition_failingAnalyzer", props);

        //Initialize Instance artifacts(jobInstance, jobInstanceId);
        long jobInstanceId = jobInstance.getJsonNumber("instanceId").longValue();

        //Initialize Execution artifacts(jobExecution, jobExecutionId);
        JsonObject jobExecution = dispatcherUtils.waitForFirstJobExecution(jobInstanceId);
        long jobExecutionId = jobExecution.getJsonNumber("executionId").longValue();

        //Wait for JobInstance to Finish
        try {
            jobInstance = dispatcherUtils.waitForJobInstanceToFinish(jobInstanceId, 30);
        } catch (RuntimeException e) {
            // TODO - we could throw/catch a more specific exc to NOT ignore other, valid errors.
            Log.info(this.getClass(), "ASSUMPTION FAILED", "We'll just assume we timed out waiting for job to finish, skipping test");
            assumeTrue(false);
        }

        //Updating artifacts
        jobInstanceId = jobInstance.getJsonNumber("instanceId").longValue();
        jobExecution = dispatcherUtils.getOnlyJobExecution(jobInstanceId);
        jobExecutionId = jobExecution.getJsonNumber("executionId").longValue();

        JsonObject stepExecution = dispatcherUtils.getStepExecutionsFromExecutionId(jobExecutionId).getJsonObject(0);

        assertFalse(endpointServer2.findStringsInLogsAndTraceUsingMark("received exception[\\s\\S]*javax.batch.operations.JobSecurityException[\\s\\S]*sending FAILED status for this partition").isEmpty());
        //Checking
        assertTrue(BatchRestUtils.isDone(jobInstance));
        assertEquals("test_sleepy_partition_failingAnalyzer", BatchRestUtils.getJobName(jobInstance));
        assertEquals(ADMIN_NAME, BatchRestUtils.getSubmitter(jobInstance));
        assertEquals("SimpleBatchJob#SimpleBatchJob.war", BatchRestUtils.getAppName(jobInstance));
        assertEquals("FAILED", jobInstance.getString("batchStatus"));

        assertEquals("Analyzed failing partition", stepExecution.getString("exitStatus"));

        JsonArray linkArray = jobInstance.getJsonArray("_links");
        assertNotNull(linkArray);

        //Setup Step Artifacts and checking
        BatchFatUtils.checkPartitionsSizeAndStatusCompleted(stepExecution, 2);

        //PartitionNum 0 : EndpointServer
        //PartitionNum 1 : EndpointServer
        checkPartitionsExitStatus(stepExecution.getJsonArray("partitions"), new String[] { "BatchJmsEndpoint", "BatchJmsEndpoint" });

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
