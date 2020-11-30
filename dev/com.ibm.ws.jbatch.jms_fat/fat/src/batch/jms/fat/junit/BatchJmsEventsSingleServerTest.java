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

import java.nio.file.Paths;
import java.util.List;
import java.util.Properties;

import javax.batch.runtime.BatchStatus;
import javax.json.JsonArray;
import javax.json.JsonObject;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.ibm.websphere.simplicity.RemoteFile;
import com.ibm.websphere.simplicity.log.Log;
import com.ibm.ws.jbatch.test.FatUtils;
import com.ibm.ws.jbatch.test.dbservlet.DbServletClient;

import batch.fat.common.util.RepeatTestRule;
import batch.fat.util.BatchJobEventsTestHelper;
import componenttest.annotation.ExpectedFFDC;
import componenttest.custom.junit.runner.FATRunner;
import componenttest.custom.junit.runner.Mode;
import componenttest.custom.junit.runner.Mode.TestMode;
import componenttest.rules.repeater.JakartaEE9Action;
import componenttest.topology.impl.JavaInfo;
import componenttest.topology.impl.LibertyServer;
import componenttest.topology.impl.LibertyServerFactory;
import componenttest.topology.utils.HttpUtils;

/**
 *
 */
@RunWith(FATRunner.class)
public class BatchJmsEventsSingleServerTest {
    private static final LibertyServer server = LibertyServerFactory.getLibertyServer("JobEventsSingleServer");
    public static String topicRoot = "";
    public static String[] eventsConfig = { "",
                                            "EventTopicRoot/server2.xml",
                                            "EventTopicRoot/server1.xml" };

    BatchRestUtils serverUtils = new BatchRestUtils(server);

