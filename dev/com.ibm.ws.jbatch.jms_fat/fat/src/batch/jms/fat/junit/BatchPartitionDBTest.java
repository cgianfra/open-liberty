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
import static org.junit.Assert.assertNotNull;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.Properties;

import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonStructure;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.ibm.websphere.simplicity.log.Log;
import com.ibm.ws.jbatch.test.FatUtils;
import com.ibm.ws.jbatch.test.dbservlet.DbServletClient;

import batch.fat.util.BatchFatUtils;
import componenttest.annotation.AllowedFFDC;
import componenttest.annotation.ExpectedFFDC;
import componenttest.custom.junit.runner.FATRunner;
import componenttest.rules.repeater.JakartaEE9Action;
import componenttest.topology.impl.LibertyServer;
import componenttest.topology.impl.LibertyServerFactory;
import componenttest.topology.utils.HttpUtils;
import componenttest.topology.utils.HttpUtils.HTTPRequestMethod;

/**
 *
 */
@RunWith(FATRunner.class)
public class BatchPartitionDBTest {

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

    @Before
    public void beforeEachTest() throws Exception {

        if (!dispatcherServer.isStarted()) {
            BatchFatUtils.startServer(dispatcherServer);
        }

        if (!endpointServer.isStarted()) {
            BatchFatUtils.startServer(endpointServer);
        }

        if (!endpointServer2.isStarted()) {
            BatchFatUtils.startServer(endpointServer2);
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

        endpointServer.setServerConfigurationFile("ThrottlingEndpoint/server.xml");
        endpointServer2.setServerConfigurationFile("ThrottlingEndpoint2/server.xml");

        setports();
        
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

        //clean start dispatcher
        dispatcherServer.startServer();
        uploadMessage = dispatcherServer.waitForStringInLogUsingMark(serverStartedMsg, dispatcherServer.getMatchingLogFile("trace.log"));
        assertNotNull("Could not find CWWKF0011I (server start info message) in trace.log of " + dispatcherServer.getServerName(), uploadMessage);
        assertEquals(Integer.getInteger("batch.dispatcher_1_HTTP_default").intValue(), dispatcherServer.getHttpDefaultPort());

        //clean start endpoint
        endpointServer.startServer();
        uploadMessage = endpointServer.waitForStringInLogUsingMark(serverStartedMsg, endpointServer.getMatchingLogFile("trace.log"));
        assertNotNull("Could not find CWWKF0011I (server start info message) in trace.log of " + endpointServer.getServerName(), uploadMessage);
        assertEquals(Integer.getInteger("batch.endpoint_1_HTTP_default").intValue(), endpointServer.getHttpDefaultPort());

        //clean start second endpoint
        endpointServer2.startServer();
        uploadMessage = endpointServer2.waitForStringInLogUsingMark(serverStartedMsg, endpointServer2.getMatchingLogFile("trace.log"));
        assertNotNull("Could not find CWWKF0011I (server start info message) in trace.log of " + endpointServer2.getServerName(), uploadMessage);
        assertEquals(Integer.getInteger("batch.endpoint_2_HTTP_default").intValue(), endpointServer2.getHttpDefaultPort());

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
     * // A failure before this happens can mess up other tests in this class
     * Stop Endpoint2 to prevent partitions to start
     * submit job
     * expect the top-level job to be executed by EndpointServer
     * EndpointServer will dispatch partitions
     * start Endpoint2
     * expect the job to be completed without errors even though there is no remotablePartition found
     */
    @AllowedFFDC({ "com.ibm.ws.sib.jfapchannel.JFapConnectionBrokenException",
                   "com.ibm.wsspi.sib.core.exception.SIConnectionDroppedException",
                   "com.ibm.websphere.sib.exception.SIResourceException",
                   "java.lang.IllegalStateException",
                   "com.ibm.ws.sib.mfp.MessageEncodeFailedException",
                   "com.ibm.wsspi.channelfw.exception.InvalidChainNameException",
                   "com.ibm.wsspi.sib.core.exception.SIConnectionLostException" })
    @Test
    public void testOldDispatcherWithNewPartitionExecutor() throws Exception {
        BatchRestUtils dispatcherUtils = new BatchRestUtils(dispatcherServer);
        BatchFatUtils.stopServer(endpointServer2);

        Integer numPartitions = 4;

        Properties props = new Properties();

        props.put("sleep.time.seconds", "3");
        props.put("randomize.sleep.time", "true");
        props.put("numPartitions", numPartitions.toString());

        // Start with a fresh count so our check if there's been a new entry is meaningful
        String schema = dispatcherUtils.getDatabaseSchema();
        String sqlDelete = "DELETE FROM " + schema + ".REMOTABLEPARTITION";
        String result = null;

        // Swallow exception, assuming it's because we ran this first.
        try {
            result = executeSqlUpdate(dispatcherServer, "jdbc/batch", sqlDelete);
        } catch (Exception e) {
            Log.info(getClass(), "testOldDispatcherWithNewPartitionExecutor",
                     "Caught exception deleting from REMOTABLEPARTITION table;  let's assume it's because we ran this test method first and keep going.  We'll catch a real failure later.  Exception msg was = "
                                                                              + e.getMessage());
        }

        //Submit job
        JsonObject jobInstance = dispatcherUtils.submitJob("SimpleBatchJob", "test_sleepy_partitioned_batchlet", props);
        long jobInstanceId = jobInstance.getJsonNumber("instanceId").longValue();

        JsonObject jobExecution = dispatcherUtils.waitForFirstJobExecution(jobInstanceId);

        //JsonObject jobExecution = dispatcherUtils.waitForJobInstanceToStart(jobInstance.getJsonNumber("instanceId").longValue());
        long jobExecutionId = jobExecution.getJsonNumber("executionId").longValue();
        jobExecution = dispatcherUtils.waitForJobExecutionToStart(jobExecutionId);

        String queryAll = "SELECT COUNT(*) FROM " + schema + ".REMOTABLEPARTITION";
        String queryJobExec = "SELECT COUNT(*) FROM " + schema + ".REMOTABLEPARTITION  WHERE FK_JOBEXECUTIONID = " + jobExecutionId;

        int numRetries = 30;
        int partitionsDispatched = 0;
        for (int i = 0; i < numRetries && partitionsDispatched != numPartitions; i++) {
            result = executeSql(dispatcherServer, "jdbc/batch", queryJobExec);
            // 'result' will be something like [2], so unwrap the brackets
            partitionsDispatched = Integer.parseInt(result.substring(1, result.length() - 1));
            Thread.sleep(2500);
        }

        assertEquals("Don't see expected number of rows in REMOTABLEPARTITION TABLE", (long) numPartitions, (long) partitionsDispatched);

        result = executeSqlUpdate(dispatcherServer, "jdbc/batch", sqlDelete);
        result = executeSql(dispatcherServer, "jdbc/batch", queryAll);
        assertEquals("Unexpected REMOTABLEPARTITION table entries, expecting empty row count", "[0]", result);

        BatchFatUtils.startServer(endpointServer2);

        jobInstance = dispatcherUtils.waitForJobInstanceToFinish(jobInstance.getJsonNumber("instanceId").longValue());

        assertEquals("COMPLETED", jobInstance.getString("batchStatus"));

        result = executeSql(dispatcherServer, "jdbc/batch", queryAll);
        assertEquals("Unexpected REMOTABLEPARTITION table entries, expecting empty row count", "[0]", result);
    }

    /**
     * Dispatch a job
     * Check when all the partitions are started on Endpoint2
     * Then, kill the Server EndpointServer2 that is running all remote partitions
     * start EndpointServer2 again
     * access a batch api to activate batch component
     * expect the StartUp recovery to change batchstatus of all running partitions to FAILED
     * restart the failed job and verify successful completion
     */

    @AllowedFFDC({ "com.ibm.wsspi.sib.core.exception.SIConnectionDroppedException",
                   "com.ibm.ws.sib.jfapchannel.JFapConnectionBrokenException",
                   "com.ibm.jbatch.container.exception.PersistenceException",
                   "javax.transaction.RollbackException",
                   "com.ibm.jbatch.container.exception.BatchIllegalJobStatusTransitionException",
                   "com.ibm.websphere.sib.exception.SIResourceException" })
    @ExpectedFFDC({ "com.ibm.jbatch.container.exception.BatchContainerRuntimeException" })
    @Test
    public void testBatchPartitionJobRecovery() throws Exception {

        BatchRestUtils dispatcherUtils = new BatchRestUtils(dispatcherServer);

        int numPartitions = 2;

        Properties props = new Properties();

        props.put("numPartitions", numPartitions + "");
        props.put("sleep.time.seconds", "30");
        props.put("randomize.sleep.time", "false");

        //Submit job
        JsonObject jobInstance = dispatcherUtils.submitJob("SimpleBatchJob", "test_sleepy_partitioned_batchlet", props);

        long jobInstanceId = jobInstance.getJsonNumber("instanceId").longValue();
        JsonObject jobExecution = dispatcherUtils.waitForFirstJobExecution(jobInstanceId);
        long jobExecutionId = jobExecution.getJsonNumber("executionId").longValue();

        //Wait for the job to start and then create a step
        Thread.sleep(2 * 1000);
        for (int i = 0; i < 50; i++) {
            Thread.sleep(1 * 1000);
            jobExecution = dispatcherUtils.getJobExecutionFromExecutionId(jobExecutionId);
            try {
                if (jobExecution.getJsonArray("stepExecutions").getJsonObject(0) != null) {
                    break;
                }
            } catch (Exception e) {
                e.printStackTrace();
                continue;
            }
        }

        Thread.sleep(3 * 1000);
        final long stepExecutionId1 = jobExecution.getJsonArray("stepExecutions").getJsonObject(0).getJsonNumber("stepExecutionId").longValue();
        JsonArray partitions1 = null;
        JsonObject stepExecution1;

        //Wait for the step to create and start partitions
        boolean isPartitionStarted = false;
        for (int i = 0; i < 30 && !isPartitionStarted; i++) {
            Thread.sleep(1 * 1000);

            stepExecution1 = dispatcherUtils.getStepExecutionFromStepExecutionId(stepExecutionId1).getJsonObject(0);
            partitions1 = stepExecution1.getJsonArray("partitions");

            if (partitions1.size() >= numPartitions) {
                for (int partitionNum = numPartitions - 1; partitionNum >= 0; partitionNum--) {

                    if (partitions1.getJsonObject(partitionNum).getString("batchStatus").equals("STARTED")) {
                        isPartitionStarted = true;
                    }
                }
            }
        }

        Thread.sleep(2 * 1000);

        killServer(endpointServer2);
        Thread.sleep(10 * 1000);

        endpointServer2.stopServer();

        JsonObject stepExecution = dispatcherUtils.getStepExecutionsFromExecutionId(jobExecution.getJsonNumber("executionId").longValue()).getJsonObject(0);

        JsonArray partitions = stepExecution.getJsonArray("partitions");

        for (int i = 0; i < numPartitions; i++) {
            assertEquals("STARTED", partitions.getJsonObject(i).getString("batchStatus"));
            assertEquals("STARTED", partitions.getJsonObject(i).getString("batchStatus"));

        }

        endpointServer2.startServer();
        FatUtils.waitForStartupAndSsl(endpointServer2);

        BatchRestUtils endpointUtils2 = new BatchRestUtils(endpointServer2);
        endpointUtils2.getBatchApi("/jobinstances");

        jobExecution = dispatcherUtils.getJobExecutionFromExecutionId(jobExecution.getJsonNumber("executionId").longValue());

        stepExecution = dispatcherUtils.getStepExecutionsFromExecutionId(jobExecution.getJsonNumber("executionId").longValue()).getJsonObject(0);

        partitions = stepExecution.getJsonArray("partitions");

        for (int i = 0; i < numPartitions; i++) {
            assertEquals("FAILED", partitions.getJsonObject(i).getString("batchStatus"));
            assertEquals("FAILED", partitions.getJsonObject(i).getString("batchStatus"));
        }

        // Wait for top-level job instance to be marked failed
        jobInstance = dispatcherUtils.waitForFinalJobInstanceState(jobInstanceId);
        jobExecution = dispatcherUtils.getJobExecutionFromExecutionId(jobExecutionId);
        stepExecution = dispatcherUtils.getStepExecutionsFromExecutionId(jobExecution.getInt("executionId")).getJsonObject(0);

        // Ensure that the job/partitions are restartable
        JsonObject jobInstanceRestart = dispatcherUtils.restartJobInstance(jobInstanceId, props);

        jobInstance = dispatcherUtils.waitForFinalJobInstanceState(jobInstanceId);
        jobExecution = dispatcherUtils.getJobExecutionsMostRecentFirst(jobInstanceId).getJsonObject(0);
        stepExecution = dispatcherUtils.getStepExecutionsFromExecutionId(jobExecution.getInt("executionId")).getJsonObject(0);

        // Check for proper completion
        assertEquals("COMPLETED", jobInstance.getString("batchStatus"));

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

    /**
     * Call the ThrottlingMDB app and get status of jobs and partitions
     *
     * @param server
     * @param path
     * @return JsonStructure
     * @throws Exception
     */
    public void killServer(LibertyServer server) throws Exception {

        try {
            JsonStructure struct = null;

            HttpURLConnection con = HttpUtils.getHttpConnection(buildHttpsURL(server.getHostname(), server.getHttpDefaultSecurePort(), "/DbServletApp/ServerKillerServlet"),
                                                                HttpURLConnection.HTTP_OK,
                                                                new int[0],
                                                                20,
                                                                HTTPRequestMethod.GET,
                                                                BatchRestUtils.buildHeaderMap(),
                                                                null);

            con.connect();
            con.disconnect();
        } catch (Exception e) {
            // Killed server won't reply cleanly
        }

    }

    /**
     * @return an https:// URL for the given host, port, and path.
     */
    public static URL buildHttpsURL(String host, int port, String path) throws MalformedURLException {
        URL retMe = new URL("https://" + host + ":" + port + path);
        return retMe;
    }

    /**
     * Changes the Database tables to back before there was a GroupAssociation Table
     *
     * @throws Exception
     */
    private static void changeDBNoRemotablePartitionTable() throws Exception {
        BatchRestUtils restUtils = new BatchRestUtils(endpointServer);
        String schema = restUtils.getDatabaseSchema();
        String tp = restUtils.getDatabaseTablePrefix();

        new DbServletClient().setDataSourceJndi("jdbc/batch").setHostAndPort("localhost", 8010).loadSql(endpointServer.pathToAutoFVTTestFiles
                                                                                                        + "NoGroupTable.ddl",
                                                                                                        schema,
                                                                                                        tp).executeUpdate();

        Log.info(BatchPartitionDBTest.class, "changeDBNoRemotablePartitionTable", "RemotablePartition Table Dropped");
    }
}
