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

import java.util.List;
import java.util.Properties;

import javax.json.JsonObject;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.ibm.websphere.simplicity.config.JMSActivationSpec;
import com.ibm.websphere.simplicity.config.ServerConfiguration;
import com.ibm.websphere.simplicity.config.WasJmsProperties;

import batch.fat.util.BatchFatUtils;
import componenttest.annotation.AllowedFFDC;
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
public class BatchMultiExecAndDynamicMSTest {

    private static final LibertyServer messageEngineServer = LibertyServerFactory.getLibertyServer("BatchJmsMessageEngine");

    //batch jms endpoint server
    private static final LibertyServer endpointServer = LibertyServerFactory.getLibertyServer("BatchJmsEndpoint");

    //batch jms dispatcher server
    private static final LibertyServer dispatcherServer = LibertyServerFactory.getLibertyServer("BatchJmsDispatcher");

    private static final LibertyServer dispatcherServer2 = LibertyServerFactory.getLibertyServer("BatchJmsDispatcher2");

    // As defined in the server.xml
    protected final static String ADMIN_NAME = "bob";
    protected final static String ADMIN_PASSWORD = "bobpwd";

    @Before
    public void beforeEachTest() throws Exception {

        if (!dispatcherServer.isStarted()) {
            BatchFatUtils.startServer(dispatcherServer);
        }

        if (!dispatcherServer2.isStarted()) {
            BatchFatUtils.startServer(dispatcherServer2);
        }

        if (!endpointServer.isStarted()) {
            BatchFatUtils.startServer(endpointServer);
        }
    }

    /**
     * Startup the servers
     */
    @BeforeClass
    public static void beforeClass() throws Exception {

        HttpUtils.trustAllCertificates();
        String serverStartedMsg = "CWWKF0011I:.*";

        //set port in LibertyServer object because it doesn't return the correct port value if substitution is used in server.xml
        messageEngineServer.setServerConfigurationFile("MultiQueueMessageEngine/server.xml");
        endpointServer.setServerConfigurationFile("MultiExecutorEndpoint/server.xml");

        setports();

        String uploadMessage;

        messageEngineServer.startServer();
        uploadMessage = messageEngineServer.waitForStringInLogUsingMark(serverStartedMsg, messageEngineServer.getMatchingLogFile("trace.log"));
        assertNotNull("Could not find CWWKF0011I (server start info message) in trace.log of " + messageEngineServer.getServerName(), uploadMessage);

        dispatcherServer.startServer();
        uploadMessage = dispatcherServer.waitForStringInLogUsingMark(serverStartedMsg, dispatcherServer.getMatchingLogFile("trace.log"));
        assertNotNull("Could not find CWWKF0011I (server start info message) in trace.log of " + dispatcherServer.getServerName(), uploadMessage);
        assertEquals(Integer.getInteger("batch.dispatcher_1_HTTP_default").intValue(), dispatcherServer.getHttpDefaultPort());

        dispatcherServer2.startServer();
        uploadMessage = dispatcherServer2.waitForStringInLogUsingMark(serverStartedMsg, dispatcherServer2.getMatchingLogFile("trace.log"));
        assertNotNull("Could not find CWWKF0011I (server start info message) in trace.log of " + dispatcherServer2.getServerName(), uploadMessage);
        assertEquals(Integer.getInteger("batch.dispatcher_2_HTTP_default").intValue(), dispatcherServer2.getHttpDefaultPort());

        //clean start endpoint
        endpointServer.startServer();
        uploadMessage = endpointServer.waitForStringInLogUsingMark(serverStartedMsg, endpointServer.getMatchingLogFile("trace.log"));
        assertNotNull("Could not find CWWKF0011I (server start info message) in trace.log of " + endpointServer.getServerName(), uploadMessage);
        assertEquals(Integer.getInteger("batch.endpoint_1_HTTP_default").intValue(), endpointServer.getHttpDefaultPort());

    }