    /**
     * This rule provides some flexibility in terms of running tests multiple times (for example
     * against different server configurations). In this case, the tests are executed twice - once
     * against the default batch event topic root 'batch\', and again against a modified event topic root.
     */
    @ClassRule
    public static RepeatTestRule repeatTestRule = new RepeatTestRule(new RepeatTestRule.Callback() {

        public int runCount = 0;
        public final String[] testTopicRoot = new String[] { "batch", "Fred", "" };
        //public final String[] testTopicRoot = new String[] { "" };

        /**
         * Run before all the repeated test(ran once at the beginning)
         */
        @Override
        public void beforeAll() throws Exception {

            beforeClass();

        }

        /**
         * Run after all the repeated test(ran once at the end)
         */
        @Override
        public void afterAll() throws Exception {
            afterClass();
        }

        @Override
        public void beforeEach() throws Exception {

            topicRoot = testTopicRoot[runCount];
            log("beforeEach", "Starting Test with BATCH EVENT TOPIC ROOT = " + topicRoot + "/");

            if (runCount > 0) {
                if (server.isStarted() == false) {
                    server.startServer();

                    switchToBatchJmsEventTopicConfig(eventsConfig[runCount]);

                    server.setMarkToEndOfLog();
                }
            }
        }

        @Override
        public boolean doRepeat() {
            return (++runCount < testTopicRoot.length); // Quit after running all variations (no url and v1).
        }

    });

//    private static final String[] BatchJobEventsTestHelper.msgToWaitDispatcher = new String[] {
//                                                                      "JMSDestination=topic://batch/jobs/instance/submitted",
//                                                                      "JMSDestination=topic://batch/jobs/instance/jms_queued",
//                                                                      "JMSDestination=topic://batch/jobs/instance/jms_consumed" };
//    private static final String[] BatchJobEventsTestHelper.msgToWaitExecutorJobCompleted = new String[] {
//                                                                                "JMSDestination=topic://batch/jobs/execution/starting",
//                                                                                "JMSDestination=topic://batch/jobs/instance/dispatched",
//                                                                                "JMSDestination=topic://batch/jobs/execution/started",
//                                                                                "JMSDestination=topic://batch/jobs/execution/completed",
//                                                                                "JMSDestination=topic://batch/jobs/instance/completed" };
//
//    private static final String[] BatchJobEventsTestHelper.msgToWaitExecutorJobFailed = new String[] {
//                                                                             "JMSDestination=topic://batch/jobs/execution/starting",
//                                                                             "JMSDestination=topic://batch/jobs/instance/dispatched",
//                                                                             "JMSDestination=topic://batch/jobs/execution/started",
//                                                                             "JMSDestination=topic://batch/jobs/execution/failed",
//                                                                             "JMSDestination=topic://batch/jobs/instance/failed" };
//
//    private static final String[] BatchJobEventsTestHelper.msgToWaitExecutorJobRestarted = new String[] {
//                                                                                "JMSDestination=topic://batch/jobs/execution/starting",
//                                                                                "JMSDestination=topic://batch/jobs/instance/dispatched",
//                                                                                "JMSDestination=topic://batch/jobs/execution/restarting",
//                                                                                "JMSDestination=topic://batch/jobs/execution/completed",
//                                                                                "JMSDestination=topic://batch/jobs/instance/completed" };
//
//    private static final String[] BatchJobEventsTestHelper.msgToWaitJobPurged = new String[] {
//                                                                     "JMSDestination=topic://batch/jobs/instance/purged" };
//
//    private static final String[] BatchJobEventsTestHelper.msgToWaitExecutorJobStopped = new String[] {
//                                                                              "JMSDestination=topic://batch/jobs/execution/starting",
//                                                                              "JMSDestination=topic://batch/jobs/instance/dispatched",
//                                                                              "JMSDestination=topic://batch/jobs/execution/started",
//                                                                              "JMSDestination=topic://batch/jobs/instance/stopping",
//                                                                              "JMSDestination=topic://batch/jobs/execution/stopping",
//                                                                              "JMSDestination=topic://batch/jobs/execution/stopped",
//                                                                              "JMSDestination=topic://batch/jobs/instance/stopped" };
//
//    private static final String[] BatchJobEventsTestHelper.msgToWaitExecutorStepCompleted = new String[] {
//                                                                                 "JMSDestination=topic://batch/jobs/execution/starting",
//                                                                                 "JMSDestination=topic://batch/jobs/instance/dispatched",
//                                                                                 "JMSDestination=topic://batch/jobs/execution/started",
//                                                                                 "JMSDestination=topic://batch/jobs/execution/step/started",
//                                                                                 "JMSDestination=topic://batch/jobs/execution/step/completed",
//                                                                                 "JMSDestination=topic://batch/jobs/execution/completed",
//                                                                                 "JMSDestination=topic://batch/jobs/instance/completed" };
//
//    //3 partitions job
//    private static final String[] BatchJobEventsTestHelper.msgToWaitOnExecutorBegan = new String[] {
//                                                                           "JMSDestination=topic://batch/jobs/execution/starting",
//                                                                           "JMSDestination=topic://batch/jobs/instance/dispatched",
//                                                                           "JMSDestination=topic://batch/jobs/execution/started",
//                                                                           "JMSDestination=topic://batch/jobs/execution/step/started" };
//
//    private static final String[] BatchJobEventsTestHelper.msgToWaitOnExecutorEnded = new String[] {
//                                                                           "JMSDestination=topic://batch/jobs/execution/step/completed",
//                                                                           "JMSDestination=topic://batch/jobs/execution/completed",
//                                                                           "JMSDestination=topic://batch/jobs/instance/completed"
//    };
//
//    private static final String BatchJobEventsTestHelper.msgToWaitPartitionStarted = new String("JMSDestination=topic://batch/jobs/execution/partition/started");
//
//    private static final String BatchJobEventsTestHelper.msgToWaitPartitionEnded = new String("JMSDestination=topic://batch/jobs/execution/partition/completed");
//
//    /**
//     * there are multiple step events and checkpoint events, we just check for one
//     */
//    private static final String[] BatchJobEventsTestHelper.msgToWaitExecutorCheckpoint = new String[] {
//                                                                              "JMSDestination=topic://batch/jobs/execution/starting",
//                                                                              "JMSDestination=topic://batch/jobs/instance/dispatched",
//                                                                              "JMSDestination=topic://batch/jobs/execution/started",
//                                                                              "JMSDestination=topic://batch/jobs/execution/step/started",
//                                                                              "JMSDestination=topic://batch/jobs/execution/step/checkpoint",
//                                                                              "JMSDestination=topic://batch/jobs/execution/step/completed",
//                                                                              "JMSDestination=topic://batch/jobs/execution/completed",
//                                                                              "JMSDestination=topic://batch/jobs/instance/completed" };
//
//    /**
//     * test_simpleSplitFlow.xml has 1 split, which has 2 flows, and 2 steps in each flow.
//     * the steps can finish in any order, so we will test by searching for # of occurrence
//     */
//
//    private static final String BatchJobEventsTestHelper.msgToWaitStepStarted = "JMSDestination=topic://batch/jobs/execution/step/started";
//    private static final String BatchJobEventsTestHelper.msgToWaitStepCompleted = "JMSDestination=topic://batch/jobs/execution/step/completed";
//    private static final String BatchJobEventsTestHelper.msgToWaitSplitFlowStarted = "JMSDestination=topic://batch/jobs/execution/split-flow/started";
//    private static final String BatchJobEventsTestHelper.msgToWaitSplitFlowEnded = "JMSDestination=topic://batch/jobs/execution/split-flow/ended";

    /**
     * Shouldn't have to wait more than 10s for messages to appear.
     */
    private static final long LogScrapingTimeout = 10 * 1000;

