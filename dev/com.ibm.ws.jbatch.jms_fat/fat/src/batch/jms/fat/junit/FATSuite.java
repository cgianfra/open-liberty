/*
 * IBM Confidential
 *
 * OCO Source Materials
 *
 * Copyright IBM Corp. 2012
 *
 * The source code for this program is not published or otherwise divested
 * of its trade secrets, irrespective of what has been deposited with the
 * U.S. Copyright Office.
 */
package batch.jms.fat.junit;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

import componenttest.rules.repeater.JakartaEE9Action;
import componenttest.rules.repeater.RepeatTests;
import componenttest.topology.database.DerbyNetworkUtilities;

/**
 * Collection of all example tests
 */
@RunWith(Suite.class)
/*
 * The classes specified in the @SuiteClasses annotation
 * below should represent all of the test cases for this FAT.
 */
@SuiteClasses({
//                BatchJmsSingleServerTest.class,
                //BatchJmsBadConfigTest.class,
//                BatchJmsNonDefaultConfigTest.class,
//                BatchJmsInstanceStateTest.class,
 //                BatchJmsMultiServersTest.class,
//                BatchJms2EndpointsTest.class,
 //                BatchJmsEventsSingleServerTest.class,
//                BatchJmsMultiJVMPartitionsTest.class,
//                BatchJmsMultiJVMPartitionSelectorConfigTest.class,
//                BatchJmsZeroExecutionsTest.class,
//                BatchJmsMultiJVMPartitionsStopDispatcherTest.class,
//                BatchJmsMultiJVMThrottlingTest.class,
//                BatchJmsMultiJVMUserDataRollbackTest.class,
//                BatchMultiExecAndDynamicMSTest.class,
//                BatchJmsJobLogEventsSingleServerTest.class,
                BatchPartitionDBTest.class,
 //                BatchPartitionOldDBTablesTest.class,
//                BatchPurgeTest.class,
 //                BatchNonLocalWithSSLTest.class,
 //                BatchNonLocalNoSSLTest.class,
 //                BatchRemotePartitionsTest.class

//Commented out until remote job logging is enabled
//               BatchJmsMultiJVMJobLogEventsTest.class

//The 2 tests below should only be run manually because they are not
//suitable to run in build framework.
//               BatchJmsWmqClientModeTest.class,
//               BatchJmsInstanceStateWmqTest.class
})
public class FATSuite {
    @ClassRule
    public static RepeatTests r = RepeatTests.with(new JakartaEE9Action());
    //outModification().andWith

    @BeforeClass
    public static void beforeSuite() throws Exception {
        DerbyNetworkUtilities.startDerbyNetwork();
    }

    @AfterClass
    public static void afterSuite() throws Exception {
        DerbyNetworkUtilities.stopDerbyNetwork();
    }
}
