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

import java.util.Properties;

import javax.batch.runtime.BatchStatus;
import javax.json.JsonObject;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.ibm.ws.jbatch.test.FatUtils;
import com.ibm.ws.jbatch.test.dbservlet.DbServletClient;

import batch.fat.util.InstanceStateMirrorImage;
import componenttest.annotation.AllowedFFDC;
import componenttest.annotation.ExpectedFFDC;
import componenttest.custom.junit.runner.FATRunner;
import componenttest.custom.junit.runner.Mode;
import componenttest.custom.junit.runner.Mode.TestMode;
import componenttest.topology.impl.LibertyServer;
import componenttest.topology.impl.LibertyServerFactory;
import componenttest.topology.utils.HttpUtils;

// UGLY - too lazy to figure out how to copy the class file

/**
 * This test has 2 endpoint servers, 1 dispatcher, 1 message engine server
 *
 * This test only run in full mode because it is just an extension of the
 * topology of the BatchJmsMultiServerTest.
 *
 */
@RunWith(FATRunner.class)
@Mode(TestMode.FULL)
public class BatchJms2EndpointsTest {
    private static final LibertyServer messageEngineServer = LibertyServerFactory.getLibertyServer("BatchJmsMessageEngine");

    //batch jms endpoint server
    private static final LibertyServer endpointServer = LibertyServerFactory.getLibertyServer("BatchJmsEndpoint");

    //batch jms endpoint server
    private static final LibertyServer endpointServer2 = LibertyServerFactory.getLibertyServer("BatchJmsEndpoint2");

    //batch jms dispatcher server
    private static final LibertyServer dispatcherServer = LibertyServerFactory.getLibertyServer("BatchJmsDispatcher");

    /**
     * Startup the servers
     */
    @BeforeClass
    public static void beforeClass() throws Exception {

        HttpUtils.trustAllCertificates();
        String serverStartedMsg = "CWWKF0011I:.*";

        //set port in LibertyServer object because it doesn't return the correct port value if substitution is used in server.xml
        setports();

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
        FatUtils.waitForLTPA(endpointServer); //because ep has app security enabled
        assertEquals(Integer.getInteger("batch.endpoint_1_HTTP_default").intValue(), endpointServer.getHttpDefaultPort());

        //start second endpoint
        endpointServer2.startServer();
        FatUtils.waitForStartupAndSsl(endpointServer2);
        FatUtils.waitForLTPA(endpointServer2);
        assertEquals(Integer.getInteger("batch.endpoint_2_HTTP_default").intValue(), endpointServer2.getHttpDefaultPort());

        // Setup BonusPayout app tables
        new DbServletClient().setDataSourceJndi("jdbc/BonusPayoutDS").setDataSourceUser("user",
                                                                                        "pass").setHostAndPort(endpointServer2.getHostname(),
                                                                                                               endpointServer2.getHttpDefaultPort()).loadSql(endpointServer2.pathToAutoFVTTestFiles
                                                                                                                                                             + "common/BonusPayout.derby.ddl",
                                                                                                                                                             "JBATCH",
                                                                                                                                                             "").executeUpdate();

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
            dispatcherServer.stopServer("CWSIJ0047E");
        }

        if (endpointServer != null && endpointServer.isStarted()) {
            endpointServer.stopServer("CWSIJ0047E");
        }

        if (endpointServer2 != null && endpointServer2.isStarted()) {
            endpointServer2.stopServer("CWSIJ0047E");
        }

