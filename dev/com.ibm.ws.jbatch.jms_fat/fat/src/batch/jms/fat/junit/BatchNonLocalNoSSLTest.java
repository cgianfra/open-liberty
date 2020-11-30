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

import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Paths;

import javax.batch.runtime.BatchStatus;
import javax.batch.runtime.JobExecution;
import javax.batch.runtime.JobInstance;
import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.ibm.websphere.simplicity.ProgramOutput;
import com.ibm.websphere.simplicity.log.Log;
import com.ibm.ws.jbatch.test.BatchManagerCliUtils;
import com.ibm.ws.jbatch.test.FatUtils;

import componenttest.annotation.AllowedFFDC;
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
public class BatchNonLocalNoSSLTest {

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
        HttpURLConnection.setFollowRedirects(false);

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

        //start dispatcher
        dispatcherServer.startServer();
        FatUtils.waitForStartupAndSsl(dispatcherServer);

        assertEquals(Integer.getInteger("batch.dispatcher_1_HTTP_default").intValue(), dispatcherServer.getHttpDefaultPort());

        //start endpoint
        endpointServer.startServer();

        FatUtils.waitForStartupAndSsl(endpointServer);
        FatUtils.waitForLTPA(endpointServer); //because endpoint has app security enabled

        assertEquals(Integer.getInteger("batch.endpoint_1_HTTP_default").intValue(), endpointServer.getHttpDefaultPort());

        // Skip certificate setup to prevent use of SSL
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

        // This message will only show up once, check for it here, rather than after
        // every test to avoid timing issues
        assertNotNull("Dispatcher server did not attempt to use SSL before falling back to redirect",
                      dispatcherServer.waitForStringInLog("CWWKY0154I"));

        if (dispatcherServer != null && dispatcherServer.isStarted()) {
            dispatcherServer.stopServer("CWSIJ0077E");
        }

        if (endpointServer != null && endpointServer.isStarted()) {
            endpointServer.stopServer("CWSIJ0077E");
        }

