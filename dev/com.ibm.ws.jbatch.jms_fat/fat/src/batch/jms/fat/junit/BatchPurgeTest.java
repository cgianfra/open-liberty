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

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import javax.json.JsonArray;
import javax.json.JsonObject;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.ibm.websphere.simplicity.log.Log;
import com.ibm.ws.jbatch.test.FatUtils;

import batch.fat.util.BatchFatUtils;
import batch.fat.util.InstanceStateMirrorImage;
import componenttest.annotation.AllowedFFDC;
import componenttest.annotation.ExpectedFFDC;
import componenttest.custom.junit.runner.FATRunner;
import componenttest.custom.junit.runner.Mode;
import componenttest.custom.junit.runner.Mode.TestMode;
import componenttest.rules.repeater.JakartaEE9Action;
import componenttest.topology.impl.LibertyServer;
import componenttest.topology.impl.LibertyServerFactory;
import componenttest.topology.utils.HttpUtils;
import sun.net.www.protocol.http.HttpURLConnection;

@RunWith(FATRunner.class)
@Mode(TestMode.FULL)
public class BatchPurgeTest {

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

    // Utils for each of the servers
    BatchRestUtils dispatcherUtils = new BatchRestUtils(dispatcherServer);
    BatchRestUtils endpointUtils = new BatchRestUtils(endpointServer);
    BatchRestUtils endpoint2Utils = new BatchRestUtils(endpointServer2);

    // As defined in the server.xml
    protected final static String ADMIN_NAME = "bob";
    protected final static String ADMIN_PASSWORD = "bobpwd";

