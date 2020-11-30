/*
 * IBM Confidential
 *
 * OCO Source Materials
 *
 * WLP Copyright IBM Corp. 2016
 *
 * The source code for this program is not published or otherwise divested
 * of its trade secrets, irrespective of what has been deposited with the
 * U.S. Copyright Office.
 */
package batch.jms.fat.junit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.nio.file.Paths;
import java.util.Properties;

import javax.json.JsonObject;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.ibm.ws.jbatch.test.dbservlet.DbServletClient;

import batch.fat.util.BatchFatUtils;
import componenttest.annotation.AllowedFFDC;
import componenttest.custom.junit.runner.FATRunner;
import componenttest.rules.repeater.JakartaEE9Action;
import componenttest.topology.impl.LibertyServer;
import componenttest.topology.impl.LibertyServerFactory;
import componenttest.topology.utils.HttpUtils;

/**
 *
 */
@RunWith(FATRunner.class)
public class BatchPartitionOldDBTablesTest {

    private static final LibertyServer messageEngineServer = LibertyServerFactory.getLibertyServer("BatchJmsMessageEngine");
    //batch jms end-point server
    //    This should run the jobs
    private static final LibertyServer endpointServer = LibertyServerFactory.getLibertyServer("BatchJmsEndpoint");

    //batch jms end-point server
    //This should run the partitions
    private static final LibertyServer endpointServer2 = LibertyServerFactory.getLibertyServer("BatchJmsEndpoint2");

    //batch jms dispatcher server
    //This should run as dispatcher as well as end-point for all top-level jobs
    private static final LibertyServer dispatcherServer = LibertyServerFactory.getLibertyServer("BatchJmsDispatcher");

    // As defined in the server.xml
    protected final static String ADMIN_NAME = "bob";
    protected final static String ADMIN_PASSWORD = "bobpwd";

    /**
     * Startup the servers
     */
    @BeforeClass
    public static void beforeClass() throws Exception {

        HttpUtils.trustAllCertificates();
        String serverStartedMsg = "CWWKF0011I:.*";

        //set port in LibertyServer object because it doesn't return the correct port value if substitution is used in server.xml

        endpointServer.setServerConfigurationFile("ThrottlingEndpoint/server.xml");
        endpointServer2.setServerConfigurationFile("ThrottlingEndpoint2/server.xml");

        dispatcherServer.setHttpDefaultPort(Integer.getInteger("batch.dispatcher_1_HTTP_default").intValue());
        dispatcherServer.setHttpDefaultSecurePort(Integer.getInteger("batch.dispatcher_1_HTTP_default.secure").intValue());

        endpointServer.setHttpDefaultPort(Integer.getInteger("batch.endpoint_1_HTTP_default").intValue());
        endpointServer.setHttpDefaultSecurePort(Integer.getInteger("batch.endpoint_1_HTTP_default.secure").intValue());

        endpointServer2.setHttpDefaultPort(Integer.getInteger("batch.endpoint_2_HTTP_default").intValue());
        endpointServer2.setHttpDefaultSecurePort(Integer.getInteger("batch.endpoint_2_HTTP_default.secure").intValue());
        
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
            JakartaEE9Action.transformApp(Paths.get(endpointServer2.getServerRoot(), "dropins", "BonusPayout.war"));
            JakartaEE9Action.transformApp(Paths.get(endpointServer2.getServerRoot(), "dropins", "DbServletApp.war"));
            JakartaEE9Action.transformApp(Paths.get(endpointServer2.getServerRoot(), "dropins", "SimpleBatchJob.war"));
        }

        //clean start server with Message Engine first
        messageEngineServer.startServer();
        String uploadMessage = messageEngineServer.waitForStringInLogUsingMark(serverStartedMsg, messageEngineServer.getMatchingLogFile("trace.log"));
        assertNotNull("Could not find CWWKF0011I (server start info message) in trace.log of " + messageEngineServer.getServerName(), uploadMessage);

