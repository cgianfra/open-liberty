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
import static org.junit.Assert.assertTrue;

import java.net.HttpURLConnection;
import java.net.URL;

import javax.batch.runtime.BatchStatus;
import javax.batch.runtime.JobInstance;
import javax.json.JsonObject;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.ibm.websphere.simplicity.ProgramOutput;
import com.ibm.ws.jbatch.test.BatchManagerCliUtils;
import com.ibm.ws.jbatch.test.FatUtils;

import componenttest.annotation.AllowedFFDC;
import componenttest.custom.junit.runner.FATRunner;
import componenttest.custom.junit.runner.Mode;
import componenttest.custom.junit.runner.Mode.TestMode;
import componenttest.topology.impl.LibertyServer;
import componenttest.topology.impl.LibertyServerFactory;
import componenttest.topology.utils.HttpUtils;
import componenttest.topology.utils.HttpUtils.HTTPRequestMethod;

/**
 * This test has 1 endpoint server, 1 dispatcher server, 1 message engine server
 *
 */
@RunWith(FATRunner.class)
@Mode(TestMode.FULL)
public class BatchJmsZeroExecutionsTest {

    private static final LibertyServer messageEngineServer = LibertyServerFactory.getLibertyServer("BatchJmsMessageEngine");

    //batch jms endpoint server
    private static final LibertyServer endpointServer = LibertyServerFactory.getLibertyServer("BatchJmsEndpoint");

    //batch jms dispatcher server
    private static final LibertyServer dispatcherServer = LibertyServerFactory.getLibertyServer("BatchJmsDispatcher");

    //private static final String executorHost = endpointServer.getHostname();
    //private static int executorPort = -1;

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

        //String serverStartedMsg = "CWWKF0011I:.*";

        setports();

        //executorPort = endpointServer.getHttpDefaultPort();

        //start server with Message Engine first
        messageEngineServer.startServer();
        FatUtils.waitForSmarterPlanet(messageEngineServer);

        //start dispatcher
        dispatcherServer.startServer();
        FatUtils.waitForStartupAndSsl(dispatcherServer);

        assertEquals(Integer.getInteger("batch.dispatcher_1_HTTP_default").intValue(), dispatcherServer.getHttpDefaultPort());

        endpointServer.copyFileToLibertyServerRoot("DelayExecutionConfig/jvm.options");
        //start endpoint
        endpointServer.startServer();

        FatUtils.waitForStartupAndSsl(endpointServer);
        //FatUtils.waitForLTPA(endpointServer); //because endpoint has app security enabled

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

        endpointServer.deleteFileFromLibertyServerRoot("jvm.options");

        if (endpointServer != null && endpointServer.isStarted()) {
            endpointServer.stopServer();
        }

