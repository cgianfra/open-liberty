/*
 * IBM Confidential
 *
 * OCO Source Materials
 *
 * WLP Copyright IBM Corp. 2014,2019
 *
 * The source code for this program is not published or otherwise divested
 * of its trade secrets, irrespective of what has been deposited with the
 * U.S. Copyright Office.
 */
package batch.jms.fat.junit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Paths;
import java.util.Properties;

import javax.batch.runtime.BatchStatus;
import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.ibm.websphere.simplicity.log.Log;
import com.ibm.ws.jbatch.test.BatchJmsFatUtils;
import com.ibm.ws.jbatch.test.FatUtils;

import batch.fat.util.BatchFatUtils;
import batch.fat.util.InstanceStateMirrorImage;
import componenttest.annotation.AllowedFFDC;
import componenttest.annotation.ExpectedFFDC;
import componenttest.custom.junit.runner.FATRunner;
import componenttest.rules.repeater.JakartaEE9Action;
import componenttest.topology.impl.LibertyServer;
import componenttest.topology.impl.LibertyServerFactory;
import componenttest.topology.utils.HttpUtils;
import componenttest.topology.utils.HttpUtils.HTTPRequestMethod;

/**
 * This test has 1 endpoint server, 1 dispatcher server, 1 message engine server
 *
 * Sender and Message on one server, receiver on another server
 */
@RunWith(FATRunner.class)
public class BatchJmsMultiServersTest {

    private static final LibertyServer messageEngineServer = LibertyServerFactory.getLibertyServer("BatchJmsMessageEngine");

    //batch jms endpoint server
    private static final LibertyServer endpointServer = LibertyServerFactory.getLibertyServer("BatchJmsEndpoint");

    //batch jms dispatcher server
    private static final LibertyServer dispatcherServer = LibertyServerFactory.getLibertyServer("BatchJmsDispatcher");

    private static final String dispatcherHost = dispatcherServer.getHostname();
    private static int dispatcherPort = -1;

    /**
     * Shouldn't have to wait more than 10s for messages to appear.
     */
    private static final long LogScrapingTimeout = 10 * 1000;

    /**
     * Startup the servers
     */
    @BeforeClass
    public static void beforeClass() throws Exception {

        HttpUtils.trustAllCertificates();

        String serverStartedMsg = "CWWKF0011I:.*";

        setports();

        dispatcherPort = dispatcherServer.getHttpDefaultPort();

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
        }

        //start server with Message Engine first
        messageEngineServer.startServer();
        FatUtils.waitForSmarterPlanet(messageEngineServer);
        System.out.println("CGCG MultiServersTest messageEngineServer port = " + messageEngineServer.getHttpDefaultPort());

        //start dispatcher
        dispatcherServer.startServer();
        FatUtils.waitForStartupAndSsl(dispatcherServer);
        System.out.println("CGCG MultiServersTest dispatcherServer port = " + dispatcherServer.getHttpDefaultPort());

        assertEquals(Integer.getInteger("batch.dispatcher_1_HTTP_default").intValue(), dispatcherServer.getHttpDefaultPort());

        //start endpoint
        endpointServer.startServer();

        FatUtils.waitForStartupAndSsl(endpointServer);
        FatUtils.waitForLTPA(endpointServer); //because endpoint has app security enabled

        System.out.println("CGCG MultiServersTest endpointServer port = " + endpointServer.getHttpDefaultPort());

