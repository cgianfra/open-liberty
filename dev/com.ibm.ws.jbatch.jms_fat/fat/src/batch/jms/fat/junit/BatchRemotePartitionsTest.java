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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Enumeration;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.batch.runtime.BatchStatus;
import javax.batch.runtime.JobExecution;
import javax.batch.runtime.JobInstance;
import javax.json.JsonArray;
import javax.json.JsonObject;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.ibm.websphere.simplicity.ProgramOutput;
import com.ibm.websphere.simplicity.log.Log;
import com.ibm.ws.jbatch.test.BatchManagerCliUtils;
import com.ibm.ws.jbatch.test.FatUtils;
import com.ibm.ws.jbatch.test.dbservlet.DbServletClient;

import batch.fat.util.BatchFatUtils;
import componenttest.annotation.AllowedFFDC;
import componenttest.custom.junit.runner.FATRunner;
import componenttest.custom.junit.runner.Mode;
import componenttest.custom.junit.runner.Mode.TestMode;
import componenttest.rules.repeater.JakartaEE9Action;
import componenttest.topology.impl.LibertyServer;
import componenttest.topology.impl.LibertyServerFactory;
import componenttest.topology.utils.HttpUtils;
import componenttest.topology.utils.HttpUtils.HTTPRequestMethod;

/**
 * These test heavily depend on the trace logs. Any change in the logging level or runtime methods
 * might fail these tests.
 *
 * PLEASE UPDATE THESE TEST WITH CHANGE IN TRACE AND RUNTIME CODE RELATED TO LOCAL/JMS PARTITIONS
 */
@RunWith(FATRunner.class)
@Mode(TestMode.LITE)
public class BatchRemotePartitionsTest {

    private static final LibertyServer messageEngineServer = LibertyServerFactory.getLibertyServer("BatchJmsMessageEngine");

    //batch jms endpoint server
    //This should run the partitions with partition number 0 and 1
    private static final LibertyServer endpointServer = LibertyServerFactory.getLibertyServer("BatchJmsEndpoint");

    //batch jms endpoint server
    //This should run the partitions with partitoin number 2
    private static final LibertyServer endpointServer2 = LibertyServerFactory.getLibertyServer("BatchJmsEndpoint2");

    //batch jms dispatcher server
    //This should run as dispatcher as well as endpoint for all top-level jobs
    private static final LibertyServer dispatcherServer = LibertyServerFactory.getLibertyServer("BatchJmsDispatcher");

    // As defined in the server.xml
    protected final static String ADMIN_NAME = "bob";
    protected final static String ADMIN_PASSWORD = "bobpwd";

    // For parsing text log results
    private static final String prefix = "xxxxx Begin file: ";
    private static final String suffix = " xxxxxxxxxxxxxxxxxx";

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

