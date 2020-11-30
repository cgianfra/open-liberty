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

import java.util.Collections;
import java.util.Map;

import javax.json.JsonArray;
import javax.json.JsonNumber;
import javax.json.JsonObject;

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

import componenttest.custom.junit.runner.FATRunner;
import componenttest.custom.junit.runner.Mode;
import componenttest.custom.junit.runner.Mode.TestMode;
import componenttest.topology.impl.LibertyServer;
import componenttest.topology.impl.LibertyServerFactory;
import componenttest.topology.utils.HttpUtils;

/**
 * Test batch jms configuration when user use non-default value
 */
@RunWith(FATRunner.class)
@Mode(TestMode.FULL)
public class BatchJmsNonDefaultConfigTest {
    private static final LibertyServer server = LibertyServerFactory.getLibertyServer("BatchJmsSingleServer_fat");
    private static final String testServletHost = server.getHostname();
    private static final int testServletPort = server.getHttpDefaultPort();

    //Instance fields
    private final Map<String, String> adminHeaderMap, headerMap;

    // As defined in the server.xml
    protected final static String ADMIN_NAME = "bob";
    protected final static String ADMIN_PASSWORD = "bobpwd";
    private final static String USER_NAME = "jane";
    private final static String USER_PASSWORD = "janepwd";

    public BatchJmsNonDefaultConfigTest() {
        adminHeaderMap = Collections.singletonMap("Authorization", "Basic " + Base64Coder.base64Encode(ADMIN_NAME + ":" + ADMIN_PASSWORD));
        headerMap = Collections.singletonMap("Authorization", "Basic " + Base64Coder.base64Encode(USER_NAME + ":" + USER_PASSWORD));
    }

    /**
     * Startup the server
     */
    @BeforeClass
    public static void beforeClass() throws Exception {
        HttpUtils.trustAllCertificates();

        server.setServerConfigurationFile("NonDefaultBatchJmsConfig/server.xml");
        server.copyFileToLibertyServerRoot("NonDefaultBatchJmsConfig/bootstrap.properties");

        server.startServer();

        FatUtils.waitForStartupAndSsl(server);
    }

    /**
     * Shutdown the server.
     */
    @AfterClass
    public static void afterClass() throws Exception {
        if (server != null && server.isStarted()) {
            server.stopServer();
        }
    }

    @Ignore
    public void testBatchJmsDispatcherActivated() {

    }

    @Ignore
    public void testBatchJmsEndpointListenerActivated() {

    }

    /**
     * Send Stop as operation type. Expecting the endpoint listener to discard message
     *
     * @throws Exception
     */
    @Test
    public void testJmsListenerNonDefaultConfigDiscardMessageWithWrongOperation() throws Exception {
        String appName = "SimpleBatchJob";
        String operation = "Stop";
        String msgToWaitFor = "CWWKY0201W";

        BatchJmsFatUtils.runInServlet(testServletHost, testServletPort, "sendMapMessageToQueue", appName, operation, "jms%2FtestJmsConnectionFactory", "jms%2FtestJmsQueue");
        String uploadMessage = server.waitForStringInLogUsingMark(msgToWaitFor, server.getMatchingLogFile("trace.log"));
        assertNotNull("Could not find message: " + msgToWaitFor, uploadMessage);
    }

    /**
     * Send a valid type message, expecting the endpoint listener to receive message, but will not process message
     * because we don't want it to process message (by not setting the operation)
     *
     * @throws Exception
     */
    @Test
    public void testJmsListenerNonDefaultConfigReceiveMesage() throws Exception {

        String appName = "SimpleBatchJob";
        String msgToWaitFor = "received message from " + BatchJmsConstants.JBATCH_JMS_LISTENER_CLASS_NAME + " for applicationName: ";

        BatchJmsFatUtils.runInServlet(testServletHost, testServletPort, "sendNoOpMapMessageToQueue", appName, "Start", "jms%2FtestJmsConnectionFactory", "jms%2FtestJmsQueue");
        String uploadMessage = server.waitForStringInLogUsingMark(msgToWaitFor + appName, server.getMatchingLogFile("trace.log"));
        assertNotNull("Could not find message: " + msgToWaitFor + appName, uploadMessage);

        appName = "BonusPayout";
        BatchJmsFatUtils.runInServlet(testServletHost, testServletPort, "sendNoOpMapMessageToQueue", appName, "Start", "jms%2FtestJmsConnectionFactory", "jms%2FtestJmsQueue");
        uploadMessage = server.waitForStringInLogUsingMark(msgToWaitFor + appName, server.getMatchingLogFile("trace.log"));
        assertNotNull("Could not find message " + msgToWaitFor + appName, uploadMessage);
    }

    /**
     * Submit a job to a rest interface.
     * Expecting BatchJmsDispatcher to put message on queue
     * Expecting BatchJmsEndpointListener to pick up message and execute
     */
    @Test
    public void testJmsDispatcherNonDefaultConfigRESTPostSubmitJob() throws Exception {

        BatchRestUtils restUtils = new BatchRestUtils(server);

        JsonObject jsonResponse = restUtils.submitJob("SimpleBatchJob", "test_simplePartition");

        log("testJmsDispatcherNonDefaultConfigRESTPostSubmitJob", "Response: jsonResponse= " + jsonResponse.toString());

        //Wait till the message is consumed and jobName is set
        Thread.sleep(2 * 1000);

        jsonResponse = restUtils.getJobInstance(jsonResponse.getJsonNumber("instanceId").longValue());

        //Verify that the proper mappings are returned
        String jobName = jsonResponse.getString("jobName");
        Assert.assertEquals("test_simplePartition", jobName);
        JsonNumber instanceId = jsonResponse.getJsonNumber("instanceId"); //verifies this is a valid number

        JsonArray linkArray = jsonResponse.getJsonArray("_links");
        assertNotNull(linkArray);

        assertEquals(ADMIN_NAME, jsonResponse.getString("submitter"));
        assertEquals("SimpleBatchJob#SimpleBatchJob.war", jsonResponse.getString("appName"));
    }

    /**
     * helper for simple logging.
     */
    private static void log(String method, String msg) {
        Log.info(BatchJmsNonDefaultConfigTest.class, method, msg);
    }

}
