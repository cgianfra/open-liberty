/*
 *
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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import javax.batch.runtime.BatchStatus;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.ibm.websphere.simplicity.config.ConfigElementList;
import com.ibm.websphere.simplicity.config.DataSource;
import com.ibm.websphere.simplicity.config.DataSourceProperties;
import com.ibm.websphere.simplicity.config.ServerConfiguration;
import com.ibm.websphere.simplicity.log.Log;
import com.ibm.ws.common.internal.encoder.Base64Coder;
import com.ibm.ws.jbatch.jms.internal.BatchJmsConstants;
import com.ibm.ws.jbatch.test.BatchJmsFatUtils;
import com.ibm.ws.jbatch.test.FatUtils;
import com.ibm.ws.jbatch.test.dbservlet.DbServletClient;

// UGLY - too lazy to figure out how to copy the class file
import batch.fat.util.InstanceStateMirrorImage;
import componenttest.annotation.AllowedFFDC;
import componenttest.annotation.ExpectedFFDC;
import componenttest.custom.junit.runner.FATRunner;
import componenttest.custom.junit.runner.Mode;
import componenttest.custom.junit.runner.Mode.TestMode;
import componenttest.topology.impl.LibertyServer;
import componenttest.topology.impl.LibertyServerFactory;
import componenttest.topology.utils.HttpUtils;
import componenttest.topology.utils.HttpUtils.HTTPRequestMethod;

/**
 * Test the instance state of the job instance
 */
@RunWith(FATRunner.class)
@Mode(TestMode.FULL)
public class BatchJmsInstanceStateTest {
    private static final LibertyServer server = LibertyServerFactory.getLibertyServer("BatchJmsSingleServer_fat");

    private final Map<String, String> adminHeaderMap;

    // As defined in the server.xml
    protected final static String ADMIN_NAME = "bob";
    protected final static String ADMIN_PASSWORD = "bobpwd";

    public BatchJmsInstanceStateTest() {
        adminHeaderMap = Collections.singletonMap("Authorization", "Basic " + Base64Coder.base64Encode(ADMIN_NAME + ":" + ADMIN_PASSWORD));
    }

    @Before
    public void beforeTest() throws Exception {

        server.deleteDirectoryFromLibertyServerRoot("messaging");

        // By deleting the DB we ensure each test gets job instance/execution ids without
        // any leftover side effects from earlier tests.
        server.deleteDirectoryFromLibertyServerRoot("resources/BatchDB");

    }

    /**
     * Shutdown the server.
     *
     * @throws Exception
     */
    @After
    public void afterTest() throws Exception {
        if (server != null && server.isStarted()) {
            server.stopServer(LibertyServer.DISABLE_FAILURE_CHECKING);
        }
    }

    /**
     * Because we have a newly created database, there should be only 1 entry
     * for this submission.
     * The jvm.options will trigger a jms exception
     *
     * @throws Exception
     */
    @Test
    @ExpectedFFDC({ "com.ibm.ws.jbatch.jms.internal.BatchJmsDispatcherException",
                    "javax.jms.JMSException" })
    public void testDispatch_FAILED_state_FailureInJms() throws Exception {

        //start server here because we need a specific jvm.options
        HttpUtils.trustAllCertificates();

        //This server will have its own db
        server.setServerConfigurationFile("DispatchJmsFailureConfig/server.xml");
        server.copyFileToLibertyServerRoot("DispatchJmsFailureConfig/jvm.options");
        server.copyFileToLibertyServerRoot("DispatchJmsFailureConfig/bootstrap.properties");
        server.startServer();

        FatUtils.waitForStartupAndSsl(server);

        Map<String, String> newMap = new HashMap<String, String>();
        newMap.putAll(adminHeaderMap);
        newMap.put("Content-Type", "text");

        JsonObjectBuilder jsonBuilder = Json.createObjectBuilder();
        jsonBuilder.add("applicationName", "SimpleBatchJob");
        jsonBuilder.add("jobXMLName", "test_batchlet_stepCtx");
        jsonBuilder.add("jobParameters", BatchJmsFatUtils.buildJsonObjectFromMap(new Properties()));

        JsonObject jsonObject = jsonBuilder.build();

        log("testDispatch_FAILED_state_FailureInJms", "Request: jsonObject= " + jsonObject.toString());

        InputStream input = new ByteArrayInputStream(jsonObject.toString().getBytes("UTF-8"));
        HttpURLConnection con = BatchJmsFatUtils.getConnection("/ibm/api/batch/jobinstances", HttpURLConnection.HTTP_INTERNAL_ERROR, HTTPRequestMethod.POST, input, newMap);
        BufferedReader br = HttpUtils.getErrorStream(con);
        String body = org.apache.commons.io.IOUtils.toString(br);
        br.close();

        //The outer exception is BatchJmsDispatcherException
        Assert.assertTrue("Actual:" + body,
                          body.contains("javax.jms.JMSException"));

        //the instance state in db should be FAILED
        String queryInstance = "SELECT INSTANCESTATE FROM JBATCH.JOBINSTANCE WHERE instanceState = " + InstanceStateMirrorImage.FAILED.ordinal();
        String response = executeSql(server, "jdbc/batch", queryInstance);

        log("testDispatch_FAILED_state_FailureInJms", "query: " + queryInstance + ", response= " + response);

        assertTrue("Instance state FAILED is not found in database, response was: " + response,
                   response.contains(Integer.toString(InstanceStateMirrorImage.FAILED.ordinal())));
    }

