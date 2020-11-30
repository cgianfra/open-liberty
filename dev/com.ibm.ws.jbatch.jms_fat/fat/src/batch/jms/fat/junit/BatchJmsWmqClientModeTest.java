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

import java.util.Collections;
import java.util.Map;
import java.util.Properties;

import javax.batch.runtime.BatchStatus;
import javax.json.JsonArray;
import javax.json.JsonObject;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import batch.fat.util.BatchJobEventsTestHelper;
import batch.fat.util.WmqFatHelper;

import com.ibm.websphere.simplicity.RemoteFile;
import com.ibm.ws.common.internal.encoder.Base64Coder;
import com.ibm.ws.jbatch.test.FatUtils;
import com.ibm.ws.jbatch.test.dbservlet.DbServletClient;
import componenttest.annotation.ExpectedFFDC;
import componenttest.custom.junit.runner.FATRunner;
import componenttest.custom.junit.runner.Mode;
import componenttest.custom.junit.runner.Mode.TestMode;
import componenttest.topology.impl.LibertyServer;
import componenttest.topology.utils.HttpUtils;

/**
 * Test batch dispatcher and executor using WMQ on distributed
 */
@RunWith(FATRunner.class)
@Mode(TestMode.FULL)
public class BatchJmsWmqClientModeTest extends WmqFatHelper {

    /**
     * Shouldn't have to wait more than 10s for messages to appear.
     */
    private static final long LogScrapingTimeout = 10 * 1000;

    // Instance fields
    private final Map<String, String> adminHeaderMap;

    // As defined in the server.xml
    protected final static String ADMIN_NAME = "bob";
    protected final static String ADMIN_PASSWORD = "bobpwd";

    public BatchJmsWmqClientModeTest() {
        adminHeaderMap = Collections.singletonMap("Authorization", "Basic " + Base64Coder.base64Encode(ADMIN_NAME + ":" + ADMIN_PASSWORD));
    }

    /**
     * Startup the servers
     */
    @BeforeClass
    public static void beforeClass() throws Exception {

        FatUtils.checkJava7();

        HttpUtils.trustAllCertificates();

        setUpWMQ();

        server.startServer();
        FatUtils.waitForStartupAndSsl(server);

        // Setup BonusPayout app tables
        new DbServletClient()
                        .setDataSourceJndi("jdbc/BonusPayoutDS")
                        .setDataSourceUser("user", "pass")
                        .setHostAndPort(server.getHostname(), server.getHttpDefaultPort())
                        .loadSql(server.pathToAutoFVTTestFiles + "common/BonusPayout.derby.ddl", "JBATCH", "")
                        .executeUpdate();
    }

    /**
     * Shutdown the server.
     */
    @AfterClass
    public static void afterClass() throws Exception {

        if (server != null && server.isStarted()) {
            server.stopServer(LibertyServer.DISABLE_FAILURE_CHECKING);
        }

        tearDownWMQ();
    }

    /**
     * Submit a job to a rest interface. Expecting BatchJmsDispatcher to put
     * message on queue Expecting BatchJmsEndpointListener to pick up message
     * and execute
     */
    @Test
    public void testSubmitJob_WMQ_CLIENT() throws Exception {

        BatchRestUtils serverUtils = new BatchRestUtils(server);

        Properties jobParameters = new Properties();
        jobParameters.setProperty("dsJNDI", "jdbc/BonusPayoutDS");
        JsonObject jobInstance = serverUtils.submitJob("BonusPayout", "BonusPayoutJob", jobParameters);

        jobInstance = serverUtils.waitForJobInstanceToFinish(jobInstance.getJsonNumber("instanceId").longValue());

        assertTrue(BatchRestUtils.isDone(jobInstance));
        assertEquals(ADMIN_NAME, BatchRestUtils.getSubmitter(jobInstance));

        JsonArray linkArray = jobInstance.getJsonArray("_links");
        assertNotNull(linkArray);

        assertEquals(BatchStatus.COMPLETED, BatchStatus.valueOf(jobInstance.getString("batchStatus")));
    }