    private final static String jobName = "test_sleepy_partition";
    private final static String firstStepName = "step1";

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
     * Startup the servers
     */
    @BeforeClass
    public static void beforeClass() throws Exception {

        HttpUtils.trustAllCertificates();
        HttpURLConnection.setFollowRedirects(true);
        String serverStartedMsg = "CWWKF0011I:.*";

        //set port in LibertyServer object because it doesn't return the correct port value if substitution is used in server.xml
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

        //clean start dispatcher
        dispatcherServer.startServer();
        uploadMessage = dispatcherServer.waitForStringInLogUsingMark(serverStartedMsg, dispatcherServer.getMatchingLogFile("trace.log"));
        assertNotNull("Could not find CWWKF0011I (server start info message) in trace.log of " + dispatcherServer.getServerName(), uploadMessage);
        assertEquals(Integer.getInteger("batch.dispatcher_1_HTTP_default").intValue(), dispatcherServer.getHttpDefaultPort());
        dispatcherServer.setServerConfigurationFile("MultiJvmLocalJobLogPurgeDispatcher/server.xml");
        assertNotNull("Didn't find config update message in log", dispatcherServer.waitForStringInLog("CWWKG001(7|8)I"));

        //clean start endpoint
        endpointServer.startServer();
        uploadMessage = endpointServer.waitForStringInLogUsingMark(serverStartedMsg, endpointServer.getMatchingLogFile("trace.log"));
        assertNotNull("Could not find CWWKF0011I (server start info message) in trace.log of " + endpointServer.getServerName(), uploadMessage);
        assertEquals(Integer.getInteger("batch.endpoint_1_HTTP_default").intValue(), endpointServer.getHttpDefaultPort());
        endpointServer.setServerConfigurationFile("MultiJvmLocalJobLogPurgeEndpoint/server.xml");
        assertNotNull("Didn't find config update message in log", endpointServer.waitForStringInLog("CWWKG001(7|8)I"));

        //clean start second endpoint
        endpointServer2.startServer();
        uploadMessage = endpointServer2.waitForStringInLogUsingMark(serverStartedMsg, endpointServer2.getMatchingLogFile("trace.log"));
        assertNotNull("Could not find CWWKF0011I (server start info message) in trace.log of " + endpointServer2.getServerName(), uploadMessage);
        assertEquals(Integer.getInteger("batch.endpoint_2_HTTP_default").intValue(), endpointServer2.getHttpDefaultPort());
        endpointServer2.setServerConfigurationFile("MultiJvmLocalJobLogPurgeEndpoint2/server.xml");
        assertNotNull("Didn't find config update message in log", endpointServer2.waitForStringInLog("CWWKG001(7|8)I"));

        // Setup certificate for use in SSL
        BatchRestUtils.exchangeCertificates(dispatcherServer, endpointServer);
        BatchRestUtils.exchangeCertificates(dispatcherServer, endpointServer2);

        // Restart so the server notices the certificate we just added to its keystore
        dispatcherServer.stopServer("CWWKG0033W");
        endpointServer.stopServer("CWWKG0033W");
        endpointServer2.stopServer("CWWKG0033W");

        dispatcherServer.startServer();
        endpointServer.startServer();
        endpointServer2.startServer();

        FatUtils.waitForStartupAndSsl(dispatcherServer);
        FatUtils.waitForStartupAndSsl(endpointServer);
        FatUtils.waitForStartupAndSsl(endpointServer2);

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
     * Test purging logs from one instance on two different servers
     */
    @Test
    @AllowedFFDC("com.ibm.jbatch.container.exception.BatchContainerRuntimeException")
    public void testMultiJvmLocalJobLogPurgeOneInstance() throws Exception {

        String methodName = "testMultiJvmLocalJobLogPurgeOneInstance";
        log(methodName, " Start ");

        // Submit jobs to each endpoint
        List<Long> jobIds = submitJobExecutionOnEachEndpoint();
        long jobInstanceId = jobIds.get(0);

        // Purge the job instance logs
        log(methodName, "Purging local job logs");
        dispatcherUtils.purgeJobInstance(jobInstanceId);

        // Check to make sure all the job logs and db entries are removed
        dispatcherUtils.checkFileSystemEntriesRemoved(jobInstanceId, jobName, methodName);
        endpointUtils.checkFileSystemEntriesRemoved(jobInstanceId, jobName, methodName);
        endpoint2Utils.checkFileSystemEntriesRemoved(jobInstanceId, jobName, methodName);
        dispatcherUtils.checkDBEntriesRemoved(jobInstanceId, jobName, methodName);

        log(methodName, " End ");

    }

    @Test
    @ExpectedFFDC("com.ibm.ws.jbatch.rest.internal.resources.RequestException")
    public void testPurgeOfQueuedJob() throws Exception {

        String methodName = "testPurgeOfQueuedJob";
        log(methodName, " Start ");

        JsonObject jobInstance = dispatcherUtils.submitJob("MockApp", "MockJob", new Properties());
        long jobInstanceId = jobInstance.getJsonNumber("instanceId").longValue();

        assertEquals("Expecting JMS_QUEUED after dispatched", InstanceStateMirrorImage.JMS_QUEUED.toString(),
                     jobInstance.getString("instanceState"));

        Thread.sleep(6000);

        // Expecting it to still be JMS_QUEUED since no app is configured to consume it.
        jobInstance = dispatcherUtils.getJobInstance(jobInstanceId);
        assertEquals("Expecting JMS_QUEUED still, after waiting", InstanceStateMirrorImage.JMS_QUEUED.toString(),
                     jobInstance.getString("instanceState"));

        boolean caughtError = false;
        try {
            dispatcherUtils.purgeJobInstance(jobInstanceId);
        } catch (AssertionError e) {
            log(methodName, " Caught expected error since we can't purge a queued job ");
            caughtError = true;
        }
        assertTrue("Expecting failure on purge", caughtError);

        long jobExecutionId = dispatcherUtils.getMostRecentExecutionIdFromInstance(jobInstance);
        log(methodName, " Stop execId = " + jobExecutionId);
        dispatcherUtils.stopJob(jobExecutionId);

        log(methodName, " Purge");
        dispatcherUtils.purgeJobInstance(jobInstanceId);

        log(methodName, " End ");
    }

    /*
     * Test purging logs from multiple instances on two different servers
     */
    @Test
    @AllowedFFDC("com.ibm.jbatch.container.exception.BatchContainerRuntimeException")
    public void testMultiJvmLocalJobLogPurgeMultipleInstances() throws Exception {

        String methodName = "testMultiJvmLocalJobLogPurgeMultipleInstances";
        log(methodName, " Start ");

        // Submit two jobs to each endpoint
        List<Long> jobIds1 = submitJobExecutionOnEachEndpoint();
        long jobInstanceId1 = jobIds1.get(0);

        List<Long> jobIds2 = submitJobExecutionOnEachEndpoint();
        long jobInstanceId2 = jobIds2.get(0);

        // Purge the job instance logs
        log(methodName, "Purging job logs");
        dispatcherUtils.purgeJobInstances(jobInstanceId1, jobInstanceId2);

        // Check to make sure all the job logs and db entries are removed
        dispatcherUtils.checkFileSystemEntriesRemoved(jobInstanceId1, jobName, methodName);
        endpointUtils.checkFileSystemEntriesRemoved(jobInstanceId1, jobName, methodName);
        endpoint2Utils.checkFileSystemEntriesRemoved(jobInstanceId1, jobName, methodName);
        dispatcherUtils.checkDBEntriesRemoved(jobInstanceId1, jobName, methodName);

        dispatcherUtils.checkFileSystemEntriesRemoved(jobInstanceId2, jobName, methodName);
        endpointUtils.checkFileSystemEntriesRemoved(jobInstanceId2, jobName, methodName);
        endpoint2Utils.checkFileSystemEntriesRemoved(jobInstanceId2, jobName, methodName);
        dispatcherUtils.checkDBEntriesRemoved(jobInstanceId2, jobName, methodName);

        log(methodName, " End ");

    }

    @Test
    @ExpectedFFDC("java.net.ConnectException")
    @AllowedFFDC("com.ibm.jbatch.container.exception.BatchContainerRuntimeException")
    public void testMultiJvmLocalJobLogPurgeServerUnavailableOneInstance() throws Exception {

        String methodName = "testMultiJvmLocalJobLogPurgeServerUnavailableOneInstance";
        log(methodName, " Start ");

        List<Long> jobIds = submitJobExecutionOnEachEndpoint();
        long jobInstanceId = jobIds.get(0);

        // Stop endpoint server
        endpointServer.stopServer();
        endpointServer2.stopServer();

        // Purge the job instance logs from the dispatcher
        log(methodName, "Purging local job logs");

        dispatcherUtils.purgeJobInstance(jobInstanceId, HttpURLConnection.HTTP_INTERNAL_ERROR);

        // Check that endpoint files and db entries were not removed because the server was unavailable
        endpointUtils.checkFileSystemEntriesExist(jobInstanceId, jobName, methodName);
        endpoint2Utils.checkFileSystemEntriesExist(jobInstanceId, jobName, methodName);
        dispatcherUtils.checkDBEntriesExist(jobInstanceId, jobName, methodName);

        // Run purge with db only flag
        dispatcherUtils.purgeJobInstanceFromDBOnly(jobInstanceId);

        // Check that the db entries are now removed
        dispatcherUtils.checkDBEntriesRemoved(jobInstanceId, jobName, methodName);

        log(methodName, " End ");

    }

    @Test
    @ExpectedFFDC("java.net.ConnectException")
    @AllowedFFDC("com.ibm.jbatch.container.exception.BatchContainerRuntimeException")
    public void testMultiJvmLocalJobLogPurgeServerUnavailableTwoInstances() throws Exception {

        String methodName = "testMultiJvmLocalJobLogPurgeServerUnavailableTwoInstances";
        log(methodName, " Start ");

        // Submit a job instance to each endpoint
        JsonObject jobInstance = endpointUtils.submitJob("SimpleBatchJob", jobName);
        long jobInstanceId = jobInstance.getJsonNumber("instanceId").longValue();

        JsonObject jobExecution = endpointUtils.waitForFirstJobExecution(jobInstanceId);
        long jobExecutionId = jobExecution.getJsonNumber("executionId").longValue();
        // Since we want to check the entries make it into STEPTHREADEXECUTION and/or are deleted, let's make sure we advance far enough
        // since beginning of job != beginning of first step.
        endpointUtils.waitForStepInJobExecutionToStart(jobExecutionId, firstStepName);
        endpointUtils.stopJobExecutionAndWaitUntilDone(jobExecutionId, 30);

        List<Long> jobIds = submitJobExecutionOnEachEndpoint();
        long jobInstanceId2 = jobIds.get(0);

        // Stop endpoint server
        endpointServer.stopServer();

        // Purge the job instance logs from the dispatcher
        log(methodName, "Purging local job logs");

        dispatcherUtils.purgeJobInstance(jobInstanceId, HttpURLConnection.HTTP_INTERNAL_ERROR);

        // Check that endpoint files and db entries were not removed because the server was unavailable
        endpointUtils.checkFileSystemEntriesExist(jobInstanceId, jobName, methodName);
        dispatcherUtils.checkDBEntriesExist(jobInstanceId, jobName, methodName);

        // Purge both instances at once
        dispatcherUtils.purgeJobInstances(jobInstanceId, jobInstanceId2);

        // Check that endpoint 2 purge was successful, but db entries were not removed because the server was unavailable
        endpointUtils.checkFileSystemEntriesExist(jobInstanceId2, jobName, methodName);
        endpoint2Utils.checkFileSystemEntriesRemoved(jobInstanceId2, jobName, methodName);
        dispatcherUtils.checkDBEntriesExist(jobInstanceId, jobName, methodName);
        dispatcherUtils.checkDBEntriesExist(jobInstanceId2, jobName, methodName);

        // Run purge with db only flag
        dispatcherUtils.purgeJobInstanceFromDBOnly(jobInstanceId);
        dispatcherUtils.purgeJobInstanceFromDBOnly(jobInstanceId2);

        // Check that the db entries are now removed
        dispatcherUtils.checkDBEntriesRemoved(jobInstanceId, jobName, methodName);
        dispatcherUtils.checkDBEntriesRemoved(jobInstanceId2, jobName, methodName);

        log(methodName, " End ");

    }

    /**
     * Submit a job instance with two executions, one on each JMS endpoint.
     */
    private List<Long> submitJobExecutionOnEachEndpoint() throws Exception {
        String methodName = "submitJobExecutionOnEachEndpoint";
        List<Long> jobIds = new ArrayList<Long>();

        Properties props = new Properties();
        props.put("restartAttempt", "0");

        //Submit job
        log(methodName, "Submitting job #1");
        JsonObject jobInstance = dispatcherUtils.submitJob("SimpleBatchJob", jobName, props);

        //Initialize Instance artifacts(jobInstance, jobInstanceId);
        long jobInstanceId = jobInstance.getJsonNumber("instanceId").longValue();
        jobIds.add(jobInstanceId);

        //Initialize Execution artifacts(jobExecution, jobExecutionId);
        JsonObject jobExecution = endpointUtils.waitForFirstJobExecution(jobInstanceId);
        long jobExecutionId = jobExecution.getJsonNumber("executionId").longValue();
        jobIds.add(jobExecutionId);

        // Since we want to check the entries make it into STEPTHREADEXECUTION and/or are deleted, let's make sure we advance far enough
        // since beginning of job != beginning of first step.
        endpointUtils.waitForStepInJobExecutionToStart(jobExecutionId, firstStepName);

        // Stop the 1st job
        log(methodName, "Stopping job #1");
        endpointUtils.stopJobExecutionAndWaitUntilDone(jobExecutionId, 30);

        // Start the job again with a different restartAttempt value so it goes to the 2nd Executor
        props = new Properties();
        props.put("restartAttempt", "2");

        log(methodName, "Restarting as job #2");
        jobInstance = endpoint2Utils.restartJobExecution(jobInstance, jobExecutionId, props);

        //Initialize Instance artifacts(jobInstance, jobInstanceId);
        jobInstanceId = jobInstance.getJsonNumber("instanceId").longValue();

        //Initialize Execution artifacts(jobExecution, jobExecutionId);
        //jobExecutions = endpoint2Utils.waitForFirstJobExecution(jobInstanceId);
        JsonArray jobExecutions = endpoint2Utils.getJobExecutionsMostRecentFirst(jobInstanceId);
        assertEquals(jobExecutions.size(), 2);
        jobExecution = jobExecutions.getJsonObject(0);
        jobExecutionId = jobExecution.getJsonNumber("executionId").longValue();
        jobIds.add(jobExecutionId);

        // Make sure the job instance id's are the same
        assertEquals(jobInstanceId, (long) jobIds.get(0));

        // Wait for exec to start
        endpoint2Utils.waitForStepInJobExecutionToStart(jobExecutionId, firstStepName);

        // Stop the 2nd job
        log(methodName, "Stopping job #2");
        endpoint2Utils.stopJobExecutionAndWaitUntilDone(jobExecutionId, 30);

        return jobIds;
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

    /**
     * helper for simple logging.
     */
    private static void log(String method, String msg) {
        Log.info(BatchPurgeTest.class, method, msg);
    }

}
