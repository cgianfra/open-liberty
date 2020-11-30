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

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.ibm.websphere.simplicity.log.Log;
import com.ibm.ws.common.internal.encoder.Base64Coder;
import com.ibm.ws.jbatch.jms.internal.dispatcher.BatchJmsDispatcher;
import com.ibm.ws.jbatch.jms.internal.listener.impl.BatchJmsExecutor;
import com.ibm.ws.jbatch.test.FatUtils;

import componenttest.custom.junit.runner.FATRunner;
import componenttest.custom.junit.runner.Mode;
import componenttest.custom.junit.runner.Mode.TestMode;
import componenttest.topology.impl.LibertyServer;
import componenttest.topology.impl.LibertyServerFactory;
import componenttest.topology.utils.HttpUtils;

/**
 * Test different variations of batch jms configurations
 * This test is moved into full mode because it is not nessary to test for bad
 * config. The BatchJmsNonDefaultConfigTest should cover the validity of configuation
 */
@RunWith(FATRunner.class)
@Mode(TestMode.FULL)
public class BatchJmsBadConfigTest {

    private static final LibertyServer server = LibertyServerFactory.getLibertyServer("BatchJmsSingleServer_fat");

    //Instance fields
    private final Map<String, String> adminHeaderMap, headerMap;

    // As defined in the server.xml
    protected final static String ADMIN_NAME = "bob";
    protected final static String ADMIN_PASSWORD = "bobpwd";
    private final static String USER_NAME = "jane";
    private final static String USER_PASSWORD = "janepwd";

    public BatchJmsBadConfigTest() {
        adminHeaderMap = Collections.singletonMap("Authorization", "Basic " + Base64Coder.base64Encode(ADMIN_NAME + ":" + ADMIN_PASSWORD));
        headerMap = Collections.singletonMap("Authorization", "Basic " + Base64Coder.base64Encode(USER_NAME + ":" + USER_PASSWORD));
    }

    /**
     * Startup the server
     */
    @BeforeClass
    public static void beforeClass() throws Exception {
        HttpUtils.trustAllCertificates();
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

    /**
     * Server.xml does not contain
     * <batchJmsDispatcher> element
     * Result: log show component missing required configuration.
     */
    @Test
    public void testDispatcherNoBatchJmsConfig() throws Exception {

        try {

            server.setServerConfigurationFile("NoBatchJmsConfig/server.xml");
            server.copyFileToLibertyServerRoot("NoBatchJmsConfig/bootstrap.properties");
            server.startServer();

            FatUtils.waitForSmarterPlanet(server);

            checkLog(BatchJmsDispatcher.class);

        } finally {
            if (server != null && server.isStarted()) {
                try {
                    server.stopServer();
                } catch (Exception e) {
                    // TODO Auto-generated catch block
                    // Do you need FFDC here? Remember FFDC instrumentation and @FFDCIgnore
                    // http://was.pok.ibm.com/xwiki/bin/view/Liberty/LoggingFFDC
                    e.printStackTrace();
                }
            }
        }

    }

    /**
     * Server.xml does not contain
     * <batchJmsExecutor> element
     * Result: log show component missing required configuration.
     */
    @Test
    public void testExecutorNoBatchJmsConfig() throws Exception {

        try {

            server.setServerConfigurationFile("NoBatchJmsConfig/server.xml");
            server.copyFileToLibertyServerRoot("NoBatchJmsConfig/bootstrap.properties");
            server.startServer();

            FatUtils.waitForSmarterPlanet(server);

            checkLog(BatchJmsExecutor.class);

        } finally {
            if (server != null && server.isStarted()) {
                try {
                    server.stopServer();
                } catch (Exception e) {
                    // TODO Auto-generated catch block
                    // Do you need FFDC here? Remember FFDC instrumentation and @FFDCIgnore
                    // http://was.pok.ibm.com/xwiki/bin/view/Liberty/LoggingFFDC
                    e.printStackTrace();
                }
            }
        }

    }

    private void checkLog(Class<?> clazz) throws Exception {
        String enabledHolder = "Enabling component holder " + clazz.getCanonicalName();
        List<String> result = server.findStringsInTrace(enabledHolder);

        Assert.assertEquals(clazz.getCanonicalName() + " expected to have enabled holder once: found: " + result, 1, result.size());

        String createdComponent = "Component " + clazz.getCanonicalName() + " created:";

        result = server.findStringsInTrace(createdComponent);

        Assert.assertEquals(clazz.getCanonicalName() + " expected to have no component configuration created: found: " + result, 0, result.size());
    }

    /**
     * helper for simple logging.
     */
    private void log(String method, String msg) {
        Log.info(this.getClass(), method, msg);
    }
}