        if (messageEngineServer != null && messageEngineServer.isStarted()) {
            messageEngineServer.stopServer("CWSIJ0047E");
        }
    }

    /**
     * Have 2 endpoint servers, each with different application & message selector
     * Verify each server pick up the right message.
     *
     * @throws Exception
     */
    @Test
    public void testJmsServerPickUpTheCorrectRequest_2Endpoints() throws Exception {
        BatchRestUtils dispatcherUtils = new BatchRestUtils(dispatcherServer);

        JsonObject jobInstance = dispatcherUtils.submitJob("SimpleBatchJob", "test_sleepyBatchlet");
        JsonObject jobInstance2 = dispatcherUtils.waitForJobInstanceToFinish(jobInstance.getJsonNumber("instanceId").longValue());
        assertEquals(BatchStatus.COMPLETED, BatchStatus.valueOf(jobInstance2.getString("batchStatus")));

        Properties jobParameters = new Properties();
        jobParameters.setProperty("dsJNDI", "jdbc/BonusPayoutDS");
        JsonObject jobInstanceBonusPayout = dispatcherUtils.submitJob("BonusPayout", "BonusPayoutJob", jobParameters);
        JsonObject jobInstanceBonusPayout2 = dispatcherUtils.waitForJobInstanceToFinish(jobInstanceBonusPayout.getJsonNumber("instanceId").longValue());
        assertEquals(BatchStatus.COMPLETED, BatchStatus.valueOf(jobInstanceBonusPayout2.getString("batchStatus")));
    }

    /**
     * "jane" is not defined in a batch role on the endpoint2 (server hosting BonusPayout)
     *
     * Verify that listener update job instance state to FAILED
     * and throw JobSecurityException
     */
    @Test
    @ExpectedFFDC({ "javax.batch.operations.JobSecurityException" })
    @AllowedFFDC({ "java.lang.reflect.InvocationTargetException" })
    public void testJmsSecurityRoleNotConfiguredOnEndpoint_2Endpoints() throws Exception {
        BatchRestUtils dispatcherUtils = new BatchRestUtils(dispatcherServer);

        //this method submit job as user "jane"
        JsonObject jobInstance = dispatcherUtils.submitJob("BonusPayout", "BonusPayoutJob", BatchRestUtils.buildHeaderMapJane());

        //wait for this error message on the endpoint
        String msgToWaitFor = "CWWKY0209E:";
        //String uploadMessage = endpointServer2.waitForStringInLogUsingMark(msgToWaitFor, endpointServer2.getMatchingLogFile("trace.log"));
        String uploadMessage = endpointServer2.waitForStringInTraceUsingLastOffset(msgToWaitFor);
        assertNotNull("Could not find message " + msgToWaitFor, uploadMessage);

        long instanceId = BatchRestUtils.instanceId(jobInstance);
        jobInstance = dispatcherUtils.waitForFinalJobInstanceState(instanceId);

        assertEquals(InstanceStateMirrorImage.FAILED.toString(), jobInstance.getString("instanceState"));

    }

    /**
     * "chuck" is batchAdmin on dispatcher, but a batchMonitor on endpoint
     * Stack Dump = javax.batch.operations.JobSecurityException: Current user chuck is not an admin or submitter and is not authorized to submit jobs
     * at com.ibm.jbatch.container.ws.impl.WSBatchAuthServiceImpl.authorizedJobSubmission(WSBatchAuthServiceImpl.java:192)
     * at com.ibm.jbatch.container.ws.impl.WSJobOperatorImpl.start(WSJobOperatorImpl.java:137)
     *
     * @throws Exception
     */
    @Test
    @ExpectedFFDC({ "javax.batch.operations.JobSecurityException" })
    @AllowedFFDC({ "java.lang.reflect.InvocationTargetException" })
    public void testJmsSecurityRoleNotMatchOnEndpoint_2Endpoints() throws Exception {

        BatchRestUtils dispatcherUtils = new BatchRestUtils(dispatcherServer);

        //this method submit job as user "jane"
        JsonObject jobInstance = dispatcherUtils.submitJob("BonusPayout", "BonusPayoutJob", BatchRestUtils.buildHeaderMapChuck());

        //wait for this error message on the endpoint
        String msgToWaitFor = "CWWKY0209E:";
        //String uploadMessage = endpointServer2.waitForStringInLogUsingMark(msgToWaitFor, endpointServer2.getMatchingLogFile("trace.log"));
        //use this wait method because there could be other occurrence of message from other test.
        String uploadMessage = endpointServer2.waitForStringInTraceUsingLastOffset(msgToWaitFor);
        assertNotNull("Could not find message " + msgToWaitFor, uploadMessage);

        long instanceId = BatchRestUtils.instanceId(jobInstance);

        jobInstance = dispatcherUtils.waitForFinalJobInstanceState(instanceId);

        assertEquals(InstanceStateMirrorImage.FAILED.toString(), jobInstance.getString("instanceState"));

    }
}