    /**
     * This test simulate failure in db update that in the tran (jms queue, update state to jms_queue)
     * The jvm.options will trigger a persistence exception
     *
     * @throws Exception
     */
    @Test
    @ExpectedFFDC({ "com.ibm.ws.jbatch.jms.internal.BatchJmsDispatcherException",
                    "com.ibm.jbatch.container.exception.PersistenceException" })
    public void testDispatch_FAILED_state_FailureAfterDbUpdate() throws Exception {
        String method = "testDispatch_FAILED_state_FailureAfterDbUpdate";
        //start server here because we need a specific jvm.options
        HttpUtils.trustAllCertificates();

        //This server will have its own db
        //The jvm.options will trigger a persistence exception
        server.setServerConfigurationFile("DispatchDBFailureConfig/server.xml");
        server.copyFileToLibertyServerRoot("DispatchDBFailureConfig/jvm.options");
        server.copyFileToLibertyServerRoot("DispatchDBFailureConfig/bootstrap.properties");
        server.startServer();

        FatUtils.waitForStartupAndSsl(server);

        Map<String, String> newMap = new HashMap<String, String>();
        newMap.putAll(adminHeaderMap);
        newMap.put("Content-Type", "text");

        JsonObjectBuilder jsonBuilder = Json.createObjectBuilder();
        jsonBuilder.add("applicationName", "SimpleBatchJob");
        jsonBuilder.add("jobXMLName", "test_batchlet_stepCtx");
        jsonBuilder.add("jobParameters", BatchJmsFatUtils.buildJsonObjectFromMap(new Properties()));

        JsonObject jsonObject = jsonBuilder.build();

        log(method, "Request: jsonObject= " + jsonObject.toString());

        InputStream input = new ByteArrayInputStream(jsonObject.toString().getBytes("UTF-8"));
        HttpURLConnection con = BatchJmsFatUtils.getConnection("/ibm/api/batch/jobinstances", HttpURLConnection.HTTP_INTERNAL_ERROR, HTTPRequestMethod.POST, input, newMap);
        BufferedReader br = HttpUtils.getErrorStream(con);
        String body = org.apache.commons.io.IOUtils.toString(br);
        br.close();

        Assert.assertTrue("Actual:" + body,
                          body.contains("com.ibm.jbatch.container.exception.PersistenceException"));

        //the instance state in db should be FAILED
        String queryInstance = "SELECT INSTANCESTATE FROM JBATCH.JOBINSTANCE WHERE instanceState = " + InstanceStateMirrorImage.FAILED.ordinal();
        String response = executeSql(server, "jdbc/batch", queryInstance);

        log(method, "query: " + queryInstance + ", response= " + response);

        assertTrue("Instance state FAILED is not found in database, response was: " + response,
                   response.contains(Integer.toString(InstanceStateMirrorImage.FAILED.ordinal())));
    }

    /**
     * Bring up a server that just have dispatcher.
     * Submit a job.
     * Verify instance state = JMS_QUEUED
     *
     * @throws Exception
     */
    @Test
    public void testDispatch_JMS_QUEUED_state() throws Exception {

        //start server here because we need a specific jvm.options
        HttpUtils.trustAllCertificates();

        //This server will have its own db
        server.setServerConfigurationFile("DispatchConfig_JMS_QUEUED/server.xml");
        server.copyFileToLibertyServerRoot("DispatchConfig_JMS_QUEUED/bootstrap.properties");
        //make sure there no left over jvm.options file from previous test
        server.deleteFileFromLibertyServerRoot("jvm.options");
        server.startServer();

        FatUtils.waitForStartupAndSsl(server);

        BatchRestUtils dispatcherUtils = new BatchRestUtils(server);
        JsonObject jobInstance = dispatcherUtils.submitJob("SimpleBatchJob", "test_batchlet_stepCtx");

        //the instance state in db should be JMS_QUEUED
        long instanceId = BatchRestUtils.instanceId(jobInstance);
        String queryInstance = "SELECT JOBINSTANCEID,INSTANCESTATE FROM JBATCH.JOBINSTANCE WHERE JOBINSTANCEID = " + instanceId + " AND instanceState = "
                               + InstanceStateMirrorImage.JMS_QUEUED.ordinal();
        String response = executeSql(server, "jdbc/batch", queryInstance);

        assertTrue("Instance state JMS_QUEUED is not found in database, response was: " + response,
                   response.contains(instanceId + "|" + InstanceStateMirrorImage.JMS_QUEUED.ordinal()));
    }

