/*
 * IBM Confidential
 *
 * OCO Source Materials
 *
 * WLP Copyright IBM Corp. 2014
 *
 * The source code for this program is not published or otherwise divested
 * of its trade secrets, irrespective of what has been deposited with the
 * U.S. Copyright Office.
 */
package batch.jms.fat.junit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import javax.batch.runtime.BatchStatus;
import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.ibm.websphere.simplicity.log.Log;
import com.ibm.ws.common.internal.encoder.Base64Coder;
import com.ibm.ws.jbatch.jms.internal.BatchJmsConstants;
import com.ibm.ws.jbatch.test.BatchJmsFatUtils;
import com.ibm.ws.jbatch.test.FatUtils;

import componenttest.annotation.AllowedFFDC;
import componenttest.annotation.ExpectedFFDC;
import componenttest.custom.junit.runner.FATRunner;
import componenttest.custom.junit.runner.Mode;
import componenttest.custom.junit.runner.Mode.TestMode;
import componenttest.rules.repeater.JakartaEE9Action;
import componenttest.topology.impl.LibertyServer;
import componenttest.topology.impl.LibertyServerFactory;
import componenttest.topology.utils.HttpUtils;
import componenttest.topology.utils.HttpUtils.HTTPRequestMethod;

/**
 * Sender, Receiver and Message Engine on same server
 */
@RunWith(FATRunner.class)
@Mode(TestMode.FULL)
public class BatchJmsSingleServerTest {

    private static final LibertyServer server = LibertyServerFactory.getLibertyServer("BatchJmsSingleServer_fat");

    private static final String testServletHost = server.getHostname();
    private static final int testServletPort = server.getHttpDefaultPort();

    /**
     * Shouldn't have to wait more than 10s for messages to appear.
     */
    private static final long LogScrapingTimeout = 10 * 1000;

    //Instance fields
    private final Map<String, String> adminHeaderMap;

    // As defined in the server.xml
    protected final static String ADMIN_NAME = "bob";
    protected final static String ADMIN_PASSWORD = "bobpwd";

    public BatchJmsSingleServerTest() {
        adminHeaderMap = Collections.singletonMap("Authorization", "Basic " + Base64Coder.base64Encode(ADMIN_NAME + ":" + ADMIN_PASSWORD));
    }

    /**
     * Startup the server
     */
    @BeforeClass
    public static void beforeClass() throws Exception {

        HttpUtils.trustAllCertificates();
        
        if (JakartaEE9Action.isActive()) {
            JakartaEE9Action.transformApp(Paths.get(server.getServerRoot(), "dropins", "jmsweb.war"));
            JakartaEE9Action.transformApp(Paths.get(server.getServerRoot(), "dropins", "SimpleBatchJob.war"));
            JakartaEE9Action.transformApp(Paths.get(server.getServerRoot(), "dropins", "DbServletApp.war"));
        }

        server.setServerConfigurationFile("DefaultBatchJmsConfig/server.xml");
        server.copyFileToLibertyServerRoot("DefaultBatchJmsConfig/bootstrap.properties");
        server.startServer();

        FatUtils.waitForStartupAndSsl(server);

    }

    /**
     * Shutdown the server.
     */
    @AfterClass
    public static void afterClass() throws Exception {
        if (server != null && server.isStarted()) {
            server.stopServer("CWWKY0201W", "CWWKY0047W", "CWWKY0011W", "CWWKG0032W");
        }
    }

    /**
     * Send Jms TextMessage type. Expecting the endpoint listener to discard message
     *
     * @throws Exception
     */
    @Ignore
    public void testJmsListenerDiscardMessageWithInvalidMessageType() throws Exception {
        String appName = "SimpleBatchJob";
        String msgToWaitFor = "CWWKY0200W";

        BatchJmsFatUtils.runInServlet(testServletHost, testServletPort, "sendInvalidMessageToQueue", appName);

        String uploadMessage = server.waitForStringInLog(msgToWaitFor, LogScrapingTimeout, server.getMatchingLogFile("trace.log"));

        assertNotNull("Could not find message: " + msgToWaitFor, uploadMessage);

    }