    /**
     * Set the port that test servers used because
     * LibertyServer does not return the actual value used if it is not default.
     */
    private static void setports() {
        dispatcherServer.setHttpDefaultPort(Integer.getInteger("batch.dispatcher_1_HTTP_default").intValue());
        dispatcherServer.setHttpDefaultSecurePort(Integer.getInteger("batch.dispatcher_1_HTTP_default.secure").intValue());

        dispatcherServer2.setHttpDefaultPort(Integer.getInteger("batch.dispatcher_2_HTTP_default").intValue());
        dispatcherServer2.setHttpDefaultSecurePort(Integer.getInteger("batch.dispatcher_2_HTTP_default.secure").intValue());

        endpointServer.setHttpDefaultPort(Integer.getInteger("batch.endpoint_1_HTTP_default").intValue());
        endpointServer.setHttpDefaultSecurePort(Integer.getInteger("batch.endpoint_1_HTTP_default.secure").intValue());

    }

    /**
     * Shutdown the server.
     */
    @AfterClass
    public static void afterClass() throws Exception {

        if (dispatcherServer != null && dispatcherServer.isStarted()) {
            dispatcherServer.stopServer("CWWKG0011W");
        }

        if (endpointServer != null && endpointServer.isStarted()) {
            endpointServer.stopServer("CWWKG0011W");
        }

        if (messageEngineServer != null && messageEngineServer.isStarted()) {
            messageEngineServer.stopServer("CWWKG0011W", "CWSIJ0047E", "CWSIC2019E");
        }

        if (dispatcherServer2 != null && dispatcherServer2.isStarted()) {
            dispatcherServer2.stopServer("CWWKG0011W");

        }
    }

    /**
     * Stop executor
     * submit jobs form Dispatcher1 to queue1 and from Dispatcher2 to queue2
     * start executor
     * wait for all jobs to be completed
     */
    @Test
    @AllowedFFDC({ "javax.resource.spi.UnavailableException",
                   "com.ibm.wsspi.sib.core.exception.SIConnectionDroppedException"
    })
    /* Message should be redelivered when listener ready and it should otherwise work. */
    public void testMultipleExecutorsInOneEndpoint() throws Exception {

        BatchRestUtils dispatcherUtils = new BatchRestUtils(dispatcherServer);
        BatchRestUtils dispatcherUtils2 = new BatchRestUtils(dispatcherServer2);

        BatchFatUtils.stopServer(endpointServer);
        endpointServer.setServerConfigurationFile("MultiExecutorEndpoint/server.xml");

        int numPartitions = 2;
        int numJobs = 12;
        Properties props = new Properties();

        props.put("numPartitions", numPartitions + "");
        props.put("sleep.time.seconds", "1");
        props.put("randomize.sleep.time", "true");

        JsonObject[] jobInstances = new JsonObject[numJobs];

        //Submit jobs
        //Submit one job using dispatcher1 to queue1 and other using dispatcher2 to queue2
        for (int i = 0; i < numJobs; i = i + 2) {
            jobInstances[i] = dispatcherUtils.submitJob("SimpleBatchJob", "test_sleepy_partitioned_batchlet", props);
            jobInstances[i + 1] = dispatcherUtils2.submitJob("SimpleBatchJobCopy", "test_sleepy_partitioned_batchlet", props);
        }

        BatchFatUtils.startServer(endpointServer);

        dispatcherUtils.waitForJobInstanceToFinish(jobInstances[0].getJsonNumber("instanceId").longValue());

        for (int i = 0; i < numJobs; i++) {
            JsonObject jobInstance = dispatcherUtils.waitForJobInstanceToFinish(jobInstances[i].getJsonNumber("instanceId").longValue());
            assertEquals("COMPLETED", jobInstance.getString("batchStatus"));
        }
    }