    /**
     * Start a server with jmv.options file set to trigger a jms exception in endpoint listener
     * path. Submit a job.
     *
     * Verify:
     * state = FAILED
     * Message id CWWKY0208E in log
     *
     * @throws Exception
     */
    @Test
    @ExpectedFFDC({ "javax.jms.JMSException" })
    public void testEndpoint_FAILED_FailureInJms() throws Exception {

        //start server here because we need a specific jvm.options
        HttpUtils.trustAllCertificates();

        //This server will have its own db
        server.setServerConfigurationFile("EndpointJmsFailureConfig/server.xml");
        server.copyFileToLibertyServerRoot("EndpointJmsFailureConfig/bootstrap.properties");
        server.copyFileToLibertyServerRoot("EndpointJmsFailureConfig/jvm.options");

        server.startServer();

        FatUtils.waitForStartupAndSsl(server);

        BatchRestUtils dispatcherUtils = new BatchRestUtils(server);

        JsonObject jobInstance = dispatcherUtils.submitJob("SimpleBatchJob", "test_batchlet_stepCtx");

        // wait for the endpoint picks up the message.
        String msgToWaitFor = "received message from " + BatchJmsConstants.JBATCH_JMS_LISTENER_CLASS_NAME + " for applicationName: " + "SimpleBatchJob";
        String uploadMessage = server.waitForStringInLogUsingMark(msgToWaitFor, server.getMatchingLogFile("trace.log"));
        assertNotNull("Could not find message: " + msgToWaitFor, uploadMessage);

        long instanceId = BatchRestUtils.instanceId(jobInstance);
        dispatcherUtils.waitForJobInstanceToFinish(instanceId); // Failure happens on separate executor MDB thread so wait for DB update to complete

        //the instance state in db should be FAILED
        String queryInstance = "SELECT JOBINSTANCEID,INSTANCESTATE FROM JBATCH.JOBINSTANCE  WHERE JOBINSTANCEID = " + instanceId + " AND instanceState = "
                               + InstanceStateMirrorImage.FAILED.ordinal();
        String response = executeSql(server, "jdbc/batch", queryInstance);

        assertTrue("Instance state FAILED is not found in database, response was: " + response,
                   response.contains(instanceId + "|" + InstanceStateMirrorImage.FAILED.ordinal()));

        //verify CWWKY0208E in log
        StringBuffer regexToLookFor = new StringBuffer("CWWKY0208E:");
        List<String> result = server.findStringsInTrace(regexToLookFor.toString());
        assertFalse("Could not find string: " + regexToLookFor.toString(), result.isEmpty());
    }

    /**
     * Start a server with jvm.options file set to trigger a persistence exception in endpoint listener
     * path.
     * 
     * Submit a job when the PersistenceException is triggered, it results in a JobInstanceNotQueuedException
     * and the message is not redelivered.
     *
     * @throws Exception
     */
    @Test
    @AllowedFFDC({ "javax.batch.operations.NoSuchJobInstanceException",
        "com.ibm.jbatch.container.ws.JobInstanceNotQueuedException"})
    @ExpectedFFDC({ "com.ibm.jbatch.container.exception.PersistenceException",
                    "java.lang.reflect.UndeclaredThrowableException"})
    public void testEndpoint_FailureInDbUpdate_MsgNotRedelivered() throws Exception {

        String method = "testEndpoint_COMPLETED_state_FailureInDbUpdate";
        //start server here because we need a specific jvm.options
        HttpUtils.trustAllCertificates();

        //This server will have its own db
        server.setServerConfigurationFile("EndpointDbFailureConfig/server.xml");
        server.copyFileToLibertyServerRoot("EndpointDbFailureConfig/bootstrap.properties");
        server.copyFileToLibertyServerRoot("EndpointDbFailureConfig/jvm.options");

        server.startServer();

        FatUtils.waitForStartupAndSsl(server);

        BatchRestUtils dispatcherUtils = new BatchRestUtils(server);
        JsonObject jobInstance = dispatcherUtils.submitJob("SimpleBatchJob", "test_batchlet_stepCtx");
        
        //com.ibm.jbatch.container.ws.JobInstanceNotQueuedException in the ExpectedFFDCs at this point
    }

