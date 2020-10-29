package batch.fat.junit;

/*
 * IBM Confidential
 *
 * OCO Source Materials
 *
 * Copyright IBM Corp. 2011
 *
 * The source code for this program is not published or other-
 * wise divested of its trade secrets, irrespective of what has
 * been deposited with the U.S. Copyright Office.
 */
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;

import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.ibm.websphere.simplicity.config.FeatureManager;
import com.ibm.websphere.simplicity.config.ServerConfiguration;
import com.ibm.websphere.simplicity.log.Log;
import com.ibm.ws.jbatch.test.FatUtils;

import batch.fat.common.util.RepeatTestRule;
import batch.fat.util.BatchFATHelper;
import componenttest.annotation.ExpectedFFDC;
import componenttest.custom.junit.runner.FATRunner;
import componenttest.custom.junit.runner.Mode;
import componenttest.custom.junit.runner.Mode.TestMode;

/*
 * This class will run multiple tests per server start.
 *
 * Regarding full vs lite mix:  idea is to have "enough" lite coverage by ensuring at least one test doing each of:
 *
 *  - restart (but not necessarily restarting twice)
 *  - partitioned
 *  - restart (once) partitioned
 *  - cursor hold
 *
 */
@RunWith(FATRunner.class)
public class BonusPayoutViaJobOperatorTest extends BatchFATHelper {

    protected final String CTX_ROOT = "BonusPayout";
    private static final Class testClass = BonusPayoutViaJobOperatorTest.class;
    private static final Set<String> appNames = new TreeSet<String>(Arrays.asList("BonusPayout"));

    /**
     * This rule is used to run the test multiple times against different server configurations.
     * In this case, the tests are run twice once with cdi and once without
     */
    @ClassRule
    public static RepeatTestRule repeatTestRule = new RepeatTestRule(new RepeatTestRule.Callback() {

        public int runCount = 0;

        /**
         * Run before all the repeated test(ran once at the beginning)
         */
        @Override
        public void beforeAll() throws Exception {
            setup();
        }

        /**
         * Run after all the repeated test(ran once at the end)
         */
        @Override
        public void afterAll() throws Exception {
            tearDown();
        }

        /**
         * Run before each test run(ran once per test at the beginning)
         */
        @Override
        public void beforeEach() throws Exception {
            if (runCount > 0)//if it isn't the first run
                turnOffCDIFeature();
        }

        /**
         * Determines how many times the test is ran
         */
        @Override
        public boolean doRepeat() {
            return (++runCount < 2); // Quit after 2 runs.
        }

    });

    private static void turnOffCDIFeature() throws Exception {
        ServerConfiguration config = server.getServerConfiguration();
        FeatureManager fm = config.getFeatureManager();
        Set<String> featureList = fm.getFeatures();
        if (featureList.contains("cdi-1.2")) {
            featureList.remove("cdi-1.2");
            log("turnOffCDIFeature", "turned off cdi");
        } else {
            featureList.add("cdi-1.2");
            log("turnOffCDIFeature", "turned on cdi");
        }
        server.updateServerConfiguration(config);
        server.waitForConfigUpdateInLogUsingMark(appNames, false);
    }

    private static void log(String method, Object msg) {
        Log.info(testClass, method, String.valueOf(msg));
    }

    public static void setup() throws Exception {

        BatchFATHelper.setConfig("BonusPayoutViaJobOperator/server.xml", testClass);

        BatchFATHelper.startServer(server, testClass);

        FatUtils.waitForSmarterPlanet(server);
        /*
         * Note this packaging detail: The DB servlet will live in the 'batchFAT' WAR, not the
         * 'BonusPayout' WAR. So while the BonusPayout WAR is a self-contained unit of function, the ability to
         * create the application tables it uses is not itself part of the BonusPayout WAR.
         *
         * Seems like there's no point in re-packaging a DB servlet in BonusPayout.war at the moment.
         */
        loadAndExecuteSql("jdbc/BonusPayoutDS",
                          "common/BonusPayout.derby.ddl",
                          DFLT_PERSISTENCE_SCHEMA,
                          DFLT_TABLE_PREFIX);

    }

    public static void tearDown() throws Exception {
        if (server != null && server.isStarted()) {
            server.stopServer("CWWKY0011W");
        }
    }

    @Override
    public String getContextRoot() {
        return CTX_ROOT;
    }

    @Test
    public void testBonusPayouttxt2db() throws Exception {
        test("BonusPayoutServlet", "generateFileNameRoot=" + getTempFilePrefix("bonuspayout.outfile"));
    }

    @Test
    @Mode(TestMode.FULL)
    public void testBonusPayouttxt2dbPartitioned() throws Exception {
        test("BonusPayoutServlet", "dsJNDI=java:comp/env/jdbc/BonusPayoutDS&junit.partitioned=true");
    }

    @Test
    @ExpectedFFDC({ "java.lang.IllegalStateException", "com.ibm.jbatch.container.exception.BatchContainerRuntimeException" })
    public void testBonusPayouttxt2dbRestartOnce() throws Exception {
        test("BonusPayoutServlet", "junit.numRestarts=1");
    }

    @Test
    @ExpectedFFDC({ "java.lang.IllegalStateException", "com.ibm.jbatch.container.exception.BatchContainerRuntimeException" })
    public void testBonusPayouttxt2dbRestartOncePartitioned() throws Exception {
        test("BonusPayoutServlet", "junit.partitioned=true&junit.numRestarts=1");
    }

    @Test
    @Mode(TestMode.FULL)
    @ExpectedFFDC({ "java.lang.IllegalStateException", "com.ibm.jbatch.container.exception.BatchContainerRuntimeException" })
    public void testBonusPayouttxt2dbRestartTwice() throws Exception {
        test("BonusPayoutServlet", "junit.numRestarts=2");
    }

    @Test
    @ExpectedFFDC({ "java.lang.IllegalStateException", "com.ibm.jbatch.container.exception.BatchContainerRuntimeException" })
    @Mode(TestMode.FULL)
    public void testBonusPayouttxt2dbRestartTwicePartitioned() throws Exception {
        test("BonusPayoutServlet", "junit.partitioned=true&junit.numRestarts=2");
    }

    @Test
    public void testBonusPayouttxt2dbCursorHold() throws Exception {
        test("BonusPayoutServlet", "junit.cursorhold=true");
    }

    @Test
    @Mode(TestMode.FULL)
    public void testBonusPayouttxt2dbPartitionedCursorHold() throws Exception {
        test("BonusPayoutServlet", "junit.cursorhold=true&junit.partitioned=true");
    }

}