    /**
     * Send a valid type message, expecting the endpoint listener to receive message, but will not process message
     * because we don't want it to process message (by not setting the operation)
     *
     * @throws Exception
     */
    @Test
    public void testJmsListenerReceiveMesage() throws Exception {

        String appName = "SimpleBatchJob";
        String msgToWaitFor = "received message from " + BatchJmsConstants.JBATCH_JMS_LISTENER_CLASS_NAME + " for applicationName: ";

        BatchJmsFatUtils.runInServlet(testServletHost, testServletPort, "sendNoOpMapMessageToQueue", appName);
        String uploadMessage = server.waitForStringInLog(msgToWaitFor + appName, LogScrapingTimeout, server.getMatchingLogFile("trace.log"));
        assertNotNull("Could not find message: " + msgToWaitFor + appName, uploadMessage);

        appName = "BonusPayOut";
        BatchJmsFatUtils.runInServlet(testServletHost, testServletPort, "sendNoOpMapMessageToQueue", appName);
        uploadMessage = server.waitForStringInLog(msgToWaitFor + appName, LogScrapingTimeout, server.getMatchingLogFile("trace.log"));
        assertNotNull("Could not find message " + msgToWaitFor + appName, uploadMessage);
    }

    /**
     * Send Stop as operation type. Expecting the endpoint listener to discard message
     *
     * @throws Exception
     */
    @Test
    public void testJmsListenerDiscardMessageWithWrongOperation() throws Exception {
        String appName = "SimpleBatchJob";
        String operation = "Stop";
        String msgToWaitFor = "CWWKY0201W";

        BatchJmsFatUtils.runInServlet(testServletHost, testServletPort, "sendMapMessageToQueue", appName, operation);
        String uploadMessage = server.waitForStringInLog(msgToWaitFor, LogScrapingTimeout, server.getMatchingLogFile("trace.log"));
        assertNotNull("Could not find message: " + msgToWaitFor, uploadMessage);
    }

    /**
     * Submit a job to a rest interface.
     * Expecting BatchJmsDispatcher to put message on queue
     * Expecting BatchJmsEndpointListener to pick up message and execute
     */
    @Test
    public void testJmsDispatcherRESTPostSubmitJob() throws Exception {

        BatchRestUtils serverUtils = new BatchRestUtils(server);

        JsonObject jobInstance = serverUtils.submitJob("SimpleBatchJob", "test_batchlet_stepCtx");
        jobInstance = serverUtils.waitForJobInstanceToFinish(jobInstance.getJsonNumber("instanceId").longValue());

        assertTrue(BatchRestUtils.isDone(jobInstance));
        assertEquals("test_batchlet_stepCtx", BatchRestUtils.getJobName(jobInstance));
        assertEquals(ADMIN_NAME, BatchRestUtils.getSubmitter(jobInstance));
        assertEquals("SimpleBatchJob#SimpleBatchJob.war", BatchRestUtils.getAppName(jobInstance));

        JsonArray linkArray = jobInstance.getJsonArray("_links");
        assertNotNull(linkArray);

    }

    /**
     * Submit a job to a rest interface.
     * Expecting BatchJmsDispatcher to put message on queue
     * Expecting BatchJmsEndpointListener to pick up message and execute
     */
    @Test
    @AllowedFFDC({ "com.ibm.wsspi.sib.core.exception.SIConnectionDroppedException" })
    public void testJmsPartitionedJob() throws Exception {

        BatchRestUtils serverUtils = new BatchRestUtils(server);

        JsonObject jobInstance = serverUtils.submitJob("SimpleBatchJob", "test_simplePartition");
        jobInstance = serverUtils.waitForJobInstanceToFinish(jobInstance.getJsonNumber("instanceId").longValue(), 30);

        assertTrue(BatchRestUtils.isDone(jobInstance));
        assertEquals("test_simplePartition", BatchRestUtils.getJobName(jobInstance));
        assertEquals(ADMIN_NAME, BatchRestUtils.getSubmitter(jobInstance));
        assertEquals("SimpleBatchJob#SimpleBatchJob.war", BatchRestUtils.getAppName(jobInstance));
        assertEquals("COMPLETED", jobInstance.getString("batchStatus"));

        JsonArray linkArray = jobInstance.getJsonArray("_links");
        assertNotNull(linkArray);

    }