        BatchRestUtils.changeDatabase(dispatcherServer);
        BatchRestUtils.changeDatabase(endpointServer);
        BatchRestUtils.changeDatabase(endpointServer2);

    }

    /**
     * Shutdown the server.
     */
    @AfterClass
    public static void afterClass() throws Exception {
        if (endpointServer != null && endpointServer.isStarted()) {
            endpointServer.stopServer();
        }

        if (endpointServer2 != null && endpointServer2.isStarted()) {
            endpointServer2.stopServer();
        }

        if (dispatcherServer != null && dispatcherServer.isStarted()) {
            dispatcherServer.stopServer();
        }

        if (messageEngineServer != null && messageEngineServer.isStarted()) {
            messageEngineServer.stopServer(LibertyServer.DISABLE_FAILURE_CHECKING);
        }

    }

    /**
     * Stop Endpoint and Endpoint2
     * drop RemotabalePartition Table if any
     * start Endpoint and Endpoint2 with createTables="false"
     * verify job can run without RemotablePartition table
     * restart servers again, set to original config
     * Expect endpoint to autocreate RemotablePartition table
     */
    @Test
    @AllowedFFDC({ "javax.resource.spi.UnavailableException",
                   "javax.resource.spi.ResourceAllocationException",
                   "javax.resource.ResourceException",
                   "javax.jms.JMSException",
                   "com.ibm.wsspi.sib.core.exception.SIConnectionDroppedException",
                   "com.ibm.websphere.sib.exception.SIResourceException",
                   "javax.resource.spi.ResourceAdapterInternalException",
                   "com.ibm.ws.sib.jfapchannel.JFapConnectionBrokenException",
                   "com.ibm.jbatch.container.exception.PersistenceException",
                   "com.ibm.jbatch.container.ws.JobInstanceNotQueuedException",
                   "java.lang.reflect.UndeclaredThrowableException",
                   "com.ibm.ws.sib.mfp.MessageEncodeFailedException",
                   "java.lang.IllegalStateException",
                   "javax.resource.ResourceException" })
    public void testRemotablePartitionOlderTables() throws Exception {

        BatchFatUtils.startServer(dispatcherServer);
        assertEquals(Integer.getInteger("batch.dispatcher_1_HTTP_default").intValue(), dispatcherServer.getHttpDefaultPort());

        BatchRestUtils dispatcherUtils = new BatchRestUtils(dispatcherServer);

        dispatcherUtils.getBatchApi("/jobinstances");

        String schema = dispatcherUtils.getDatabaseSchema();
        String query = " DROP TABLE " + schema + ".REMOTABLEPARTITION";

        String result = executeSqlUpdate(dispatcherServer, "jdbc/batch", query);

        BatchFatUtils.stopServer(dispatcherServer);

        endpointServer.saveServerConfiguration();
        endpointServer2.saveServerConfiguration();
        dispatcherServer.saveServerConfiguration();

        String preSavedSchema = dispatcherUtils.getDatabaseSchema();

        endpointServer.setServerConfigurationFile("ThrottlingEndpointAutoCreateOff/server.xml");
        endpointServer2.setServerConfigurationFile("ThrottlingEndpointAutoCreateOff2/server.xml");
        dispatcherServer.setServerConfigurationFile("DispatcherAutoCreateOff/server.xml");

        BatchRestUtils.changeDatabase(dispatcherServer);
        BatchRestUtils.changeDatabase(endpointServer);
        BatchRestUtils.changeDatabase(endpointServer2);

        BatchFatUtils.startServer(dispatcherServer);

        int numPartitions = 2;

        //Submit job, RemotablePartition table won't exist
        Properties props = new Properties();

        props.put("numPartitions", numPartitions + "");
        props.put("sleep.time.seconds", "3");
        props.put("randomize.sleep.time", "true");

        JsonObject jobInstance = dispatcherUtils.submitJob("SimpleBatchJob", "test_sleepy_partitioned_batchlet", props);

        BatchFatUtils.startServer(endpointServer);
        BatchFatUtils.startServer(endpointServer2);

        jobInstance = dispatcherUtils.waitForJobInstanceToFinish(jobInstance.getJsonNumber("instanceId").longValue());

        // Check for proper completion
        assertEquals("COMPLETED", jobInstance.getString("batchStatus"));

        // Restart servers again, let RemotablePartition table auto-create
        BatchFatUtils.stopServer(endpointServer);
        BatchFatUtils.stopServer(endpointServer2);
        BatchFatUtils.stopServer(dispatcherServer);

        endpointServer.restoreServerConfiguration();
        endpointServer2.restoreServerConfiguration();
        dispatcherServer.restoreServerConfiguration();

        // Make sure the utils don't require us to redo our DB updates, or else we're pointing to the wrong DB now
        assertEquals("After config restore, old schema != new schema", preSavedSchema, dispatcherUtils.getDatabaseSchema());

        BatchFatUtils.startServer(dispatcherServer);

        jobInstance = dispatcherUtils.submitJob("SimpleBatchJob", "test_sleepy_partitioned_batchlet", props);

        BatchFatUtils.startServer(endpointServer);
        BatchFatUtils.startServer(endpointServer2);

        jobInstance = dispatcherUtils.waitForJobInstanceToFinish(jobInstance.getInt("instanceId"));

        assertEquals("COMPLETED", jobInstance.getString("batchStatus"));

        query = "SELECT * FROM " + schema + ".REMOTABLEPARTITION";

        result = executeSql(dispatcherServer, "jdbc/batch", query);

        assertFalse("Expected REMOTABLEPARTITION table to have some data, but it is empty", result.isEmpty());
    }

    public String executeSql(LibertyServer server, String dataSourceJndi, String sql) throws Exception {

        return new DbServletClient().setDataSourceJndi(dataSourceJndi).setHostAndPort(server.getHostname(),
                                                                                      server.getHttpDefaultPort()).setSql(sql).executeQuery();
    }

    public String executeSqlUpdate(LibertyServer server, String dataSourceJndi, String sql) throws Exception {

        HttpURLConnection conn = new DbServletClient().setDataSourceJndi(dataSourceJndi).setHostAndPort(server.getHostname(),
                                                                                                        server.getHttpDefaultPort()).setSql(sql).executeUpdate();

        BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        String retVal = br.readLine();
        br.close();

        return retVal;
    }

}