        //Many tests look at the trace.log file for method entries and should have an accurate count.
        //Hence setting mark before each test is run
        BatchFatUtils.setMarkToEndOfTraceForAllServers(dispatcherServer, endpointServer, endpointServer2);
    }

    /**
     * Startup the servers
     */
    @BeforeClass
    public static void beforeClass() throws Exception {

        HttpUtils.trustAllCertificates();
        String serverStartedMsg = "CWWKF0011I:.*";

        //set port in LibertyServer object because it doesn't return the correct port value if substitution is used in server.xml

        dispatcherServer.setServerConfigurationFile("MultiJvmPartitionsDispatcher/server.xml");
        endpointServer.setServerConfigurationFile("MultiJvmPartitionsEndpoint/server.xml");
        endpointServer2.setServerConfigurationFile("MultiJvmPartitionsEndpoint2/server.xml");

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

        // Setup BonusPayout app tables
        new DbServletClient().setDataSourceJndi("jdbc/BonusPayoutDS").setDataSourceUser("user",
                                                                                        "pass").setHostAndPort(endpointServer2.getHostname(),
                                                                                                               endpointServer2.getHttpDefaultPort()).loadSql(endpointServer2.pathToAutoFVTTestFiles
                                                                                                                                                             + "common/BonusPayout.derby.ddl",

                                                                                                                                                             "JBATCH",
                                                                                                                                                             "").executeUpdate();

        // Setup certificate for use in SSL
        BatchRestUtils.exchangeCertificates(dispatcherServer, endpointServer);
        BatchRestUtils.exchangeCertificates(dispatcherServer, endpointServer2);

        // Restart so the server notices the certificate we just added to its keystore
        dispatcherServer.stopServer();
        endpointServer.stopServer();
        endpointServer2.stopServer();

        dispatcherServer.startServer();
        endpointServer.startServer();
        endpointServer2.startServer();

        FatUtils.waitForStartupAndSsl(dispatcherServer);
        FatUtils.waitForStartupAndSsl(endpointServer);
        FatUtils.waitForStartupAndSsl(endpointServer2);
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

    /*
     * Tests stop then restart while partitions are running on multiple servers
     *
     * Allowed FFDCs because when the TLJ is stopped in the dispatcher,
     * it stops listening to the reply from the partition executor
     */
    @Test
    @AllowedFFDC({ "com.ibm.ws.sib.jfapchannel.JFapConnectionBrokenException" })
    public void testMultiJvmPartitionsRestart() throws Exception {

        BatchRestUtils dispatcherUtils = new BatchRestUtils(dispatcherServer);

        //Submit job
        JsonObject jobInstance = dispatcherUtils.submitJob("SimpleBatchJob", "test_sleepy_partition");

        //Initialize Instance artifacts(jobInstance, jobInstanceId);
        long jobInstanceId = jobInstance.getJsonNumber("instanceId").longValue();

        //Initialize Execution artifacts(jobExecution, jobExecutionId);
        JsonObject jobExecution = dispatcherUtils.waitForFirstJobExecution(jobInstanceId);
        long jobExecutionId = jobExecution.getJsonNumber("executionId").longValue();

        //Wait for step1 to get created and started
        JsonObject stepExecution1 = dispatcherUtils.waitForStepInJobExecutionToReachStatus(jobExecutionId, "step1", BatchStatus.STARTED);

        JsonArray partitions;

        //Check for 3 partitions to start running. Once they start, execute the stop
        for (int i = 0; i < 30; i++) {
            stepExecution1 = dispatcherUtils.getStepExecutionFromExecutionIdAndStepName(jobExecutionId, "step1").getJsonObject(0);
            partitions = stepExecution1.getJsonArray("partitions");
            if (partitions.size() == 3) {
                break;
            }
            Thread.sleep(1000);
        }

        //Stop the job
        jobExecution = dispatcherUtils.stopJobExecutionAndWaitUntilDone(jobExecutionId);

        assertEquals("Checking job exec batch status after stop", "STOPPED", jobExecution.getString("batchStatus"));

        JsonArray stepExecutions = jobExecution.getJsonArray("stepExecutions");
        assertEquals(1, stepExecutions.size());
        JsonObject stepExecution = stepExecutions.getJsonObject(0);
        assertEquals("Checking step exec batch status after stop", "STOPPED", stepExecution.getString("batchStatus"));

        //Allow enough time to get all the running partitions to complete
        //Because we don't have stop functioning properly for batchlet partitions
        //TODO remove waiting when partitions stop is implemented and
        //change COMPLETED to STOPPED for partition status
        Thread.sleep(20 * 1000);

        //checking step1
        stepExecutions = dispatcherUtils.getStepExecutionsFromExecutionId(jobExecutionId);
        assertEquals(2, stepExecutions.size());//step1 + _links
        stepExecution1 = dispatcherUtils.getStepExecutionFromExecutionIdAndStepName(jobExecutionId, "step1").getJsonObject(0);

        assertEquals("STOPPED", stepExecution1.getString("batchStatus"));
        partitions = stepExecution1.getJsonArray("partitions");
        BatchFatUtils.checkExpectedBatchStatus(partitions, 3);

        //No step2 because the job was stopped before step2 started

        //Dispatcher should dispatch all partitions since it is running the top job
        checkNumOfMultiJvmtrueCheckInLog(dispatcherServer, 1); //step1 + 3 partitions
        checkNumOfStartPartitionRequestInLog(dispatcherServer, 3);

        //PartitionNum 0 : EndpointServer
        //PartitionNum 1 : EndpointServer
        //PartitionNum 2 : EndpointServer2
        checkPartitionsExitStatus(stepExecution1.getJsonArray("partitions"), new String[] { "BatchJmsEndpoint", "BatchJmsEndpoint", "BatchJmsEndpoint2" });

        //Reset the mark to end of trace.log
        BatchFatUtils.setMarkToEndOfTraceForAllServers(dispatcherServer, endpointServer, endpointServer2);

        //Restart the job
        jobInstance = dispatcherUtils.restartJobExecution(jobInstance, jobExecutionId, new Properties());

        Thread.sleep(2 * 1000);

        JsonArray jobExecutions = dispatcherUtils.getJobExecutionsMostRecentFirst(jobInstanceId);

        assertEquals(2, jobExecutions.size());
        jobExecution = jobExecutions.getJsonObject(0);

        //Too many timing issues trying to sort out started, and its not really a necessary check here.
        //If there's a problem, we'll see it when we wait for the instance to finish (if we don't start
        //, then that check will timeout and fail the test)
        //assertEquals("STARTED", jobExecution.getString("batchStatus"));
        jobExecutionId = jobExecution.getJsonNumber("executionId").longValue();

        jobInstance = dispatcherUtils.waitForJobInstanceToFinish(jobInstanceId);

        //Get step Executions and check their size and status
        stepExecutions = dispatcherUtils.getStepExecutionsFromExecutionId(jobExecutionId);

        //there will be no step1 partitions run because this is a RESTART_NORMAL as they were run earlier.
        //Only step2 partitions will run
        BatchFatUtils.checkPartitionsSizeAndStatusCompleted(stepExecutions, "step1", 0);
        BatchFatUtils.checkPartitionsSizeAndStatusCompleted(stepExecutions, "step2", 2);

        //Dispatcher should dispatch all partitions since it is running the top job

        //PartitionNum 0 : EndpointServer
        //PartitionNum 1 : EndpointServer
        //PartitionNum 2 : DispatcherServer

        JsonObject step2Execution = dispatcherUtils.getStepExecutionFromExecutionIdAndStepName(jobExecutionId, "step2").getJsonObject(0);
        checkPartitionsExitStatus(step2Execution.getJsonArray("partitions"), new String[] { "BatchJmsEndpoint", "BatchJmsEndpoint" });

        // Prevents previous FFDCs from being detected for these servers.
        BatchFatUtils.restartServer(endpointServer);
        BatchFatUtils.restartServer(endpointServer2);
    }

    /*
     * Tests the partitions are not run on multiple servers if disabled from the job properties
     */
    @Test
    @Mode(TestMode.FULL)
    public void testMultiJvmPartitionsDisabled() throws Exception {

        BatchRestUtils dispatcherUtils = new BatchRestUtils(dispatcherServer);

        //Submit job
        JsonObject jobInstance = dispatcherUtils.submitJob("SimpleBatchJob", "test_simplePartition_multiJvm_disabled");

        //Initialize Instance artifacts(jobInstance, jobInstanceId);
        long jobInstanceId = jobInstance.getJsonNumber("instanceId").longValue();

        jobInstance = dispatcherUtils.waitForJobInstanceToFinish(jobInstanceId);
        long jobExecutionId = dispatcherUtils.getOnlyExecutionIdFromInstance(jobInstance);

        dispatcherUtils.getStepExecutionsFromExecutionId(jobExecutionId);

        JsonArray stepExecutions = dispatcherUtils.getStepExecutionsFromExecutionId(jobExecutionId);
        BatchFatUtils.checkPartitionsSizeAndStatusCompleted(stepExecutions, "step1", 3);

        //Checking
        assertTrue(BatchRestUtils.isDone(jobInstance));
        assertEquals("test_simplePartition_multiJvm_disabled", BatchRestUtils.getJobName(jobInstance));
        assertEquals(ADMIN_NAME, BatchRestUtils.getSubmitter(jobInstance));
        assertEquals("SimpleBatchJob#SimpleBatchJob.war", BatchRestUtils.getAppName(jobInstance));
        assertEquals("COMPLETED", jobInstance.getString("batchStatus"));

        JsonArray linkArray = jobInstance.getJsonArray("_links");
        assertNotNull(linkArray);

        JsonArray partitions = stepExecutions.getJsonObject(0).getJsonArray("partitions");
        for (int i = 0; i < 3; i++) {
            assertTrue("restUrl was not empty", partitions.getJsonObject(i).getString("restUrl").equals(""));
            assertTrue("serverId was not empty", partitions.getJsonObject(i).getString("serverId").equals(""));
        }

        //PartitionNum 0 : DispatcherServer
        //PartitionNum 1 : DispatcherServer
        //PartitionNum 2 : DispatcherServer
        checkPartitionsExitStatus(stepExecutions.getJsonObject(0).getJsonArray("partitions"), new String[] { "BatchJmsDispatcher", "BatchJmsDispatcher", "BatchJmsDispatcher" });

    }

    /*
     * checks that joblogs are created on the remote server running partitions
     */
    @Test
    @Mode(TestMode.FULL)
    public void testRemotePartitionJoblogsByExecution() throws Exception {

        String jobXmlName = "test_sleepy_partition";

        BatchRestUtils dispatcherUtils = new BatchRestUtils(dispatcherServer);
        JsonObject jobInstance = dispatcherUtils.submitJob("SimpleBatchJob", jobXmlName);
        long jobInstanceId = jobInstance.getJsonNumber("instanceId").longValue();

        JsonObject jobExecution = dispatcherUtils.waitForFirstJobExecution(jobInstanceId);

        long jobExecutionId = jobExecution.getJsonNumber("executionId").longValue();
        jobExecution = dispatcherUtils.waitForJobExecutionToStart(jobExecutionId);

        dispatcherUtils.waitForFinalJobInstanceState(jobInstance.getJsonNumber("instanceId").longValue(), 120);

        assertTrue(endpointServer.fileExistsInLibertyServerRoot("logs"));

        assertTrue(endpointServer.fileExistsInLibertyServerRoot("logs/joblogs"));
        assertTrue(endpointServer2.fileExistsInLibertyServerRoot("logs/joblogs"));

        // Fetch the job execution logs in zip format.
        HttpURLConnection con = HttpUtils.getHttpConnection(dispatcherUtils.buildURL("/ibm/api/batch/jobexecutions/" + jobExecutionId + "/joblogs?type=zip"),
                                                            HttpURLConnection.HTTP_OK,
                                                            new int[0],
                                                            10 * 1000,
                                                            HTTPRequestMethod.GET,
                                                            BatchRestUtils.buildHeaderMap(),
                                                            null);

        // We'll verify that all of the partition logs made it into the zip
        String executionBase = "execution." + jobExecutionId + "/";
        ArrayList<String> expectedFiles = new ArrayList<String>();
        expectedFiles.add(executionBase + "part.1.log");
        expectedFiles.add(executionBase + "step1/0/part.1.log");
        expectedFiles.add(executionBase + "step1/1/part.1.log");
        expectedFiles.add(executionBase + "step1/2/part.1.log");
        expectedFiles.add(executionBase + "step2/0/part.1.log");
        expectedFiles.add(executionBase + "step2/1/part.1.log");

        String zipName = System.getProperty("user.dir") + File.separator + "myjoblog1.zip";
        FileOutputStream zipOut = new FileOutputStream(zipName);
        byte[] buffer = new byte[2048];
        int bytesRead = 0;
        while ((bytesRead = con.getInputStream().read(buffer)) != -1) {
            zipOut.write(buffer, 0, bytesRead);
        }
        zipOut.close();

        ZipFile zipIn = new ZipFile(zipName);
        Enumeration<ZipEntry> entries = (Enumeration<ZipEntry>) zipIn.entries();

        while (entries.hasMoreElements()) {
            String entryName = entries.nextElement().getName();
            if (expectedFiles.contains(entryName)) {
                expectedFiles.remove(entryName);
            }
        }
        zipIn.close();

        assertTrue("Not all partition log files were found in the execution zip result, missing: " + expectedFiles,
                   expectedFiles.isEmpty());

        // Fetch the job execution logs in text format
        con = HttpUtils.getHttpConnection(dispatcherUtils.buildURL("/ibm/api/batch/jobexecutions/" + jobExecutionId + "/joblogs?type=text"),
                                          HttpURLConnection.HTTP_OK,
                                          new int[0],
                                          10 * 1000,
                                          HTTPRequestMethod.GET,
                                          BatchRestUtils.buildHeaderMap(),
                                          null);

        expectedFiles = new ArrayList<String>();
        expectedFiles.add(executionBase + "part.1.log");
        expectedFiles.add(executionBase + "step1/0/part.1.log");
        expectedFiles.add(executionBase + "step1/1/part.1.log");
        expectedFiles.add(executionBase + "step1/2/part.1.log");
        expectedFiles.add(executionBase + "step2/0/part.1.log");
        expectedFiles.add(executionBase + "step2/1/part.1.log");

        BufferedReader buf = new BufferedReader(new InputStreamReader(con.getInputStream()));
        String line = buf.readLine();

        while (line != null && !expectedFiles.isEmpty()) {
            if (line.startsWith(prefix) && line.endsWith(suffix)) {
                int start = prefix.length();
                int end = line.length() - suffix.length();
                String filename = line.substring(start, end);
                if (expectedFiles.contains(filename)) {
                    expectedFiles.remove(filename);
                }
            }
            line = buf.readLine();
        }

        assertTrue("Not all partition log files were found in the execution text result, missing: " + expectedFiles,
                   expectedFiles.isEmpty());

    }

    @Test
    public void testRemotePartitionJoblogsByInstance() throws Exception {

        String jobXmlName = "test_sleepy_partition";

        BatchRestUtils dispatcherUtils = new BatchRestUtils(dispatcherServer);
        JsonObject jobInstance = dispatcherUtils.submitJob("SimpleBatchJob", jobXmlName);
        long jobInstanceId = jobInstance.getJsonNumber("instanceId").longValue();

        JsonObject jobExecution = dispatcherUtils.waitForFirstJobExecution(jobInstanceId);

        long jobExecutionId = jobExecution.getJsonNumber("executionId").longValue();
        jobExecution = dispatcherUtils.waitForJobExecutionToStart(jobExecutionId);

        dispatcherUtils.waitForFinalJobInstanceState(jobInstance.getJsonNumber("instanceId").longValue(), 120);

        assertTrue(endpointServer.fileExistsInLibertyServerRoot("logs"));

        assertTrue(endpointServer.fileExistsInLibertyServerRoot("logs/joblogs"));
        assertTrue(endpointServer2.fileExistsInLibertyServerRoot("logs/joblogs"));

        // Fetch the job instance logs in zip format.
        HttpURLConnection con = HttpUtils.getHttpConnection(dispatcherUtils.buildURL("/ibm/api/batch/jobinstances/" + jobInstanceId + "/joblogs?type=zip"),
                                                            HttpURLConnection.HTTP_OK,
                                                            new int[0],
                                                            10 * 1000,
                                                            HTTPRequestMethod.GET,
                                                            BatchRestUtils.buildHeaderMap(),
                                                            null);

        String instanceBase = "instance." + jobInstanceId + "/execution." + jobExecutionId + "/";
        ArrayList<String> expectedFiles = new ArrayList<String>();
        expectedFiles.add(instanceBase + "part.1.log");
        expectedFiles.add(instanceBase + "step1/0/part.1.log");
        expectedFiles.add(instanceBase + "step1/1/part.1.log");
        expectedFiles.add(instanceBase + "step1/2/part.1.log");
        expectedFiles.add(instanceBase + "step2/0/part.1.log");
        expectedFiles.add(instanceBase + "step2/1/part.1.log");

        String zipName = System.getProperty("user.dir") + File.separator + "myjoblog2.zip";
        FileOutputStream zipOut = new FileOutputStream(zipName);
        byte[] buffer = new byte[2048];
        int bytesRead = 0;
        while ((bytesRead = con.getInputStream().read(buffer)) != -1) {
            zipOut.write(buffer, 0, bytesRead);
        }
        zipOut.close();

        ZipFile zipIn = new ZipFile(zipName);
        Enumeration<ZipEntry> entries = (Enumeration<ZipEntry>) zipIn.entries();

        while (entries.hasMoreElements()) {
            String entryName = entries.nextElement().getName();
            if (expectedFiles.contains(entryName)) {
                expectedFiles.remove(entryName);
            }
        }
        zipIn.close();

        assertTrue("Not all partition log files were found in the instance zip result, missing: " + expectedFiles,
                   expectedFiles.isEmpty());

        // Fetch the job instance logs in text format
        con = HttpUtils.getHttpConnection(dispatcherUtils.buildURL("/ibm/api/batch/jobinstances/" + jobInstanceId + "/joblogs?type=text"),
                                          HttpURLConnection.HTTP_OK,
                                          new int[0],
                                          10 * 1000,
                                          HTTPRequestMethod.GET,
                                          BatchRestUtils.buildHeaderMap(),
                                          null);

        expectedFiles = new ArrayList<String>();
        expectedFiles.add(instanceBase + "part.1.log");
        expectedFiles.add(instanceBase + "step1/0/part.1.log");
        expectedFiles.add(instanceBase + "step1/1/part.1.log");
        expectedFiles.add(instanceBase + "step1/2/part.1.log");
        expectedFiles.add(instanceBase + "step2/0/part.1.log");
        expectedFiles.add(instanceBase + "step2/1/part.1.log");

        BufferedReader buf = new BufferedReader(new InputStreamReader(con.getInputStream()));
        String line = buf.readLine();

        while (line != null && !expectedFiles.isEmpty()) {
            if (line.startsWith(prefix) && line.endsWith(suffix)) {
                int start = prefix.length();
                int end = line.length() - suffix.length();
                String filename = line.substring(start, end);
                if (expectedFiles.contains(filename)) {
                    expectedFiles.remove(filename);
                }
            }
            line = buf.readLine();
        }

        assertTrue("Not all partition log files were found in the instance text result, missing: " + expectedFiles,
                   expectedFiles.isEmpty());

        // Purge the job instance logs
        con = HttpUtils.getHttpConnection(dispatcherUtils.buildURL("/ibm/api/batch/jobinstances/" + jobInstanceId),
                                          HttpURLConnection.HTTP_OK,
                                          new int[0],
                                          10 * 1000,
                                          HTTPRequestMethod.DELETE,
                                          BatchRestUtils.buildHeaderMap(),
                                          null);

        String joblogDirPath = "logs/joblogs/" + jobXmlName + "/" + new SimpleDateFormat("yyyy-MM-dd").format(new Date()) + "/" + instanceBase;

        assertTrue("Directory " + endpointServer.getServerRoot() + "/" + joblogDirPath + " exists after purge",
                   !endpointServer.fileExistsInLibertyServerRoot(joblogDirPath));
        assertTrue("Directory " + endpointServer2.getServerRoot() + "/" + joblogDirPath + " exists after purge",
                   !endpointServer2.fileExistsInLibertyServerRoot(joblogDirPath));

    }

    @Test
    @Mode(TestMode.FULL)
    public void testRemotePartitionJoblogsByCLI() throws Exception {
        String appName = "SimpleBatchJob";
        String jobXMLName = "test_sleepy_partition";
        String user = "bob";
        String pass = "bobpwd";

        BatchManagerCliUtils batchManagerCliUtils = new BatchManagerCliUtils(dispatcherServer);

        ProgramOutput po = batchManagerCliUtils.submitJob(
                                                          new String[] { "submit",
                                                                         "--batchManager=" + batchManagerCliUtils.getHostAndPort(),
                                                                         "--user=" + user,
                                                                         "--password=" + pass,
                                                                         "--applicationName=" + appName,
                                                                         "--jobXMLName=" + jobXMLName,
                                                                         "--pollingInterval_s=2",
                                                                         "--wait" });

        assertEquals(35, po.getReturnCode()); // 5==BatchStatus.COMPLETED

        JobInstance jobInstance = BatchManagerCliUtils.parseJobInstanceFromSubmitMessages(po.getStdout());
        JobExecution jobExecution = BatchManagerCliUtils.parseJobExecutionMessage(po.getStdout());
        long jobInstanceId = jobInstance.getInstanceId();
        long jobExecutionId = jobExecution.getExecutionId();

        log("submitJob", "parsed jobInstance=" + jobInstance + ", jobExecution=" + jobExecution);

        // Get the logs using the CLI in zip format
        BatchManagerCliUtils dispatcherCliUtils = new BatchManagerCliUtils(dispatcherServer);

        String zipName = System.getProperty("user.dir") + File.separator + "myjoblog3.zip";

        po = dispatcherCliUtils.executeCommand(new String[] { "getJobLog",
                                                              "--batchManager=" + dispatcherCliUtils.getHostAndPort(),
                                                              "--user=" + ADMIN_NAME,
                                                              "--password=" + ADMIN_PASSWORD,
                                                              "--jobInstanceId=" + jobInstanceId,
                                                              "--trustSslCertificates",
                                                              "--type=zip",
                                                              "--outputFile=" + zipName });

        String instanceBase = "instance." + jobInstanceId + "/execution." + jobExecutionId + "/";
        ArrayList<String> expectedFiles = new ArrayList<String>();
        expectedFiles.add(instanceBase + "part.1.log");
        expectedFiles.add(instanceBase + "step1/0/part.1.log");
        expectedFiles.add(instanceBase + "step1/1/part.1.log");
        expectedFiles.add(instanceBase + "step1/2/part.1.log");
        expectedFiles.add(instanceBase + "step2/0/part.1.log");
        expectedFiles.add(instanceBase + "step2/1/part.1.log");

        // Parse the output file name from the message
        Matcher matcher = Pattern.compile("CWWKY0111I: The joblog was written to file (\\S+)").matcher(po.getStdout());
        assertTrue(matcher.find());

        ZipFile zipFile = new ZipFile(matcher.group(1));
        Enumeration<ZipEntry> entries = (Enumeration<ZipEntry>) zipFile.entries();

        while (entries.hasMoreElements()) {
            String entryName = entries.nextElement().getName();
            if (expectedFiles.contains(entryName)) {
                expectedFiles.remove(entryName);
            }
        }
        zipFile.close();

        assertTrue("Not all partition log files were found in the CLI instance zip result, missing: " + expectedFiles,
                   expectedFiles.isEmpty());

        // Get the logs using the CLI in text format
        po = dispatcherCliUtils.executeCommand(new String[] { "getJobLog",
                                                              "--batchManager=" + dispatcherCliUtils.getHostAndPort(),
                                                              "--user=" + ADMIN_NAME,
                                                              "--password=" + ADMIN_PASSWORD,
                                                              "--jobInstanceId=" + jobInstanceId,
                                                              "--trustSslCertificates" });

        assertEquals(0, po.getReturnCode());

        // Verify expected contents, including the job name, along with aggregate header/footers
        expectedFiles = new ArrayList<String>();
        expectedFiles.add(instanceBase + "part.1.log");
        expectedFiles.add(instanceBase + "step1/0/part.1.log");
        expectedFiles.add(instanceBase + "step1/1/part.1.log");
        expectedFiles.add(instanceBase + "step1/2/part.1.log");
        expectedFiles.add(instanceBase + "step2/0/part.1.log");
        expectedFiles.add(instanceBase + "step2/1/part.1.log");

        ArrayList<String> expectedCopy = (ArrayList<String>) expectedFiles.clone();
        for (String expected : expectedCopy) {
            if (po.getStdout().contains(prefix + expected + suffix)) {
                expectedFiles.remove(expected);
            }
        }

        assertTrue("Not all partition log files were found in the CLI instance text result, missing: " + expectedFiles,
                   expectedFiles.isEmpty());

        // Purge the job instance
        po = batchManagerCliUtils.executeCommand(
                                                 new String[] { "purge",
                                                                "--batchManager=" + batchManagerCliUtils.getHostAndPort(),
                                                                "--user=" + user,
                                                                "--password=" + pass,
                                                                "--jobInstanceId=" + jobInstanceId
                                                 });
        assertEquals(0, po.getReturnCode());

        String joblogDirPath = "logs/joblogs/" + jobXMLName + "/" + new SimpleDateFormat("yyyy-MM-dd").format(new Date()) + "/" + instanceBase;

        assertTrue("Directory " + endpointServer.getServerRoot() + "/" + joblogDirPath + " exists after purge",
                   !endpointServer.fileExistsInLibertyServerRoot(joblogDirPath));
        assertTrue("Directory " + endpointServer2.getServerRoot() + "/" + joblogDirPath + " exists after purge",
                   !endpointServer2.fileExistsInLibertyServerRoot(joblogDirPath));
    }

    /**
     * helper for simple logging.
     */
    private void log(String method, String msg) {
        Log.info(this.getClass(), method, msg);
    }

    /*
     * Method to verify exitStatus for each partition
     *
     * @param list of partitions returned in the JSON response
     *
     * @param list of exitStatus for each partitionNumber starting from partitionNumber : 0
     */
    public void checkPartitionsExitStatus(JsonArray partitions, String[] exitStatusList) {
        for (int i = 0; i < exitStatusList.length; i++) {

            assertEquals(exitStatusList[i], partitions.getJsonObject(i).getString("exitStatus"));

        }
    }

    /*
     * checks the number of startPartition Requests in the server log
     *
     * @param server
     *
     * @param numStartPartition
     */
    private static void checkNumOfStartPartitionRequestInLog(LibertyServer server, int numStartPartition) throws Exception {
        assertEquals(numStartPartition, server.findStringsInLogsAndTraceUsingMark("BatchJmsDispatcher > startPartition Entry").size());

    }

    /*
     * checks the isMultiJvm true check count in the server log
     *
     * @param server
     *
     * @param numMultiJvmCheck
     */
    private static void checkNumOfMultiJvmtrueCheckInLog(LibertyServer server, int numMultiJvmCheck) throws Exception {
        assertEquals(numMultiJvmCheck, server.findStringsInLogsAndTraceUsingMark("isMultiJvm RETURN true").size());

    }

}