    @Test
    public void testJmsSubmitJobByModuleName() throws Exception {

        String method = "testJmsSubmitJobByModuleName";
        Map<String, String> newMap = new HashMap<String, String>();
        newMap.putAll(adminHeaderMap);
        newMap.put("Content-Type", "text");

        JsonObjectBuilder jsonBuilder = Json.createObjectBuilder();
        jsonBuilder.add("moduleName", "SimpleBatchJob.war");
        jsonBuilder.add("jobXMLName", "test_batchlet_stepCtx");
        jsonBuilder.add("jobParameters", BatchJmsFatUtils.buildJsonObjectFromMap(new Properties()));

        JsonObject jsonObject = jsonBuilder.build();

        log(method, "Request: jsonObject= " + jsonObject.toString());

        InputStream input = new ByteArrayInputStream(jsonObject.toString().getBytes("UTF-8"));

        HttpURLConnection con = BatchJmsFatUtils.getConnection("/ibm/api/batch/jobinstances", HttpURLConnection.HTTP_CREATED, HTTPRequestMethod.POST, input, newMap);

        String contentType = con.getHeaderField(BatchJmsFatUtils.HEADER_CONTENT_TYPE_KEY);
        Assert.assertEquals(BatchJmsFatUtils.MEDIA_TYPE_APPLICATION_JSON, contentType);

        BufferedReader br = HttpUtils.getConnectionStream(con);

        JsonObject jsonResponse = Json.createReader(br).readObject();

        br.close();

        log(method, "Response: jsonResponse= " + jsonResponse.toString());

        //Verify that the proper mappings are returned
        String jobXMLName = jsonResponse.getString("jobXMLName");
        BatchRestUtils.assertJobNamePossiblySet("test_batchlet_stepCtx", jobXMLName);
        long jobInstanceId = jsonResponse.getJsonNumber("instanceId").longValue(); //verifies this is a valid number

        JsonArray linkArray = jsonResponse.getJsonArray("_links");
        assertNotNull(linkArray);

        assertEquals(ADMIN_NAME, jsonResponse.getString("submitter"));
        assertEquals("SimpleBatchJob#SimpleBatchJob.war", jsonResponse.getString("appName"));

        //wait for job to complete.
        JsonObject jobInstance = new BatchRestUtils(server).waitForJobInstanceToFinish(jobInstanceId);
        assertEquals("COMPLETED", jobInstance.getString("batchStatus"));
    }