        if (messageEngineServer != null && messageEngineServer.isStarted()) {
            // Blindly copied this list from another test, assuming we're not quiescing nicely enough to avoid this kind of thing.
            messageEngineServer.stopServer("CWSIC2019E", "CWSIJ0047E", "CWSIC2023E", "CWSIC2018E", "CWSIJ0077E");
        }
    }

    /**
     * helper for simple logging.
     */
    private static void log(String method, Object msg) {
        Log.info(BatchNonLocalNoSSLTest.class, method, String.valueOf(msg));
    }

    /**
     * Test that sending a STOP request to the dispatcher will get http-302 redirected to the endpoint
     */

    @Test
    @AllowedFFDC({ "javax.net.ssl.SSLHandshakeException", "sun.security.validator.ValidatorException", "java.security.cert.CertPathBuilderException",
                   "com.ibm.security.cert.IBMCertPathBuilderException",
                   "com.ibm.wsspi.sib.core.exception.SIConnectionDroppedException" })
    public void testStopJobExecutionRedirect() throws Exception {

        BatchRestUtils dispatcherUtils = new BatchRestUtils(dispatcherServer);

        JsonObject jobInstance = dispatcherUtils.submitJob("SimpleBatchJob", "test_sleepyBatchlet");
        long jobInstanceId = jobInstance.getJsonNumber("instanceId").longValue();

        JsonObject jobExecution = dispatcherUtils.waitForFirstJobExecution(jobInstanceId);

        //JsonObject jobExecution = dispatcherUtils.waitForJobInstanceToStart(jobInstance.getJsonNumber("instanceId").longValue());
        long jobExecutionId = jobExecution.getJsonNumber("executionId").longValue();
        jobExecution = dispatcherUtils.waitForJobExecutionToStart(jobExecutionId);

        // The job should be running on the endpoint (listenerServer).
        // Now try to stop it via the dispatcher. Should get a 302 redirect back.
        HttpURLConnection con = HttpUtils.getHttpConnection(dispatcherUtils.buildURL("/ibm/api/batch/jobexecutions/" + jobExecution.getJsonNumber("executionId").longValue()
                                                                                     + "?action=stop"),
                                                            HttpURLConnection.HTTP_MOVED_TEMP,
                                                            new int[0],
                                                            10,
                                                            HTTPRequestMethod.PUT,
                                                            BatchRestUtils.buildHeaderMap(),
                                                            null);

        BatchRestUtils endpointUtils = new BatchRestUtils(endpointServer);
        String expectedLocation = endpointUtils.buildURLUsingIPAddr("/ibm/api/batch/jobexecutions/" + jobExecution.getJsonNumber("executionId").longValue()
                                                                    + "?action=stop").toString();

        assertEquals(expectedLocation, con.getHeaderField("Location"));

        // Forward STOP to redirected url. This should work (response 200 or 400 if the job already finished)
        long instanceId = jobInstance.getJsonNumber("instanceId").longValue();

        if (dispatcherUtils.stopJob(new URL(con.getHeaderField("Location")))) {
            jobInstance = dispatcherUtils.waitForJobInstanceToFinish(instanceId);

            //the check for job in final state already done by waitForJobInstanceToFinish
            //so if we get here, job was stopped, just verify status

            BatchStatus status = BatchStatus.valueOf(jobInstance.getString("batchStatus"));
            assertTrue(BatchStatus.STOPPED.equals(status));
        } else {

            JsonObject executionInstance = dispatcherUtils.getOnlyJobExecution(instanceId);
            BatchStatus status = BatchStatus.valueOf(executionInstance.getString("batchStatus"));
            assertTrue(BatchStatus.COMPLETED.equals(status));
        }
    }

    /**
     * Test that sending a STOP request to the dispatcher will get http-302 redirected to the endpoint
     */

    @Test
    @AllowedFFDC({ "javax.net.ssl.SSLHandshakeException", "sun.security.validator.ValidatorException", "java.security.cert.CertPathBuilderException",
                   "com.ibm.security.cert.IBMCertPathBuilderException" })
    public void testStopJobInstanceRedirect() throws Exception {

        BatchRestUtils dispatcherUtils = new BatchRestUtils(dispatcherServer);

        JsonObject jobInstance = dispatcherUtils.submitJob("SimpleBatchJob", "test_sleepyBatchlet");
        long jobInstanceId = jobInstance.getJsonNumber("instanceId").longValue();

        JsonObject jobExecution = dispatcherUtils.waitForFirstJobExecution(jobInstanceId);

        //JsonObject jobExecution = dispatcherUtils.waitForJobInstanceToStart(jobInstance.getJsonNumber("instanceId").longValue());
        long jobExecutionId = jobExecution.getJsonNumber("executionId").longValue();
        jobExecution = dispatcherUtils.waitForJobExecutionToStart(jobExecutionId);

        // The job should be running on the endpoint (listenerServer).
        // Now try to stop it via the dispatcher. Should get a 302 redirect back.
        HttpURLConnection con = HttpUtils.getHttpConnection(dispatcherUtils.buildURL("/ibm/api/batch/jobinstances/" + jobInstance.getJsonNumber("instanceId").longValue()
                                                                                     + "?action=stop"),
                                                            HttpURLConnection.HTTP_MOVED_TEMP,
                                                            new int[0],
                                                            10,
                                                            HTTPRequestMethod.PUT,
                                                            BatchRestUtils.buildHeaderMap(),
                                                            null);

        BatchRestUtils endpointUtils = new BatchRestUtils(endpointServer);
        String expectedLocation = endpointUtils.buildURLUsingIPAddr("/ibm/api/batch/jobinstances/" + jobInstance.getJsonNumber("instanceId").longValue()
                                                                    + "?action=stop").toString();

        assertEquals(expectedLocation, con.getHeaderField("Location"));

        // Forward STOP to redirected url. This should work (response 200 or 400 if the job already finished)
        long instanceId = jobInstance.getJsonNumber("instanceId").longValue();

        if (dispatcherUtils.stopJob(new URL(con.getHeaderField("Location")))) {
            jobInstance = dispatcherUtils.waitForJobInstanceToFinish(instanceId);

            //the check for job in final state already done by waitForJobInstanceToFinish
            //so if we get here, job was stopped, just verify status

            BatchStatus status = BatchStatus.valueOf(jobInstance.getString("batchStatus"));
            assertTrue(BatchStatus.STOPPED.equals(status));
        } else {

            JsonObject executionInstance = dispatcherUtils.getOnlyJobExecution(instanceId);
            BatchStatus status = BatchStatus.valueOf(executionInstance.getString("batchStatus"));
            assertTrue(BatchStatus.COMPLETED.equals(status));
        }
    }

    /**
     * Test that sending a joblogs request to the dispatcher will get http-302 redirected to the endpoint
     */

    @Test
    @AllowedFFDC({ "javax.net.ssl.SSLHandshakeException", "sun.security.validator.ValidatorException", "java.security.cert.CertPathBuilderException",
                   "com.ibm.security.cert.IBMCertPathBuilderException" })
    public void testJoblogsRedirect() throws Exception {

        BatchRestUtils dispatcherUtils = new BatchRestUtils(dispatcherServer);

        JsonObject jobInstance = dispatcherUtils.submitJob("SimpleBatchJob", "test_sleepyBatchlet");
        long jobInstanceId = jobInstance.getJsonNumber("instanceId").longValue();

        JsonObject jobExecution = dispatcherUtils.waitForFirstJobExecution(jobInstanceId);

        //JsonObject jobExecution = dispatcherUtils.waitForJobInstanceToStart(jobInstance.getJsonNumber("instanceId").longValue());
        long jobExecutionId = jobExecution.getJsonNumber("executionId").longValue();
        jobExecution = dispatcherUtils.waitForJobExecutionToStart(jobExecutionId);

        // The job should be running on the endpoint (listenerServer).
        // Now try to get the joblogs via the dispatcher. Should get a 302 redirect back.
        HttpURLConnection con = HttpUtils.getHttpConnection(dispatcherUtils.buildURL("/ibm/api/batch/jobexecutions/" + jobExecution.getJsonNumber("executionId").longValue()
                                                                                     + "/joblogs"),
                                                            HttpURLConnection.HTTP_MOVED_TEMP,
                                                            new int[0],
                                                            10,
                                                            HTTPRequestMethod.GET,
                                                            BatchRestUtils.buildHeaderMap(),
                                                            null);

        BatchRestUtils endpointUtils = new BatchRestUtils(endpointServer);
        String expectedLocation = endpointUtils.buildURLUsingIPAddr("/ibm/api/batch/jobexecutions/" + jobExecution.getJsonNumber("executionId").longValue()
                                                                    + "/joblogs").toString();

        assertEquals(expectedLocation, con.getHeaderField("Location"));

        // Forward to redirected url. This should work (response 200).
        HttpURLConnection con2 = HttpUtils.getHttpConnection(new URL(con.getHeaderField("Location")),
                                                             HttpURLConnection.HTTP_OK,
                                                             new int[0],
                                                             10,
                                                             HTTPRequestMethod.GET,
                                                             BatchRestUtils.buildHeaderMap(),
                                                             null);

        assertEquals(200, con2.getResponseCode());

    }

    /**
     * Test that sending a joblogs request to the dispatcher will get http-302 redirected to the endpoint
     */

    @Test
    @AllowedFFDC({ "javax.net.ssl.SSLHandshakeException", "sun.security.validator.ValidatorException", "java.security.cert.CertPathBuilderException",
                   "com.ibm.security.cert.IBMCertPathBuilderException" })
    public void testJoblogsRedirectWithQueryParms() throws Exception {

        BatchRestUtils dispatcherUtils = new BatchRestUtils(dispatcherServer);

        JsonObject jobInstance = dispatcherUtils.submitJob("SimpleBatchJob", "test_sleepyBatchlet");
        long jobInstanceId = jobInstance.getJsonNumber("instanceId").longValue();

        JsonObject jobExecution = dispatcherUtils.waitForFirstJobExecution(jobInstanceId);

        //JsonObject jobExecution = dispatcherUtils.waitForJobInstanceToStart(jobInstance.getJsonNumber("instanceId").longValue());
        long jobExecutionId = jobExecution.getJsonNumber("executionId").longValue();
        jobExecution = dispatcherUtils.waitForJobExecutionToStart(jobExecutionId);

        // The job should be running on the endpoint (listenerServer).
        // Now try to get the joblogs via the dispatcher. Should get a 302 redirect back.
        HttpURLConnection con = HttpUtils.getHttpConnection(dispatcherUtils.buildURL("/ibm/api/batch/jobexecutions/" + jobExecution.getJsonNumber("executionId").longValue()
                                                                                     + "/joblogs?type=text&part=part.1.log"),
                                                            HttpURLConnection.HTTP_MOVED_TEMP,
                                                            new int[0],
                                                            10,
                                                            HTTPRequestMethod.GET,
                                                            BatchRestUtils.buildHeaderMap(),
                                                            null);

        BatchRestUtils endpointUtils = new BatchRestUtils(endpointServer);
        String expectedLocation = endpointUtils.buildURLUsingIPAddr("/ibm/api/batch/jobexecutions/" + jobExecution.getJsonNumber("executionId").longValue() +
                                                                    "/joblogs?type=text&part=part.1.log").toString();

        assertEquals(expectedLocation, con.getHeaderField("Location"));

        // Forward to redirected url. This should work (response 200).
        HttpURLConnection con2 = HttpUtils.getHttpConnection(new URL(con.getHeaderField("Location")),
                                                             HttpURLConnection.HTTP_OK,
                                                             new int[0],
                                                             10,
                                                             HTTPRequestMethod.GET,
                                                             BatchRestUtils.buildHeaderMap(),
                                                             null);

        assertEquals(200, con2.getResponseCode());

    }

    /**
     * Test that sending a joblogs request to the dispatcher will get links to the endpoint
     */

    @Test
    @AllowedFFDC({ "javax.net.ssl.SSLHandshakeException", "sun.security.validator.ValidatorException", "java.security.cert.CertPathBuilderException",
                   "com.ibm.security.cert.IBMCertPathBuilderException" })
    public void testJoblogsJobInstanceRemoteExecution() throws Exception {

        BatchRestUtils dispatcherUtils = new BatchRestUtils(dispatcherServer);

        JsonObject jobInstance = dispatcherUtils.submitJob("SimpleBatchJob", "test_sleepyBatchlet");
        long jobInstanceId = jobInstance.getJsonNumber("instanceId").longValue();

        JsonObject jobExecution = dispatcherUtils.waitForFirstJobExecution(jobInstanceId);

        //JsonObject jobExecution = dispatcherUtils.waitForJobInstanceToStart(jobInstance.getJsonNumber("instanceId").longValue());
        long jobExecutionId = jobExecution.getJsonNumber("executionId").longValue();
        jobExecution = dispatcherUtils.waitForJobExecutionToStart(jobExecutionId);

        // The job should be running on the endpoint (listenerServer).
        // Now try to get the joblogs via the dispatcher. Should get a 302 redirect back.
        HttpURLConnection con = HttpUtils.getHttpConnection(dispatcherUtils.buildURL("/ibm/api/batch/jobinstances/" + jobInstance.getJsonNumber("instanceId").longValue()
                                                                                     + "/joblogs"),
                                                            HttpURLConnection.HTTP_OK,
                                                            new int[0],
                                                            10,
                                                            HTTPRequestMethod.GET,
                                                            BatchRestUtils.buildHeaderMap(),
                                                            null);

        // Verify that execution log links refer to the endpoint
        BatchRestUtils endpointUtils = new BatchRestUtils(endpointServer);
        String expectedLocation = endpointUtils.buildURLUsingIPAddr("/ibm/api/batch/jobexecutions/" + jobExecution.getJsonNumber("executionId").longValue()
                                                                    + "/joblogs").toString();

        JsonArray jsonResponse = Json.createReader(con.getInputStream()).readArray();
        for (JsonObject o : jsonResponse.getValuesAs(JsonObject.class)) {
            String url = o.getString("href");
            if (url.contains("jobexecutions")) {
                assertTrue("Job execution log link did not use endpoint location. Response URL: " + url + ", endpoint location: " + expectedLocation,
                           url.contains(expectedLocation));
            }
        }

    }

    /**
     * Test that sending a joblogs request to the dispatcher will get http-302 redirected to the endpoint
     *
     * Note: this works only because the executions all ran on the same endpoint (there's only 1 execution
     * here). Trying to get joblogs via instance when the executions ran on diff endpoints will fail;
     * however we don't have a test for that (yet) cuz we don't have a test env with multiple endpoints (yet).
     */

    @Test
    @AllowedFFDC({ "javax.net.ssl.SSLHandshakeException", "sun.security.validator.ValidatorException", "java.security.cert.CertPathBuilderException",
                   "com.ibm.security.cert.IBMCertPathBuilderException" })
    public void testJoblogsJobInstanceRedirectWithQueryParms() throws Exception {

        BatchRestUtils dispatcherUtils = new BatchRestUtils(dispatcherServer);

        JsonObject jobInstance = dispatcherUtils.submitJob("SimpleBatchJob", "test_sleepyBatchlet");
        long jobInstanceId = jobInstance.getJsonNumber("instanceId").longValue();

        JsonObject jobExecution = dispatcherUtils.waitForFirstJobExecution(jobInstanceId);

        //JsonObject jobExecution = dispatcherUtils.waitForJobInstanceToStart(jobInstance.getJsonNumber("instanceId").longValue());
        long jobExecutionId = jobExecution.getJsonNumber("executionId").longValue();
        jobExecution = dispatcherUtils.waitForJobExecutionToStart(jobExecutionId);

        // The job should be running on the endpoint (listenerServer).
        // Now try to get the joblogs via the dispatcher. Should get a 302 redirect back.
        HttpURLConnection con = HttpUtils.getHttpConnection(dispatcherUtils.buildURL("/ibm/api/batch/jobinstances/" + jobInstance.getJsonNumber("instanceId").longValue()
                                                                                     + "/joblogs?type=zip"),
                                                            HttpURLConnection.HTTP_MOVED_TEMP,
                                                            new int[0],
                                                            10,
                                                            HTTPRequestMethod.GET,
                                                            BatchRestUtils.buildHeaderMap(),
                                                            null);

        BatchRestUtils endpointUtils = new BatchRestUtils(endpointServer);
        String expectedLocation = endpointUtils.buildURLUsingIPAddr("/ibm/api/batch/jobinstances/" + jobInstance.getJsonNumber("instanceId").longValue()
                                                                    + "/joblogs?type=zip").toString();

        assertEquals(expectedLocation, con.getHeaderField("Location"));

        // Forward to redirected url. This should work (response 200).
        HttpURLConnection con2 = HttpUtils.getHttpConnection(new URL(con.getHeaderField("Location")),
                                                             HttpURLConnection.HTTP_OK,
                                                             new int[0],
                                                             10,
                                                             HTTPRequestMethod.GET,
                                                             BatchRestUtils.buildHeaderMap(),
                                                             null);

        assertEquals("attachment; filename=\"joblogs.test_sleepyBatchlet.instance." + jobInstance.getJsonNumber("instanceId").longValue() + ".zip\"",
                     con2.getHeaderField("Content-Disposition"));

    }

    /**
     * Test that sending a STOP request to the dispatcher via batchManager
     * will get http-302 redirected to the endpoint
     */

    @Test
    @AllowedFFDC({ "javax.net.ssl.SSLHandshakeException", "sun.security.validator.ValidatorException", "java.security.cert.CertPathBuilderException",
                   "com.ibm.security.cert.IBMCertPathBuilderException" })
    public void testStopJobInstanceRedirectViaBatchManager() throws Exception {

        final String appName = "SimpleBatchJob";
        final String jobXMLName = "test_sleepyBatchlet";
        final String user = "bob";
        final String pass = "bobpwd";

        BatchManagerCliUtils dispatcherUtils = new BatchManagerCliUtils(dispatcherServer);

        ProgramOutput po = dispatcherUtils.submitJob(new String[] { "submit",
                                                                    "--batchManager=" + dispatcherUtils.getHostAndPort(),
                                                                    "--user=" + user,
                                                                    "--password=" + pass,
                                                                    "--applicationName=" + appName,
                                                                    "--jobXMLName=" + jobXMLName,
                                                                    "--trustSslCertificates" });

        assertEquals(0, po.getReturnCode());

        JobInstance jobInstance = dispatcherUtils.parseJobInstanceFromSubmitMessages(po.getStdout());

        // Not necessarily always same as jobXMLName
        dispatcherUtils.assertJobNamePossiblySet("test_sleepyBatchlet", jobInstance.getJobName());

        // Sleep for a max 10 seconds waiting for job to stop
        dispatcherUtils.waitForStatus(10, jobInstance, BatchStatus.STARTED);

        // Now stop the job...
        // Send stop to dispatcher. The batchManager CLI should automatically redirect to
        // the endpoint.
        po = dispatcherUtils.stopJob(new String[] { "stop",
                                                    "--batchManager=" + dispatcherUtils.getHostAndPort(),
                                                    "--user=" + user,
                                                    "--password=" + pass,
                                                    "--jobInstanceId=" + jobInstance.getInstanceId(),
                                                    "--trustSslCertificates" });

        assertEquals(0, po.getReturnCode());

        // Sleep for a max 10 seconds waiting for job to stop
        dispatcherUtils.waitForStatus(10, jobInstance, BatchStatus.STOPPED, BatchStatus.COMPLETED);

        assertTrue(dispatcherUtils.isStatus(jobInstance, BatchStatus.STOPPED, BatchStatus.COMPLETED));
    }

    /**
     * Test that sending a joblogs request to the dispatcher will get http-302 redirected to the endpoint
     *
     * Note: this works only because the executions all ran on the same endpoint (there's only 1 execution
     * here). Trying to get joblogs via instance when the executions ran on diff endpoints will fail;
     * however we don't have a test for that (yet) cuz we don't have a test env with multiple endpoints (yet).
     */

    @Test
    @AllowedFFDC({ "javax.net.ssl.SSLHandshakeException", "sun.security.validator.ValidatorException", "java.security.cert.CertPathBuilderException",
                   "com.ibm.security.cert.IBMCertPathBuilderException" })
    public void testJoblogsJobInstanceRedirectViaBatchManagerCLI() throws Exception {

        final String appName = "SimpleBatchJob";
        final String jobXMLName = "test_sleepyBatchlet";
        final String user = "bob";
        final String pass = "bobpwd";

        BatchManagerCliUtils dispatcherUtils = new BatchManagerCliUtils(dispatcherServer);

        ProgramOutput po = dispatcherUtils.submitJob(new String[] { "submit",
                                                                    "--batchManager=" + dispatcherUtils.getHostAndPort(),
                                                                    "--user=" + user,
                                                                    "--password=" + pass,
                                                                    "--applicationName=" + appName,
                                                                    "--jobXMLName=" + jobXMLName,
                                                                    "--trustSslCertificates" });

        assertEquals(0, po.getReturnCode());

        JobInstance jobInstance = dispatcherUtils.parseJobInstanceFromSubmitMessages(po.getStdout());

        // Not necessarily always same as jobXMLName
        dispatcherUtils.assertJobNamePossiblySet("test_sleepyBatchlet", jobInstance.getJobName());

        // Sleep for a max 10 seconds waiting for job to start
        JobExecution jobExecution = dispatcherUtils.waitForStatus(10, jobInstance, BatchStatus.STARTED);

        // Retrieve the joblog
        // Send the request to the dispatcher; it should be auto redirected to the endpoint.
        po = dispatcherUtils.executeCommand(new String[] { "getJobLog",
                                                           "--batchManager=" + dispatcherUtils.getHostAndPort(),
                                                           "--user=" + user,
                                                           "--password=" + pass,
                                                           "--jobInstanceId=" + jobInstance.getInstanceId(),
                                                           "--trustSslCertificates" });

        assertEquals(0, po.getReturnCode());

        // Verify expected contents, including the job name, along with aggregate header/footers
        assertTrue(po.getStdout().contains("test_sleepyBatchlet"));
        assertTrue(po.getStdout().contains("Begin file: instance." + jobInstance.getInstanceId() + "/execution." + jobExecution.getExecutionId() + "/part.1.log"));
        assertTrue(po.getStdout().contains("End file: instance." + jobInstance.getInstanceId() + "/execution." + jobExecution.getExecutionId() + "/part.1.log"));
    }

    /**
     * Test that sending a jobpurge request to the dispatcher will get http-302 redirected to the endpoint
     *
     * Note: this works only because the executions all ran on the same endpoint (there's only 1 execution
     * here). Trying to get joblogs via instance when the executions ran on diff endpoints will fail;
     * however we don't have a test for that (yet) cuz we don't have a test env with multiple endpoints (yet).
     */

    @Test
    @AllowedFFDC({ "javax.net.ssl.SSLHandshakeException", "sun.security.validator.ValidatorException", "java.security.cert.CertPathBuilderException",
                   "com.ibm.security.cert.IBMCertPathBuilderException" })
    public void testJobPurgeRedirectViaBatchManagerCLI() throws Exception {

        final String appName = "SimpleBatchJob";
        final String jobXMLName = "test_batchlet_stepCtx";
        final String user = "bob";
        final String pass = "bobpwd";

        BatchManagerCliUtils dispatcherUtils = new BatchManagerCliUtils(dispatcherServer);

        ProgramOutput po = dispatcherUtils.submitJob(new String[] { "submit",
                                                                    "--batchManager=" + dispatcherUtils.getHostAndPort(),
                                                                    "--user=" + user,
                                                                    "--password=" + pass,
                                                                    "--applicationName=" + appName,
                                                                    "--jobXMLName=" + jobXMLName,
                                                                    "--trustSslCertificates" });

        assertEquals(0, po.getReturnCode());

        JobInstance jobInstance = dispatcherUtils.parseJobInstanceFromSubmitMessages(po.getStdout());

        // Not necessarily always same as jobXMLName
        dispatcherUtils.assertJobNamePossiblySet("test_batchlet_stepCtx", jobInstance.getJobName());

        // Sleep for a max 10 seconds waiting for job to start
        JobExecution jobExecution = dispatcherUtils.waitForStatus(10, jobInstance, BatchStatus.COMPLETED);

        // Retrieve the joblog
        // Send the request to the dispatcher; it should be auto redirected to the endpoint.
        po = dispatcherUtils.executeCommand(new String[] { "purge",
                                                           "--batchManager=" + dispatcherUtils.getHostAndPort(),
                                                           "--user=" + user,
                                                           "--password=" + pass,
                                                           "--jobInstanceId=" + jobInstance.getInstanceId(),
                                                           "--trustSslCertificates" });

        assertEquals(0, po.getReturnCode());
    }

    /**
     * Test that sending a purge request to the dispatcher will get http-302 redirected to the endpoint
     */
    @Test
    @AllowedFFDC({ "javax.net.ssl.SSLHandshakeException", "sun.security.validator.ValidatorException", "java.security.cert.CertPathBuilderException",
                   "com.ibm.security.cert.IBMCertPathBuilderException" })
    public void testJobPurgeRedirect() throws Exception {

        BatchRestUtils dispatcherUtils = new BatchRestUtils(dispatcherServer);

        JsonObject jobInstance = dispatcherUtils.submitJob("SimpleBatchJob", "test_batchlet_stepCtx");
        long jobInstanceId = jobInstance.getJsonNumber("instanceId").longValue();
        JsonObject jobExecution = dispatcherUtils.waitForJobInstanceToFinish(jobInstanceId);

        // The job should have run on the endpoint (listenerServer).
        // Now try to purge the joblogs via the dispatcher.  Should get a 302 redirect back.
        HttpURLConnection con = HttpUtils.getHttpConnection(dispatcherUtils.buildURL("/ibm/api/batch/jobinstances/" + jobInstanceId),
                                                            HttpURLConnection.HTTP_MOVED_TEMP,
                                                            new int[0],
                                                            10,
                                                            HTTPRequestMethod.DELETE,
                                                            BatchRestUtils.buildHeaderMap(),
                                                            null);

        BatchRestUtils endpointUtils = new BatchRestUtils(endpointServer);
        String expectedLocation = endpointUtils.buildURLUsingIPAddr("/ibm/api/batch/jobinstances/" + jobInstanceId).toString();

        assertEquals(expectedLocation, con.getHeaderField("Location"));

        // Forward to redirected url.  This should work (response 200).
        HttpURLConnection con2 = HttpUtils.getHttpConnection(new URL(con.getHeaderField("Location")),
                                                             HttpURLConnection.HTTP_OK,
                                                             new int[0],
                                                             10,
                                                             HTTPRequestMethod.DELETE,
                                                             BatchRestUtils.buildHeaderMap(),
                                                             null);

        assertEquals(200, con2.getResponseCode());

    }

}