        if (messageEngineServer != null && messageEngineServer.isStarted()) {
            messageEngineServer.stopServer();
        }
    }

    /**
     * Test sending a STOP request to the dispatcher for an instance with no executions.
     */

    @Test
    @AllowedFFDC({ "java.lang.reflect.InvocationTargetException", "javax.batch.operations.JobStartException" })
    public void testStopJobInstance() throws Exception {

        BatchRestUtils dispatcherUtils = new BatchRestUtils(dispatcherServer);

        JsonObject jobInstance = dispatcherUtils.submitJob("SimpleBatchJob", "test_sleepyBatchlet");

        //Ensure the job has been taken off of the queue and is in the local dispatcher.
        String msgToWaitFor = "handleStartRequest Entry";
        String waitMessage = endpointServer.waitForStringInTraceUsingLastOffset(msgToWaitFor, LogScrapingTimeout);

        //Instead of asserting not null will continue on the assumption that we were able to stop the job at a later time
        //assertNotNull("Could not find message " + msgToWaitFor, waitMessage);

        // Now try to stop it via the dispatcher.
        int[] state = { HttpURLConnection.HTTP_MOVED_TEMP };
        HttpURLConnection con = HttpUtils.getHttpConnection(dispatcherUtils.buildURL("/ibm/api/batch/jobinstances/" + jobInstance.getJsonNumber("instanceId").longValue()
                                                                                     + "?action=stop"),
                                                            HttpURLConnection.HTTP_OK,
                                                            state,
                                                            10,
                                                            HTTPRequestMethod.PUT,
                                                            BatchRestUtils.buildHeaderMap(),
                                                            null);
        long instanceId = jobInstance.getJsonNumber("instanceId").longValue();
        if (con.getHeaderField("Location") != null) {
            BatchRestUtils endpointUtils = new BatchRestUtils(endpointServer);
            String expectedLocation = endpointUtils.buildURLUsingIPAddr("/ibm/api/batch/jobinstances/" + jobInstance.getJsonNumber("instanceId").longValue()
                                                                        + "?action=stop").toString();

            assertEquals(expectedLocation, con.getHeaderField("Location"));

            // Forward STOP to redirected url. This should work (response 200 or 400 if the job already finished)

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
        } else {
            jobInstance = dispatcherUtils.waitForJobInstanceToFinish(instanceId);
            BatchStatus status = BatchStatus.valueOf(jobInstance.getString("batchStatus"));
            assertTrue(BatchStatus.STOPPED.equals(status));
        }

    }

    /**
     * Test sending a STOP request to the dispatcher for an instance with
     * no started executions and restart with --reusePreviousParams option puts the instance
     * into a COMPLETED state.
     */

    @Test
    @AllowedFFDC({ "java.lang.reflect.InvocationTargetException", "javax.batch.operations.JobStartException",
                   "com.ibm.jbatch.container.exception.PersistenceException", "com.ibm.jbatch.container.ws.JobInstanceNotQueuedException",
                   "java.lang.reflect.UndeclaredThrowableException" })
    public void testRestartJobInstanceReuseParams() throws Exception {

        final String appName = "SimpleBatchJob";
        final String jobXMLName = "test_sleepyBatchlet";
        final String user = "bob";
        final String pass = "bobpwd";

        BatchRestUtils dispatcherRestUtils = new BatchRestUtils(dispatcherServer);
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

        //Ensure the job has been taken off of the queue and is in the local dispatcher.
        String msgToWaitFor = "handleStartRequest Entry";
        String waitMessage = endpointServer.waitForStringInTraceUsingLastOffset(msgToWaitFor, LogScrapingTimeout);

        //Instead of asserting not null will continue on the assumption that we were able to stop the job at a later time
        //assertNotNull("Could not find message " + msgToWaitFor, waitMessage);

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

        JsonObject instance = dispatcherRestUtils.waitForJobInstanceToFinish(jobInstance.getInstanceId(), 360);
        BatchStatus status = BatchStatus.valueOf(instance.getString("batchStatus"));

        //Do rest of test normally if status is STOPPED
        if (BatchStatus.STOPPED.equals(status)) {

            HttpUtils.getHttpConnection(dispatcherRestUtils.buildURL("/ibm/api/batch/jobinstances/" + jobInstance.getInstanceId() + "?action=restart&reusePreviousParams=true"),
                                        HttpURLConnection.HTTP_OK,
                                        new int[0],
                                        10,
                                        HTTPRequestMethod.PUT,
                                        BatchRestUtils.buildHeaderMap(),
                                        null);

            String msg2ToWaitFor = "handleRestartRequest Entry";
            String wait2Message = endpointServer.waitForStringInLog(msgToWaitFor, LogScrapingTimeout, endpointServer.getMatchingLogFile("trace.log"));

            //Instead of asserting not null will continue on the assumption that we were able to stop the job at a later time
            //assertNotNull("Could not find message " + msgToWaitFor, waitMessage);

            Thread.sleep(2 * 1000);

            JsonObject jsonInstance = dispatcherRestUtils.getJobInstance(jobInstance.getInstanceId());
            long jobExecutionId = dispatcherRestUtils.getMostRecentExecutionIdFromInstance(jsonInstance);
            JsonObject jobExecutionFinal = dispatcherRestUtils.getFinalJobExecution(jobExecutionId);
            assertEquals(BatchStatus.COMPLETED, BatchStatus.valueOf(jobExecutionFinal.getString("batchStatus")));
            JsonObject jobInstanceFinal = dispatcherRestUtils.waitForJobInstanceToFinish(jobInstance.getInstanceId());
            assertEquals(BatchStatus.COMPLETED, BatchStatus.valueOf(jobInstanceFinal.getString("batchStatus")));
        } else { //If not stopped ensure job completed
            assertTrue(BatchStatus.COMPLETED.equals(status));
        }
    }

    /**
     * Submit a job that will go to FAILED status, and restart it
     *
     * @throws Exception
     */

    @Test
    @AllowedFFDC({ "java.lang.reflect.InvocationTargetException",
                   "javax.batch.operations.JobStartException",
                   "com.ibm.jbatch.container.ws.JobInstanceNotQueuedException" })
    public void testRestartJobInstance() throws Exception {

        final String appName = "SimpleBatchJob";
        final String jobXMLName = "test_sleepyBatchlet";
        final String user = "bob";
        final String pass = "bobpwd";

        BatchRestUtils dispatcherRestUtils = new BatchRestUtils(dispatcherServer);

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

        //Ensure the job has been taken off of the queue and is in the local dispatcher.
        String msgToWaitFor = "handleStartRequest Entry";
        String waitMessage = endpointServer.waitForStringInTraceUsingLastOffset(msgToWaitFor, LogScrapingTimeout);

        //Instead of asserting not null will continue on the assumption that we were able to stop the job at a later time
        //assertNotNull("Could not find message " + msgToWaitFor, waitMessage);

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

        JsonObject instance = dispatcherRestUtils.waitForJobInstanceToFinish(jobInstance.getInstanceId(), 360);
        BatchStatus status = BatchStatus.valueOf(instance.getString("batchStatus"));

        //Do rest of test normally if status is STOPPED
        if (BatchStatus.STOPPED.equals(status)) {

            // Now restart the job...
            po = dispatcherUtils.restartJob(new String[] { "restart",
                                                           "--batchManager=" + dispatcherUtils.getHostAndPort(),
                                                           "--user=" + user,
                                                           "--password=" + pass,
                                                           "--jobInstanceId=" + jobInstance.getInstanceId(),
                                                           "--trustSslCertificates" });

            assertEquals(0, po.getReturnCode());

            String msg2ToWaitFor = "handleRestartRequest Entry";
            String wait2Message = endpointServer.waitForStringInLog(msg2ToWaitFor, LogScrapingTimeout, endpointServer.getMatchingLogFile("trace.log"));

            //Instead of asserting not null will continue on the assumption that we were able to stop the job at a later time
            //assertNotNull("Could not find message " + msgToWaitFor, waitMessage);

            Thread.sleep(2 * 1000);

            JsonObject jsonInstance = dispatcherRestUtils.getJobInstance(jobInstance.getInstanceId());
            long jobExecutionId = dispatcherRestUtils.getMostRecentExecutionIdFromInstance(jsonInstance);
            JsonObject jobExecutionFinal = dispatcherRestUtils.getFinalJobExecution(jobExecutionId);
            assertEquals(BatchStatus.COMPLETED, BatchStatus.valueOf(jobExecutionFinal.getString("batchStatus")));
            JsonObject jobInstanceFinal = dispatcherRestUtils.waitForJobInstanceToFinish(jobInstance.getInstanceId());
            assertEquals(BatchStatus.COMPLETED, BatchStatus.valueOf(jobInstanceFinal.getString("batchStatus")));
        } else { //If not stopped ensure job completed
            assertTrue(BatchStatus.COMPLETED.equals(status));
        }
    }

}