    /**
     * Startup the server
     */
    @BeforeClass
    public static void beforeClass() throws Exception {

        HttpUtils.trustAllCertificates();
        
        if (JakartaEE9Action.isActive()) {
            JakartaEE9Action.transformApp(Paths.get(server.getServerRoot(), "dropins", "BonusPayout.war"));
            JakartaEE9Action.transformApp(Paths.get(server.getServerRoot(), "dropins", "DbServletApp.war"));
            JakartaEE9Action.transformApp(Paths.get(server.getServerRoot(), "dropins", "jmsmdb.ear"));
            JakartaEE9Action.transformApp(Paths.get(server.getServerRoot(), "dropins", "jmsweb.war"));
            JakartaEE9Action.transformApp(Paths.get(server.getServerRoot(), "dropins", "JobLogEventsLogCreatorMDB.war"));
            JakartaEE9Action.transformApp(Paths.get(server.getServerRoot(), "dropins", "JobLogEventsMDB.war"));
            JakartaEE9Action.transformApp(Paths.get(server.getServerRoot(), "dropins", "SimpleBatchJob.war"));
        }


        if (server.isStarted() == false) {
            server.startServer();
            FatUtils.waitForStartupAndSsl(server);
        }

        // Setup BonusPayout app tables
        new DbServletClient().setDataSourceJndi("jdbc/BonusPayoutDS").setDataSourceUser("user", "pass").setHostAndPort(server.getHostname(),
                                                                                                                       server.getHttpDefaultPort()).loadSql(server.pathToAutoFVTTestFiles
                                                                                                                                                            + "common/BonusPayout.derby.ddl",
                                                                                                                                                            "JBATCH",
                                                                                                                                                            "").executeUpdate();
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
     * Submit a job that will end successfully
     *
     * The MDB app subscribes to all topics publish under under the root batch (topic string batch//.)
     * print out of the jms message when it receives.
     *
     * This test will search for the string in the message that shows the jms destination.
     *
     * @throws Exception
     */
    @Test
    public void testReceivingEventsFromSubmit_completed() throws Exception {

        server.setMarkToEndOfLog(new RemoteFile[] { server.getMatchingLogFile("trace.log"), server.getDefaultLogFile() });

        //String BatchJobEventsTestHelper.msgToWaitFor_1 = "JMSDestination=topic://batch/jobs/instance/created\\?topicSpace=batchLibertyTopicSpace";
        //String BatchJobEventsTestHelper.msgToWaitFor_2 = "JMSDestination=topic://batch/jobs/instance/queued\\?topicSpace=batchLibertyTopicSpace";

        JsonObject jobInstance = serverUtils.submitJob("SimpleBatchJob", "test_batchlet_stepCtx");

        for (int i = 0; i < BatchJobEventsTestHelper.msgToWaitDispatcher.length; i++) {
            String topicString = resolveTopicRoot(topicRoot, BatchJobEventsTestHelper.msgToWaitDispatcher[i]);

            String uploadMessage = server.waitForStringInLog(topicString,
                                                             LogScrapingTimeout,
                                                             server.getMatchingLogFile("trace.log"));

            assertNotNull("Could not find message: " + topicString, uploadMessage);
        }

        jobInstance = serverUtils.waitForJobInstanceToFinish(jobInstance.getJsonNumber("instanceId").longValue());

        assertTrue(BatchRestUtils.isDone(jobInstance));
        assertEquals("test_batchlet_stepCtx", BatchRestUtils.getJobName(jobInstance));
        assertEquals("SimpleBatchJob#SimpleBatchJob.war", BatchRestUtils.getAppName(jobInstance));

        JsonArray linkArray = jobInstance.getJsonArray("_links");
        assertNotNull(linkArray);

        for (int i = 0; i < BatchJobEventsTestHelper.msgToWaitExecutorJobCompleted.length; i++) {
            String topicString = resolveTopicRoot(topicRoot, BatchJobEventsTestHelper.msgToWaitExecutorJobCompleted[i]);

            String uploadMessage = server.waitForStringInLog(topicString,
                                                             LogScrapingTimeout,
                                                             server.getMatchingLogFile("trace.log"));

            assertNotNull("Could not find message: " + topicString, uploadMessage);
        }

    }

    /**
     * Submit a job that will end in failed state.
     *
     * The MDB app subscribes to all topics publish under under the root batch (topic string batch//.)
     * print out of the jms message when it receives.
     *
     * This test will search for the string in the message that shows the jms destination.
     *
     * @throws Exception
     */
    @Test
    @ExpectedFFDC({ "java.lang.Exception", "com.ibm.jbatch.container.exception.BatchContainerRuntimeException" })
    public void testReceivingEventFromSubmit_failed() throws Exception {

        server.setMarkToEndOfLog(new RemoteFile[] { server.getMatchingLogFile("trace.log"), server.getDefaultLogFile() });

        //set this prop so job is running longer, so we can catch it to stop.
        Properties jobProps = new Properties();
        jobProps.setProperty("force.failure", "true");

        // submit and wait for job to job to FAILED because the force.failure == true
        JsonObject jobInstance = serverUtils.submitJob("SimpleBatchJob", "test_batchlet_stepCtx", jobProps);

        for (int i = 0; i < BatchJobEventsTestHelper.msgToWaitDispatcher.length; i++) {
            String topicString = resolveTopicRoot(topicRoot, BatchJobEventsTestHelper.msgToWaitDispatcher[i]);

            String uploadMessage = server.waitForStringInLog(topicString, LogScrapingTimeout, server.getMatchingLogFile("trace.log"));
            assertNotNull("Could not find message: " + topicString, uploadMessage);
        }

        long instanceId = BatchRestUtils.instanceId(jobInstance);
        jobInstance = serverUtils.waitForJobInstanceToFinish(instanceId);

        //the check for job in final state already done by waitForJobInstanceToFinish
        //so if we get here, job ran
        BatchStatus status = BatchStatus.valueOf(jobInstance.getString("batchStatus"));
        assertEquals(BatchStatus.FAILED, status);

        jobInstance = serverUtils.waitForJobInstanceToFinish(jobInstance.getJsonNumber("instanceId").longValue());

        assertTrue(BatchRestUtils.isDone(jobInstance));
        assertEquals("test_batchlet_stepCtx", BatchRestUtils.getJobName(jobInstance));
        assertEquals("SimpleBatchJob#SimpleBatchJob.war", BatchRestUtils.getAppName(jobInstance));

        JsonArray linkArray = jobInstance.getJsonArray("_links");
        assertNotNull(linkArray);

        for (int i = 0; i < BatchJobEventsTestHelper.msgToWaitExecutorJobFailed.length; i++) {
            String topicString = resolveTopicRoot(topicRoot, BatchJobEventsTestHelper.msgToWaitExecutorJobFailed[i]);

            String uploadMessage = server.waitForStringInLog(topicString, LogScrapingTimeout, server.getMatchingLogFile("trace.log"));
            assertNotNull("Could not find message: " + topicString, uploadMessage);
        }
    }

    /**
     * Submit a job that will end in failed state, then restart it.
     *
     * The MDB app subscribes to all topics publish under under the root batch (topic string batch//.)
     * print out of the jms message when it receives.
     *
     * This test will search for the string in the message that shows the jms destination.
     *
     * @throws Exception
     */
    @Test
    @ExpectedFFDC({ "java.lang.Exception", "com.ibm.jbatch.container.exception.BatchContainerRuntimeException" })
    public void testReceivingEventFromSubmit_restarted() throws Exception {

        server.setMarkToEndOfLog(new RemoteFile[] { server.getMatchingLogFile("trace.log"), server.getDefaultLogFile() });

        //set this prop so job is running longer, so we can catch it to stop.
        Properties jobProps = new Properties();
        jobProps.setProperty("force.failure", "true");

        // submit and wait for job to job to FAILED because the force.failure == true
        JsonObject jobInstance = serverUtils.submitJob("SimpleBatchJob", "test_batchlet_stepCtx", jobProps);

        for (int i = 0; i < BatchJobEventsTestHelper.msgToWaitDispatcher.length; i++) {
            String topicString = resolveTopicRoot(topicRoot, BatchJobEventsTestHelper.msgToWaitDispatcher[i]);

            String uploadMessage = server.waitForStringInLog(topicString, LogScrapingTimeout, server.getMatchingLogFile("trace.log"));
            assertNotNull("Could not find message: " + topicString, uploadMessage);
        }

        long instanceId = BatchRestUtils.instanceId(jobInstance);
        jobInstance = serverUtils.waitForJobInstanceToFinish(instanceId);

        //the check for job in final state already done by waitForJobInstanceToFinish
        //so if we get here, job ran
        BatchStatus status = BatchStatus.valueOf(jobInstance.getString("batchStatus"));
        assertEquals(BatchStatus.FAILED, status);

        assertTrue(BatchRestUtils.isDone(jobInstance));
        assertEquals("test_batchlet_stepCtx", BatchRestUtils.getJobName(jobInstance));
        assertEquals("SimpleBatchJob#SimpleBatchJob.war", BatchRestUtils.getAppName(jobInstance));

        JsonArray linkArray = jobInstance.getJsonArray("_links");
        assertNotNull(linkArray);

        for (int i = 0; i < BatchJobEventsTestHelper.msgToWaitExecutorJobFailed.length; i++) {
            String topicString = resolveTopicRoot(topicRoot, BatchJobEventsTestHelper.msgToWaitExecutorJobFailed[i]);

            String uploadMessage = server.waitForStringInLog(topicString, LogScrapingTimeout, server.getMatchingLogFile("trace.log"));
            assertNotNull("Could not find message: " + topicString, uploadMessage);
        }

        //restart, this time with force.failure == false
        jobProps.setProperty("force.failure", "false");

        JsonObject jobInstanceRestart = serverUtils.restartJobInstance(instanceId, jobProps);
        assertEquals(instanceId, BatchRestUtils.instanceId(jobInstanceRestart));

        for (int i = 0; i < BatchJobEventsTestHelper.msgToWaitDispatcher.length; i++) {
            String topicString = resolveTopicRoot(topicRoot, BatchJobEventsTestHelper.msgToWaitDispatcher[i]);

            String uploadMessage = server.waitForStringInLog(topicString, LogScrapingTimeout, server.getMatchingLogFile("trace.log"));
            assertNotNull("Could not find message: " + topicString, uploadMessage);
        }

        Thread.sleep(2 * 1000);

        JsonObject jobInstanceFinal = serverUtils.waitForJobInstanceToFinish(instanceId);
        assertEquals(BatchStatus.COMPLETED, BatchStatus.valueOf(jobInstanceFinal.getString("batchStatus")));

        for (int i = 0; i < BatchJobEventsTestHelper.msgToWaitExecutorJobRestarted.length; i++) {
            String topicString = resolveTopicRoot(topicRoot, BatchJobEventsTestHelper.msgToWaitExecutorJobRestarted[i]);

            String uploadMessage = server.waitForStringInLog(topicString, LogScrapingTimeout, server.getMatchingLogFile("trace.log"));
            assertNotNull("Could not find message: " + topicString, uploadMessage);
        }
    }

    /**
     * Expected to receive message from topic batch/jobs/instance/purged
     *
     * @throws Exception
     */
    @Test
    public void testReceivingEventFromPurge() throws Exception {

        server.setMarkToEndOfLog(new RemoteFile[] { server.getMatchingLogFile("trace.log"), server.getDefaultLogFile() });

        JsonObject jobInstance = serverUtils.submitJob("SimpleBatchJob", "test_batchlet_stepCtx");
        long jobInstanceId = jobInstance.getJsonNumber("instanceId").longValue();

        for (int i = 0; i < BatchJobEventsTestHelper.msgToWaitDispatcher.length; i++) {
            String topicString = resolveTopicRoot(topicRoot, BatchJobEventsTestHelper.msgToWaitDispatcher[i]);

            String uploadMessage = server.waitForStringInLog(topicString, LogScrapingTimeout, server.getMatchingLogFile("trace.log"));
            assertNotNull("Could not find message: " + topicString, uploadMessage);
        }

        //wait for job to complete
        jobInstance = serverUtils.waitForJobInstanceToFinish(jobInstanceId);

        assertTrue(BatchRestUtils.isDone(jobInstance));

        for (int i = 0; i < BatchJobEventsTestHelper.msgToWaitExecutorJobCompleted.length; i++) {
            String topicString = resolveTopicRoot(topicRoot, BatchJobEventsTestHelper.msgToWaitExecutorJobCompleted[i]);

            String uploadMessage = server.waitForStringInLog(topicString, LogScrapingTimeout, server.getMatchingLogFile("trace.log"));
            assertNotNull("Could not find message: " + topicString, uploadMessage);
        }

        //purge
        serverUtils.purgeJobInstance(jobInstanceId);

        for (int i = 0; i < BatchJobEventsTestHelper.msgToWaitJobPurged.length; i++) {
            String topicString = resolveTopicRoot(topicRoot, BatchJobEventsTestHelper.msgToWaitJobPurged[i]);

            String uploadMessage = server.waitForStringInLog(topicString, LogScrapingTimeout, server.getMatchingLogFile("trace.log"));
            assertNotNull("Could not find message: " + topicString, uploadMessage);
        }
    }

    /**
     * Expected to receive message from topic batch/jobs/instance/purged
     *
     * @throws Exception
     */
    @Test
    public void testReceivingEventFromPurgeJobFromDBOnly() throws Exception {

        server.setMarkToEndOfLog(new RemoteFile[] { server.getMatchingLogFile("trace.log"), server.getDefaultLogFile() });

        JsonObject jobInstance = serverUtils.submitJob("SimpleBatchJob", "test_batchlet_stepCtx");
        long jobInstanceId = jobInstance.getJsonNumber("instanceId").longValue();

        for (int i = 0; i < BatchJobEventsTestHelper.msgToWaitDispatcher.length; i++) {
            String topicString = resolveTopicRoot(topicRoot, BatchJobEventsTestHelper.msgToWaitDispatcher[i]);

            String uploadMessage = server.waitForStringInLog(topicString, LogScrapingTimeout, server.getMatchingLogFile("trace.log"));
            assertNotNull("Could not find message: " + topicString, uploadMessage);
        }

        //wait for job to complete
        jobInstance = serverUtils.waitForJobInstanceToFinish(jobInstanceId);

        assertTrue(BatchRestUtils.isDone(jobInstance));

        for (int i = 0; i < BatchJobEventsTestHelper.msgToWaitExecutorJobCompleted.length; i++) {
            String topicString = resolveTopicRoot(topicRoot, BatchJobEventsTestHelper.msgToWaitExecutorJobCompleted[i]);

            String uploadMessage = server.waitForStringInLog(topicString, LogScrapingTimeout, server.getMatchingLogFile("trace.log"));
            assertNotNull("Could not find message: " + topicString, uploadMessage);
        }

        //purge
        serverUtils.purgeJobInstanceFromDBOnly(jobInstanceId);

        //wait for message from purge topic
        for (int i = 0; i < BatchJobEventsTestHelper.msgToWaitJobPurged.length; i++) {
            String topicString = resolveTopicRoot(topicRoot, BatchJobEventsTestHelper.msgToWaitJobPurged[i]);

            String uploadMessage = server.waitForStringInLog(topicString, LogScrapingTimeout, server.getMatchingLogFile("trace.log"));
            assertNotNull("Could not find message: " + topicString, uploadMessage);
        }
    }

    /**
     * Check for events from stop a job
     *
     * @throws Exception
     */
    @Test
    public void testReceivingEventsFromJobStopped() throws Exception {
        String method = "testReceivingEventsFromJobStopped";

        server.setMarkToEndOfLog(new RemoteFile[] { server.getMatchingLogFile("trace.log"), server.getDefaultLogFile() });

        //set this prop so job is running longer, so we can catch it to stop.
        Properties jobProps = new Properties();
        jobProps.setProperty("sleep.time.seconds", "20");

        // submit and wait for job to start
        JsonObject jobInstance = serverUtils.submitJob("SimpleBatchJob", "test_SleepyMultiStepJob", jobProps);
        long instanceId = BatchRestUtils.instanceId(jobInstance);
        JsonObject executionInstance = serverUtils.waitForJobInstanceToStart(BatchRestUtils.instanceId(jobInstance));

        // stop job
        if (serverUtils.stopJob(BatchRestUtils.execId(executionInstance))) {

            jobInstance = serverUtils.waitForJobInstanceToFinish(instanceId);

            //the check for job in final state already done by waitForJobInstanceToFinish
            //so if we get here, job was stopped, just verify status

            BatchStatus status = BatchStatus.valueOf(jobInstance.getString("batchStatus"));
            assertTrue(BatchStatus.STOPPED.equals(status));

            for (int i = 0; i < BatchJobEventsTestHelper.msgToWaitDispatcher.length; i++) {
                String topicString = resolveTopicRoot(topicRoot, BatchJobEventsTestHelper.msgToWaitDispatcher[i]);

                String uploadMessage = server.waitForStringInLog(topicString, LogScrapingTimeout, server.getMatchingLogFile("trace.log"));
                assertNotNull("Could not find message: " + topicString, uploadMessage);
            }

            for (int i = 0; i < BatchJobEventsTestHelper.msgToWaitExecutorJobStopped.length; i++) {
                String topicString = resolveTopicRoot(topicRoot, BatchJobEventsTestHelper.msgToWaitExecutorJobStopped[i]);

                String uploadMessage = server.waitForStringInLog(topicString, LogScrapingTimeout,
                                                                 server.getMatchingLogFile("trace.log"));
                assertNotNull("Could not find message: " + topicString, uploadMessage);
            }

        } else {
            //job was completed before stop was issued
            log(method, "job instance " + instanceId + " was completed before stop request was issued. In this case, this test is considered passing if job status is COMPLETED");
            executionInstance = serverUtils.getOnlyJobExecution(instanceId);
            BatchStatus status = BatchStatus.valueOf(executionInstance.getString("batchStatus"));
            assertTrue(BatchStatus.COMPLETED.equals(status));
        }

    }

    /**
     * Check step events
     *
     * @throws Exception
     */
    @Test
    public void testReceivingEventsFromStep() throws Exception {

        // submit and wait for job to start
        JsonObject jobInstance = serverUtils.submitJob("SimpleBatchJob", "test_SleepyMultiStepJob");
        long instanceId = BatchRestUtils.instanceId(jobInstance);
        serverUtils.waitForJobInstanceToStart(BatchRestUtils.instanceId(jobInstance));

        for (int i = 0; i < BatchJobEventsTestHelper.msgToWaitDispatcher.length; i++) {
            String topicString = resolveTopicRoot(topicRoot, BatchJobEventsTestHelper.msgToWaitDispatcher[i]);

            String uploadMessage = server.waitForStringInLog(topicString, LogScrapingTimeout, server.getMatchingLogFile("trace.log"));
            assertNotNull("Could not find message: " + topicString, uploadMessage);
        }

        //wait for job to complete
        jobInstance = serverUtils.waitForJobInstanceToFinish(instanceId);

        assertTrue(BatchRestUtils.isDone(jobInstance));

        for (int i = 0; i < BatchJobEventsTestHelper.msgToWaitExecutorStepCompleted.length; i++) {
            String topicString = resolveTopicRoot(topicRoot, BatchJobEventsTestHelper.msgToWaitExecutorStepCompleted[i]);

            String uploadMessage = server.waitForStringInLog(topicString, LogScrapingTimeout,
                                                             server.getMatchingLogFile("trace.log"));
            assertNotNull("Could not find message: " + topicString, uploadMessage);
        }
    }

    /**
     * Check partition events
     *
     * @throws Exception
     */
    @Test
    @Mode(TestMode.QUARANTINE)
    // defect 218359 - fix test logic
    public void testReceiveEventsPartition() throws Exception {

        server.setMarkToEndOfLog(new RemoteFile[] { server.getMatchingLogFile("trace.log"), server.getDefaultLogFile() });

        // submit and wait for job to start
        JsonObject jobInstance = serverUtils.submitJob("SimpleBatchJob", "test_simplePartition");

        for (int i = 0; i < BatchJobEventsTestHelper.msgToWaitDispatcher.length; i++) {
            String topicString = resolveTopicRoot(topicRoot, BatchJobEventsTestHelper.msgToWaitDispatcher[i]);

            String uploadMessage = server.waitForStringInLog(topicString, LogScrapingTimeout, server.getMatchingLogFile("trace.log"));
            assertNotNull("Could not find message: " + topicString, uploadMessage);
        }

        long instanceId = BatchRestUtils.instanceId(jobInstance);
        //wait for job to complete
        jobInstance = serverUtils.waitForJobInstanceToFinish(instanceId);

        assertTrue(BatchRestUtils.isDone(jobInstance));

        for (int i = 0; i < BatchJobEventsTestHelper.msgToWaitOnExecutorBegan.length; i++) {
            String topicString = resolveTopicRoot(topicRoot, BatchJobEventsTestHelper.msgToWaitOnExecutorBegan[i]);

            String uploadMessage = server.waitForStringInLog(topicString, LogScrapingTimeout, server.getMatchingLogFile("trace.log"));
            assertNotNull("Could not find message: " + topicString, uploadMessage);
        }

        //the next few search might not be in the order specified.
        //the test should pass as long as we can find the string from this offset.

        //expecting 3 partition started messages,
        //the api searches message.log & trace.log --> resulting in 3x2 occurrences
        List<String> results = server.findStringsInLogsAndTraceUsingMark(BatchJobEventsTestHelper.msgToWaitPartitionStarted);
        log("testReceiveEventsPartition", "result of searching for:" + BatchJobEventsTestHelper.msgToWaitPartitionStarted + " =" + results.toString());
        assertEquals(6, results.size());

        //expecting 3 partition ended message
        assertEquals(6, server.findStringsInLogsAndTraceUsingMark(BatchJobEventsTestHelper.msgToWaitPartitionEnded).size());

        for (int i = 0; i < BatchJobEventsTestHelper.msgToWaitOnExecutorEnded.length; i++) {
            String topicString = resolveTopicRoot(topicRoot, BatchJobEventsTestHelper.msgToWaitOnExecutorEnded[i]);

            String uploadMessage = server.waitForStringInLog(topicString, LogScrapingTimeout, server.getMatchingLogFile("trace.log"));
            assertNotNull("Could not find message: " + topicString, uploadMessage);
        }

    }

    /**
     * Check event from checkpoint
     *
     * @throws Exception
     */
    @Test
    public void testReceiveEventsCheckpoint() throws Exception {

        Properties jobParameters = new Properties();
        jobParameters.setProperty("dsJNDI", "jdbc/BonusPayoutDS");
        JsonObject jobInstanceBonusPayout = serverUtils.submitJob("BonusPayout", "BonusPayoutJob", jobParameters);

        for (int i = 0; i < BatchJobEventsTestHelper.msgToWaitDispatcher.length; i++) {
            String topicString = resolveTopicRoot(topicRoot, BatchJobEventsTestHelper.msgToWaitDispatcher[i]);

            String uploadMessage = server.waitForStringInLog(topicString, LogScrapingTimeout, server.getMatchingLogFile("trace.log"));
            assertNotNull("Could not find message: " + topicString, uploadMessage);
        }

        JsonObject jobInstanceBonusPayout2 = serverUtils.waitForJobInstanceToFinish(jobInstanceBonusPayout.getJsonNumber("instanceId").longValue());
        assertEquals(BatchStatus.COMPLETED, BatchStatus.valueOf(jobInstanceBonusPayout2.getString("batchStatus")));

        for (int i = 0; i < BatchJobEventsTestHelper.msgToWaitExecutorCheckpoint.length; i++) {
            String topicString = resolveTopicRoot(topicRoot, BatchJobEventsTestHelper.msgToWaitExecutorCheckpoint[i]);

            String uploadMessage = server.waitForStringInLog(topicString, LogScrapingTimeout, server.getMatchingLogFile("trace.log"));
            assertNotNull("Could not find message: " + topicString, uploadMessage);
        }
    }

    @Test
    @Mode(TestMode.QUARANTINE)
    // defect 227550/218359 - fix test logic
    public void testReceivingEventsSplitFlow() throws Exception {

        server.setMarkToEndOfLog(new RemoteFile[] { server.getMatchingLogFile("trace.log"), server.getDefaultLogFile() });

        JsonObject jobInstance = serverUtils.submitJob("SimpleBatchJob", "test_simpleSplitFlow");

        for (int i = 0; i < BatchJobEventsTestHelper.msgToWaitDispatcher.length; i++) {
            String topicString = resolveTopicRoot(topicRoot, BatchJobEventsTestHelper.msgToWaitDispatcher[i]);

            String uploadMessage = server.waitForStringInLog(topicString, LogScrapingTimeout, server.getMatchingLogFile("trace.log"));
            assertNotNull("Could not find message: " + topicString, uploadMessage);
        }

        jobInstance = serverUtils.waitForJobInstanceToFinish(jobInstance.getJsonNumber("instanceId").longValue());
        assertEquals(BatchStatus.COMPLETED, BatchStatus.valueOf(jobInstance.getString("batchStatus")));

        //job finished.  All events should be written to log.

        for (int i = 0; i < BatchJobEventsTestHelper.msgToWaitOnExecutorBegan.length; i++) {
            String topicString = resolveTopicRoot(topicRoot, BatchJobEventsTestHelper.msgToWaitOnExecutorBegan[i]);

            String uploadMessage = server.waitForStringInLog(topicString, LogScrapingTimeout, server.getMatchingLogFile("trace.log"));
            assertNotNull("Could not find message: " + topicString, uploadMessage);
        }

        //expecting 2 split-flow started messages,
        //the api searches message.log & trace.log --> resulting in 2x2 occurrences
        List<String> results = server.findStringsInLogsAndTraceUsingMark(BatchJobEventsTestHelper.msgToWaitSplitFlowStarted);
        log("testReceivingEventsSplitFlow", "result of searching for:" + BatchJobEventsTestHelper.msgToWaitSplitFlowStarted + " =" + results.toString());
        assertEquals(4, results.size());

        //expecting 2 split-flow ended message
        results = server.findStringsInLogsAndTraceUsingMark(BatchJobEventsTestHelper.msgToWaitSplitFlowEnded);
        log("testReceivingEventsSplitFlow", "result of searching for:" + BatchJobEventsTestHelper.msgToWaitSplitFlowEnded + " =" + results.toString());
        assertEquals(4, results.size());

        //expecting 4 step started --> 4x2 entries
        assertEquals(8, server.findStringsInLogsAndTraceUsingMark(BatchJobEventsTestHelper.msgToWaitStepStarted).size());

        //expecting 4 step ended
        assertEquals(8, server.findStringsInLogsAndTraceUsingMark(BatchJobEventsTestHelper.msgToWaitStepCompleted).size());

        //start at index 1 because step completed already check
        for (int i = 1; i < BatchJobEventsTestHelper.msgToWaitOnExecutorEnded.length; i++) {
            String topicString = resolveTopicRoot(topicRoot, BatchJobEventsTestHelper.msgToWaitOnExecutorEnded[i]);

            String uploadMessage = server.waitForStringInLog(topicString, LogScrapingTimeout, server.getMatchingLogFile("trace.log"));
            assertNotNull("Could not find message: " + topicString, uploadMessage);
        }

    }

    private static String resolveTopicRoot(String topicRoot, String defaultTopicRoot) {
        String x;

        if (topicRoot != null) {
            x = defaultTopicRoot.replaceFirst("batch" + ((topicRoot.isEmpty()) ? "/" : ""), topicRoot);
            return x;
        }
        return defaultTopicRoot;
    }

    private static void switchToBatchJmsEventTopicConfig(String config) throws Exception {
        setConfig(config, BatchJmsEventsSingleServerTest.class);
    }

    private static void setConfig(String config, Class testClass) throws Exception {
        if (!config.isEmpty()) {
            Log.info(testClass, "setConfig", "Setting server.xml to: " + config);
            server.setServerConfigurationFile(config);
            server.waitForConfigUpdateInLogUsingMark(null);
        } else {
            Log.info(testClass, "setConfig", "Using default server.xml");
        }
    }

    /**
     * helper for simple logging.
     */
    private static void log(String method, String msg) {
        Log.info(BatchJmsEventsSingleServerTest.class, method, msg);
    }
}