        assertEquals(Integer.getInteger("batch.endpoint_1_HTTP_default").intValue(), endpointServer.getHttpDefaultPort());
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
            endpointServer.stopServer("CWWKG0033W", "CWWKY0201W", "CWWKY0011W", "CWWKY0209E", "CWWKG0032W", "CWWKY0041W");
        }

        if (messageEngineServer != null && messageEngineServer.isStarted()) {
            messageEngineServer.stopServer(LibertyServer.DISABLE_FAILURE_CHECKING);
        }
    }

    /**
     * helper for simple logging.
     */
    private static void log(String method, Object msg) {
        Log.info(BatchJmsMultiServersTest.class, method, String.valueOf(msg));
    }

    @AllowedFFDC({ "com.ibm.ws.sib.jfapchannel.JFapConnectionBrokenException",
                   "com.ibm.wsspi.sib.core.exception.SIConnectionDroppedException" })
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
        jobInstance = dispatcherUtils.waitForJobInstanceToFinish(jobInstance.getJsonNumber("instanceId").longValue(), 50);

        //Updating artifacts
        jobInstanceId = jobInstance.getJsonNumber("instanceId").longValue();
        jobExecution = dispatcherUtils.getOnlyJobExecution(jobInstanceId);
        jobExecutionId = jobExecution.getJsonNumber("executionId").longValue();

        //Checking
        assertTrue(BatchRestUtils.isDone(jobInstance));
        assertEquals("test_simplePartition", BatchRestUtils.getJobName(jobInstance));
        assertEquals("SimpleBatchJob#SimpleBatchJob.war", BatchRestUtils.getAppName(jobInstance));
        assertEquals("COMPLETED", jobInstance.getString("batchStatus"));

        //Get step executions
        JsonArray stepExecutions = dispatcherUtils.getStepExecutionFromExecutionIdAndStepName(jobExecutionId, "step1");

        //Setup Step Artifacts and checking
        assertEquals(stepExecutions.size(), 2); //0: step1, 1: _links
        BatchFatUtils.checkPartitionsSizeAndStatusCompleted(stepExecutions, "step1", 3);
    }

    /**
     * Send Jms TextMessage type. Expecting the endpoint listener to discard message
     *
     * @throws Exception
     */

    @Ignore
    public void testDiscardInvalidMessageType_MultiServers() throws Exception {
        String appName = "SimpleBatchJob";
        String msgToWaitFor = "CWWKY0200W";

        BatchJmsFatUtils.runInServlet(dispatcherHost, dispatcherPort, "sendInvalidMessageToQueue", appName);

        String uploadMessage = endpointServer.waitForStringInLogUsingMark(msgToWaitFor, endpointServer.getMatchingLogFile("trace.log"));

        assertNotNull("Could not find message: " + msgToWaitFor, uploadMessage);

    }

    /**
     * Send Stop as operation type. Expecting the endpoint listener to discard message
     *
     * @throws Exception
     */

    @Test
    public void testDiscardWrongOperation_MultiServers() throws Exception {
        String appName = "SimpleBatchJob";
        String operation = "Stop";
        String msgToWaitFor = "CWWKY0201W";

        BatchJmsFatUtils.runInServlet(dispatcherHost, dispatcherPort, "sendMapMessageToQueue", appName, operation);
        String uploadMessage = endpointServer.waitForStringInLogUsingMark(msgToWaitFor, endpointServer.getMatchingLogFile("trace.log"));
        assertNotNull("Could not find message: " + msgToWaitFor, uploadMessage);
    }

    /**
     * Submit a job to a rest interface.
     * Expecting BatchJmsDispatcher to put message on queue
     * Expecting BatchJmsEndpointListener to pick up message and execute
     */

    @Test
    public void testJmsSubmitJob_MultiServers() throws Exception {
        BatchRestUtils dispatcherUtils = new BatchRestUtils(dispatcherServer);

        JsonObject jobInstance = dispatcherUtils.submitJob("SimpleBatchJob", "test_sleepyBatchlet");
        JsonObject jobInstance2 = dispatcherUtils.waitForJobInstanceToFinish(jobInstance.getJsonNumber("instanceId").longValue());

        assertEquals(BatchStatus.COMPLETED, BatchStatus.valueOf(jobInstance2.getString("batchStatus")));
    }

    /**
     * Submit a job that will go to FAILED status, and restart it
     *
     * @throws Exception
     */

    @Test
    @ExpectedFFDC({ "java.lang.Exception", "com.ibm.jbatch.container.exception.BatchContainerRuntimeException" })
    public void testJmsRestartJob_MultiServers() throws Exception {

        BatchRestUtils dispatcherUtils = new BatchRestUtils(dispatcherServer);

        //set this prop so job is running longer, so we can catch it to stop.
        Properties jobProps = new Properties();
        jobProps.setProperty("force.failure", "true");

        JsonObject jobInstance = dispatcherUtils.submitJob("SimpleBatchJob", "test_batchlet_stepCtx", jobProps);

        long instanceId = jobInstance.getJsonNumber("instanceId").longValue();
        jobInstance = dispatcherUtils.waitForJobInstanceToFinish(instanceId);

        //the check for job in final state already done by waitForJobInstanceToFinish
        //so if we get here, job ran
        BatchStatus status = BatchStatus.valueOf(jobInstance.getString("batchStatus"));
        assertEquals(BatchStatus.FAILED, status);

        //restart, this time with force.failure == false
        jobProps.setProperty("force.failure", "false");

        JsonObject jobInstanceRestart = dispatcherUtils.restartJobInstance(instanceId, jobProps);
        assertEquals(instanceId, BatchRestUtils.instanceId(jobInstanceRestart));

        String msgToWaitFor = "handleRestartRequest Entry";
        String waitMessage = endpointServer.waitForStringInLog(msgToWaitFor, LogScrapingTimeout, endpointServer.getMatchingLogFile("trace.log"));
        assertNotNull("Could not find message " + msgToWaitFor, waitMessage);

        Thread.sleep(2 * 1000);

        JsonObject jobInstanceFinal = dispatcherUtils.waitForJobInstanceToFinish(instanceId);
        assertEquals(BatchStatus.COMPLETED, BatchStatus.valueOf(jobInstanceFinal.getString("batchStatus")));
    }

    /**
     * Endpoint has message selector for BonusPayout, but don't actually have the app (BonusPayoutFake)
     * Verify exception is thrown, and job status is set to FAILED
     *
     * @throws Exception
     */
    @Test
    @ExpectedFFDC({ "com.ibm.ws.jbatch.rest.bridge.BatchContainerAppNotFoundException",
                    "java.lang.IllegalStateException" })
    @AllowedFFDC("java.lang.reflect.InvocationTargetException")
    public void testAppNotInstallAtEndpoint_MultiServers() throws Exception {

        BatchRestUtils dispatcherUtils = new BatchRestUtils(dispatcherServer);

        JsonObject jobInstance = dispatcherUtils.submitJob("BonusPayoutFake", "BonusPayoutJob");

        String msgToWaitFor = "CWWKY0209E:";
        String uploadMessage = endpointServer.waitForStringInLogUsingMark(msgToWaitFor, endpointServer.getMatchingLogFile("trace.log"));
        assertNotNull("Could not find message " + msgToWaitFor, uploadMessage);

        long instanceId = BatchRestUtils.instanceId(jobInstance);

        jobInstance = dispatcherUtils.waitForFinalJobInstanceState(instanceId);

        assertEquals(InstanceStateMirrorImage.FAILED.toString(), jobInstance.getString("instanceState"));

    }

    /**
     * Submit a job with a valid message priority specified
     *
     * @throws Exception
     */
    @Test
    public void testJmsMessagePriority() throws Exception {
        BatchRestUtils dispatcherUtils = new BatchRestUtils(dispatcherServer);

        Properties jobParameters = new Properties();
        jobParameters.setProperty("com_ibm_ws_batch_message_priority", "9");
        JsonObject jobInstance = dispatcherUtils.submitJob("SimpleBatchJob", "test_sleepyBatchlet", jobParameters);
        JsonObject jobInstance2 = dispatcherUtils.waitForJobInstanceToFinish(jobInstance.getJsonNumber("instanceId").longValue());
        assertEquals(BatchStatus.COMPLETED, BatchStatus.valueOf(jobInstance2.getString("batchStatus")));
    }

    /**
     * Submit a job with a invalid message priority specified
     *
     * @throws Exception
     */
    @Test
    @ExpectedFFDC({ "javax.jms.JMSException", "com.ibm.ws.jbatch.jms.internal.BatchJmsDispatcherException" })
    public void testJmsMessageInvalidPriority() throws Exception {
        BatchRestUtils dispatcherUtils = new BatchRestUtils(dispatcherServer);

        Properties jobParameters = new Properties();
        jobParameters.setProperty("com_ibm_ws_batch_message_priority", "45");

        JsonObjectBuilder payloadBuilder = Json.createObjectBuilder().add("applicationName", "SimpleBatchJob").add("jobXMLName", "test_sleepyBatchlet").add("jobParameters",
                                                                                                                                                            BatchRestUtils.propertiesToJson(jobParameters));
        JsonObject jobSubmitPayload = payloadBuilder.build();
        log("submitJob", "Request: jobSubmitPayload= " + jobSubmitPayload.toString());

        URL url = dispatcherUtils.buildURL("/ibm/api/batch/jobinstances");

        HttpURLConnection con = HttpUtils.getHttpConnection(url,
                                                            HttpURLConnection.HTTP_INTERNAL_ERROR,
                                                            new int[0],
                                                            BatchRestUtils.HTTP_TIMEOUT,
                                                            HTTPRequestMethod.POST,
                                                            BatchRestUtils.buildHeaderMap(),
                                                            new ByteArrayInputStream(jobSubmitPayload.toString().getBytes("UTF-8")));

    }

    /**
     * Submit a job with a valid message delay specified
     *
     * @throws Exception
     */
    @Test
    public void testJmsMessageDelay() throws Exception {
        BatchRestUtils dispatcherUtils = new BatchRestUtils(dispatcherServer);

        Properties jobParameters = new Properties();
        jobParameters.setProperty("com_ibm_ws_batch_message_deliveryDelay", "5000"); // 5 seconds
        JsonObject jobInstance = dispatcherUtils.submitJob("SimpleBatchJob", "test_sleepyBatchlet", jobParameters);
        JsonObject jobInstance2 = dispatcherUtils.waitForJobInstanceToFinish(jobInstance.getJsonNumber("instanceId").longValue());
        assertEquals(BatchStatus.COMPLETED, BatchStatus.valueOf(jobInstance2.getString("batchStatus")));
    }

    /**
     * Submit a job with an invalid message delay specified
     *
     * @throws Exception
     */
    @Test
    @ExpectedFFDC({ "com.ibm.ws.jbatch.jms.internal.BatchJmsDispatcherException", "java.lang.NumberFormatException" })
    public void testJmsMessageInvalidDelay() throws Exception {
        BatchRestUtils dispatcherUtils = new BatchRestUtils(dispatcherServer);

        Properties jobParameters = new Properties();
        jobParameters.setProperty("com_ibm_ws_batch_message_deliveryDelay", "ABCD"); // must be numeric

        JsonObjectBuilder payloadBuilder = Json.createObjectBuilder().add("applicationName", "SimpleBatchJob").add("jobXMLName", "test_sleepyBatchlet").add("jobParameters",
                                                                                                                                                            BatchRestUtils.propertiesToJson(jobParameters));
        JsonObject jobSubmitPayload = payloadBuilder.build();
        log("submitJob", "Request: jobSubmitPayload= " + jobSubmitPayload.toString());

        URL url = dispatcherUtils.buildURL("/ibm/api/batch/jobinstances");

        HttpURLConnection con = HttpUtils.getHttpConnection(url,
                                                            HttpURLConnection.HTTP_INTERNAL_ERROR,
                                                            new int[0],
                                                            BatchRestUtils.HTTP_TIMEOUT,
                                                            HTTPRequestMethod.POST,
                                                            BatchRestUtils.buildHeaderMap(),
                                                            new ByteArrayInputStream(jobSubmitPayload.toString().getBytes("UTF-8")));
    }

}