    /**
     * Submit a new job, then stop it.
     * The @AllowedFFDC is to accommodate for generated FFDC in case job was completed before stop is issued
     */
    @Test
    @AllowedFFDC({ "com.ibm.ws.jbatch.rest.internal.BatchJobExecutionNotRunningException",
                   "javax.batch.operations.JobExecutionNotRunningException",
                   "java.lang.reflect.InvocationTargetException" })
    public void testJmsStopJob() throws Exception {
        String method = "testJmsStopJob";
        BatchRestUtils serverUtils = new BatchRestUtils(server);

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
        } else {
            //job was completed before stop was issued
            log(method, "job instance " + instanceId + " was completed before stop request was issued. In this case, this test is considered passing if job status is COMPLETED");
            executionInstance = serverUtils.getOnlyJobExecution(instanceId);
            BatchStatus status = BatchStatus.valueOf(executionInstance.getString("batchStatus"));
            assertTrue(BatchStatus.COMPLETED.equals(status));
        }
    }

    /**
     * Submit a job that will go to FAILED status, and restart
     *
     * @throws Exception
     */
    @Test
    @ExpectedFFDC({ "java.lang.Exception", "com.ibm.jbatch.container.exception.BatchContainerRuntimeException" })
    public void testJmsRestartJob() throws Exception {

        BatchRestUtils serverUtils = new BatchRestUtils(server);

        //set this prop so job is running longer, so we can catch it to stop.
        Properties jobProps = new Properties();
        jobProps.setProperty("force.failure", "true");

        // submit and wait for job to job to FAILED because the force.failure == true
        JsonObject jobInstance = serverUtils.submitJob("SimpleBatchJob", "test_batchlet_stepCtx", jobProps);

        long instanceId = BatchRestUtils.instanceId(jobInstance);
        jobInstance = serverUtils.waitForJobInstanceToFinish(instanceId);

        //the check for job in final state already done by waitForJobInstanceToFinish
        //so if we get here, job ran
        BatchStatus status = BatchStatus.valueOf(jobInstance.getString("batchStatus"));
        assertEquals(BatchStatus.FAILED, status);

        //restart, this time with force.failure == false
        jobProps.setProperty("force.failure", "false");

        JsonObject jobInstanceRestart = serverUtils.restartJobInstance(instanceId, jobProps);
        assertEquals(instanceId, BatchRestUtils.instanceId(jobInstanceRestart));

        //on the endpoint, wait for message to show up
        String msgToWaitFor = "handleRestartRequest Entry";
        String waitMessage = server.waitForStringInLog(msgToWaitFor, LogScrapingTimeout, server.getMatchingLogFile("trace.log"));
        assertNotNull("Could not find message " + msgToWaitFor, waitMessage);

        Thread.sleep(2 * 1000);

        JsonObject jobInstanceFinal = serverUtils.waitForJobInstanceToFinish(instanceId);
        assertEquals(BatchStatus.COMPLETED, BatchStatus.valueOf(jobInstanceFinal.getString("batchStatus")));

    }

    /*
     * This test checks that two simultaneous jobintance restart requests might not create two jobexecutions
     *
     * Ignoring it because we do not have a fix for this yet, and will always fail.
     */
    @Ignore
    @Test
    @ExpectedFFDC({ "javax.batch.operations.JobRestartException",
                    "com.ibm.ws.jbatch.rest.internal.BatchJobRestartException" })
    public void testRestartInstanceSimultaneously() throws Exception {

        BatchRestUtils restUtils = new BatchRestUtils(server);

        JsonObject jobInstance = restUtils.submitJob("SimpleBatchJob", "test_sleepyBatchlet");
        long jobInstanceId = jobInstance.getJsonNumber("instanceId").longValue();

        JsonObject jobExecution = restUtils.waitForFirstJobExecution(jobInstanceId);
        long jobExecutionId = jobExecution.getJsonNumber("executionId").longValue();

        //Wait for jobExecution and stepExecution to be in STARTED state, or else the stop request will fail
        restUtils.waitForStepInJobExecutionToStart(jobExecutionId, "step1");

        //Stop the job
        assertTrue(restUtils.stopJob(jobExecutionId));
        restUtils.waitForJobInstanceToFinish(jobInstanceId);

        jobExecution = restUtils.getJobExecutionFromExecutionId(jobExecutionId);
        assertEquals("STOPPED", jobExecution.getString("batchStatus"));
        jobExecution = restUtils.getJobExecutionFromExecutionId(jobExecutionId);
        JsonArray stepExecutions = jobExecution.getJsonArray("stepExecutions");
        assertEquals(1, stepExecutions.size());
        JsonObject stepExecution = stepExecutions.getJsonObject(0);
        assertEquals("STOPPED", stepExecution.getString("batchStatus"));

        jobInstance = restUtils.restartJobInstance(jobInstanceId, new Properties());
        assertEquals("JMS_QUEUED", jobInstance.getString("instanceState"));
        //Second job request. Should fail because the first restart request might not be fully processed( i.e consumed)
        restUtils.restartJobInstanceExpectHttpConflict(jobInstanceId, new Properties());

        //Should only have restarted once, hence a total of two jobExecutions
        assertEquals(2, restUtils.getJobExecutionsMostRecentFirst(jobInstanceId).size());

        assertNotNull(server.waitForStringInLog("Cannot restart instance " + jobInstanceId
                                                + " because the previous restart request is not executed yet."));
    }

    /**
     * helper for simple logging.
     */
    private static void log(String method, String msg) {
        Log.info(BatchJmsSingleServerTest.class, method, msg);
    }
}