    /**
     * Expecting the ep to throw exception when starting the job because it can not find the app
     *
     * @throws Exception
     */
    @Test
    @ExpectedFFDC({ "com.ibm.ws.jbatch.rest.bridge.BatchContainerAppNotFoundException",
                    "java.lang.IllegalStateException" })
    @AllowedFFDC({ "java.lang.reflect.InvocationTargetException" })
    public void testEndpoint_FAILED_FailureInInvokingApp() throws Exception {

        //start server here because we need a specific jvm.options
        HttpUtils.trustAllCertificates();

        //This server will have its own db
        server.setServerConfigurationFile("EndpointJmsFailureConfig/server.xml");
        server.copyFileToLibertyServerRoot("EndpointJmsFailureConfig/bootstrap.properties");

        server.deleteFileFromLibertyServerRoot("jvm.options");

        server.startServer();

        FatUtils.waitForStartupAndSsl(server);

        BatchRestUtils dispatcherUtils = new BatchRestUtils(server);

        JsonObject jobInstance = dispatcherUtils.submitJob("NonExistingApp", "test_batchlet_stepCtx");

        // wait for the endpoint picks up the message.
        String msgToWaitFor = "received message from " + BatchJmsConstants.JBATCH_JMS_LISTENER_CLASS_NAME + " for applicationName: " + "NonExistingApp";
        String uploadMessage = server.waitForStringInLogUsingMark(msgToWaitFor, server.getMatchingLogFile("trace.log"));
        assertNotNull("Could not find message: " + msgToWaitFor, uploadMessage);

        //wait for this message - this is indicate that app not found exception was thrown.
        msgToWaitFor = "CWWKY0209E";
        uploadMessage = server.waitForStringInLogUsingMark(msgToWaitFor, server.getMatchingLogFile("trace.log"));
        assertNotNull("Could not find message: " + msgToWaitFor, uploadMessage);

        long instanceId = BatchRestUtils.instanceId(jobInstance);
        dispatcherUtils.waitForJobInstanceToFinish(instanceId); // Failure happens on separate executor MDB thread so wait for DB update to complete

        //the instance state and batchstatus in db should be FAILED
        String queryInstance = "SELECT JOBINSTANCEID,INSTANCESTATE, BATCHSTATUS FROM JBATCH.JOBINSTANCE  WHERE JOBINSTANCEID = " + instanceId + " AND instanceState = "
                               + InstanceStateMirrorImage.FAILED.ordinal();
        String response = executeSql(server, "jdbc/batch", queryInstance);

        assertTrue("Instance state FAILED is not found in database, response was: " + response,
                   response.contains(instanceId + "|" + InstanceStateMirrorImage.FAILED.ordinal() + "|" + BatchStatus.FAILED.ordinal()));

    }

    /**
     * helper for simple logging.
     */
    private static void log(String method, String msg) {
        Log.info(BatchJmsInstanceStateTest.class, method, msg);
    }

    /**
     * Run sql directly to db
     *
     * @param server
     * @param dataSourceJndi
     * @param sql
     * @return
     * @throws Exception
     */
    public static String executeSql(LibertyServer server, String dataSourceJndi, String sql) throws Exception {

        String userName = "user";
        String password = "pass";

        ServerConfiguration configuration = server.getServerConfiguration();
        ConfigElementList<DataSource> dataSourcesList = configuration.getDataSources();
        Iterator<DataSource> dataSourcesListIterator = dataSourcesList.iterator();

        while (dataSourcesListIterator.hasNext()) {
            DataSource dataSource = dataSourcesListIterator.next();

            if (dataSource.getJndiName().equals(dataSourceJndi)) {
                Set<DataSourceProperties> dataSourcePropertiesList = dataSource.getDataSourceProperties();
                Iterator<DataSourceProperties> dataSourcePropertiesListIterator = dataSourcePropertiesList.iterator();

                while (dataSourcePropertiesListIterator.hasNext()) {
                    DataSourceProperties dataSourceProperties = dataSourcePropertiesListIterator.next();
                    userName = dataSourceProperties.getUser();
                    password = dataSourceProperties.getPassword();
                    break;
                }
            }

            if (!userName.equals("user"))
                break;
        }

        return new DbServletClient().setDataSourceJndi(dataSourceJndi).setDataSourceUser(userName, password).setHostAndPort(server.getHostname(),
                                                                                                                            server.getHttpDefaultPort()).setSql(sql).executeQuery();
    }
}