    /**
     * Submit a job that will go to FAILED status, and restart
     * 
     * @throws Exception
     */
    @Test
    @ExpectedFFDC({ "java.lang.Exception", "com.ibm.jbatch.container.exception.BatchContainerRuntimeException" })
    public void testJmsRestartJob_WMQ_CLIENT() throws Exception {

        BatchRestUtils serverUtils = new BatchRestUtils(server);

        // set this prop so job is running longer, so we can catch it to stop.
        Properties jobProps = new Properties();
        jobProps.setProperty("force.failure", "true");

        // submit and wait for job to job to FAILED because the force.failure ==
        // true
        JsonObject jobInstance = serverUtils.submitJob("SimpleBatchJob", "test_batchlet_stepCtx", jobProps);

        long instanceId = BatchRestUtils.instanceId(jobInstance);
        jobInstance = serverUtils.waitForJobInstanceToFinish(instanceId);

        // the check for job in final state already done by
        // waitForJobInstanceToFinish
        // so if we get here, job ran
        BatchStatus status = BatchStatus.valueOf(jobInstance.getString("batchStatus"));
        assertEquals(BatchStatus.FAILED, status);

        // restart, this time with force.failure == false
        jobProps.setProperty("force.failure", "false");

        JsonObject jobInstanceRestart = serverUtils.restartJobInstance(instanceId, jobProps);
        assertEquals(instanceId, BatchRestUtils.instanceId(jobInstanceRestart));

        // on the endpoint, wait for message to show up
        String msgToWaitFor = "handleRestartRequest Entry";
        String waitMessage = server.waitForStringInLog(msgToWaitFor, LogScrapingTimeout, server.getMatchingLogFile("trace.log"));
        assertNotNull("Could not find message " + msgToWaitFor, waitMessage);

        Thread.sleep(2 * 1000);

        JsonObject jobInstanceFinal = serverUtils.waitForJobInstanceToFinish(instanceId);
        assertEquals(BatchStatus.COMPLETED, BatchStatus.valueOf(jobInstanceFinal.getString("batchStatus")));
    }

    /**
     * Verify job events were sent and received
     * 
     * @throws Exception
     */
    @Test
    public void testReceivingJobEvents() throws Exception {
        BatchRestUtils serverUtils = new BatchRestUtils(server);

        server.setMarkToEndOfLog(new RemoteFile[] { server.getMatchingLogFile("trace.log"), server.getDefaultLogFile() });

        Properties jobParameters = new Properties();
        jobParameters.setProperty("dsJNDI", "jdbc/BonusPayoutDS");
        JsonObject jobInstance = serverUtils.submitJob("BonusPayout", "BonusPayoutJob", jobParameters);

        for (int i = 0; i < BatchJobEventsTestHelper.msgToWaitDispatcher.length; i++) {
            String uploadMessage = server.waitForStringInLog(BatchJobEventsTestHelper.msgToWaitDispatcher[i], LogScrapingTimeout, server.getMatchingLogFile("trace.log"));
            assertNotNull("Could not find message: " + BatchJobEventsTestHelper.msgToWaitDispatcher[i], uploadMessage);
        }

        jobInstance = serverUtils.waitForJobInstanceToFinish(jobInstance.getJsonNumber("instanceId").longValue());
        assertEquals(BatchStatus.COMPLETED, BatchStatus.valueOf(jobInstance.getString("batchStatus")));

        for (int i = 0; i < BatchJobEventsTestHelper.msgToWaitExecutorCheckpoint.length; i++) {
            String uploadMessage = server.waitForStringInLog(BatchJobEventsTestHelper.msgToWaitExecutorCheckpoint[i], LogScrapingTimeout, server.getMatchingLogFile("trace.log"));
            assertNotNull("Could not find message: " + BatchJobEventsTestHelper.msgToWaitExecutorCheckpoint[i], uploadMessage);
        }
    }
}