    /**
     * Stop Executor
     * Submit SimpleBatchJob jobs and SimpleBatchJobCopy jobs (different apps)
     * set executor config with message selector accepting only SimpleBatchJob jobs
     * assert all SimpleBatchJobs are completed successfully
     * assert all SimpleBathchJobCopy jobs are queued and not executed
     * change executor config with message selector accepting SimpleBatchJobCopy jobs
     * assert all SimpleBatchJobCopy jobs are completed successfully
     */
    @Test
    @AllowedFFDC("javax.resource.spi.UnavailableException")
    /* Message should be redelivered when listener ready and it should otherwise work. */
    public void testDynamicallyUpdateMessageSelector() throws Exception {

        BatchRestUtils dispatcherUtils = new BatchRestUtils(dispatcherServer);

        BatchFatUtils.stopServer(endpointServer);

        int numPartitions = 2;
        int numJobs = 12;
        Properties props = new Properties();

        props.put("numPartitions", numPartitions + "");
        props.put("sleep.time.seconds", "1");
        props.put("randomize.sleep.time", "true");

        JsonObject[] jobInstances = new JsonObject[numJobs];

        //Submit jobs
        for (int i = 0; i < numJobs; i = i + 2) {
            jobInstances[i] = dispatcherUtils.submitJob("SimpleBatchJob", "test_sleepy_partitioned_batchlet", props);
            jobInstances[i + 1] = dispatcherUtils.submitJob("SimpleBatchJobCopy", "test_sleepy_partitioned_batchlet", props);
        }

        //Set config to accept only SimpleBatchJob messages
        endpointServer.setServerConfigurationFile("BatchJmsEndpointDynamicMS/server.xml");
        endpointServer.copyFileToLibertyServerRoot("BatchJmsEndpointDynamicMS/import.xml");

        endpointServer.setHttpDefaultSecurePort(Integer.getInteger("batch.endpoint_1_HTTP_default.secure").intValue());
        endpointServer.setHttpDefaultPort(Integer.getInteger("batch.endpoint_1_HTTP_default").intValue());

        BatchFatUtils.startServer(endpointServer);

        //Wait for SimpleBatchJobs to complete since they will be executed
        //because the message selector is listening to SimpleBatchJob messages only
        for (int i = 0; i < numJobs; i += 2) {
            JsonObject jobInstance = dispatcherUtils.waitForJobInstanceToFinish(jobInstances[i].getJsonNumber("instanceId").longValue());
            assertEquals("COMPLETED", jobInstance.getString("batchStatus"));
        }

        //check messages for SimpleBatchJobCopy are still queued and not consumed
        for (int i = 1; i < numJobs; i += 2) {
            JsonObject jobInstance = dispatcherUtils.getJobInstance(jobInstances[i].getJsonNumber("instanceId").longValue());
            assertEquals("JMS_QUEUED", jobInstance.getString("instanceState"));
        }

        //Change config, change message selector to consume SimpleBatchJobCopy messages
        ServerConfiguration config = endpointServer.getServerConfiguration();
        JMSActivationSpec ac = config.getJMSActivationSpecs().get(0);
        List<WasJmsProperties> acProps = ac.getWasJmsProperties();

        for (WasJmsProperties prop : acProps) {
            assertFalse(prop.getValue("messageSelector").contains("SimpleBatchJobCopy"));
            prop.setExtraAttribute("messageSelector", "(com_ibm_ws_batch_applicationName = 'SimpleBatchJobCopy') AND (com_ibm_ws_batch_work_type = 'Job')");
        }
        endpointServer.updateServerConfiguration(config);
        assertNotNull("Didn't find config update message in log", endpointServer.waitForStringInLog("CWWKG001(7|8)I"));

        Thread.sleep(10 * 1000);

        for (int i = 1; i < numJobs; i = i + 2) {
            JsonObject jobInstance = dispatcherUtils.waitForJobInstanceToFinish(jobInstances[i].getJsonNumber("instanceId").longValue());
            assertEquals("COMPLETED", jobInstance.getString("batchStatus"));
        }
    }
}
