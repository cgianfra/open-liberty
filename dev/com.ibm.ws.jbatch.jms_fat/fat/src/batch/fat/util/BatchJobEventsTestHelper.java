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
package batch.fat.util;

/**
 * Define expected messages that the job event tests uses
 * 
 */
public class BatchJobEventsTestHelper {
    public static final String[] msgToWaitDispatcher = new String[] {
                                                                     "JMSDestination=topic://batch/jobs/instance/submitted",
                                                                     "JMSDestination=topic://batch/jobs/instance/jms_queued",
                                                                     "JMSDestination=topic://batch/jobs/instance/jms_consumed" };
    public static final String[] msgToWaitExecutorJobCompleted = new String[] {
                                                                               "JMSDestination=topic://batch/jobs/execution/starting",
                                                                               "JMSDestination=topic://batch/jobs/instance/dispatched",
                                                                               "JMSDestination=topic://batch/jobs/execution/started",
                                                                               "JMSDestination=topic://batch/jobs/execution/completed",
                                                                               "JMSDestination=topic://batch/jobs/instance/completed" };

    public static final String[] msgToWaitExecutorJobFailed = new String[] {
                                                                            "JMSDestination=topic://batch/jobs/execution/starting",
                                                                            "JMSDestination=topic://batch/jobs/instance/dispatched",
                                                                            "JMSDestination=topic://batch/jobs/execution/started",
                                                                            "JMSDestination=topic://batch/jobs/execution/failed",
                                                                            "JMSDestination=topic://batch/jobs/instance/failed" };

    public static final String[] msgToWaitExecutorJobRestarted = new String[] {
                                                                               "JMSDestination=topic://batch/jobs/execution/starting",
                                                                               "JMSDestination=topic://batch/jobs/instance/dispatched",
                                                                               "JMSDestination=topic://batch/jobs/execution/restarting",
                                                                               "JMSDestination=topic://batch/jobs/execution/completed",
                                                                               "JMSDestination=topic://batch/jobs/instance/completed" };

    public static final String[] msgToWaitJobPurged = new String[] {
                                                                    "JMSDestination=topic://batch/jobs/instance/purged" };

    public static final String[] msgToWaitExecutorJobStopped = new String[] {
                                                                             "JMSDestination=topic://batch/jobs/execution/starting",
                                                                             "JMSDestination=topic://batch/jobs/instance/dispatched",
                                                                             "JMSDestination=topic://batch/jobs/execution/started",
                                                                             "JMSDestination=topic://batch/jobs/instance/stopping",
                                                                             "JMSDestination=topic://batch/jobs/execution/stopping",
                                                                             "JMSDestination=topic://batch/jobs/execution/stopped",
                                                                             "JMSDestination=topic://batch/jobs/instance/stopped" };

    public static final String[] msgToWaitExecutorStepCompleted = new String[] {
                                                                                "JMSDestination=topic://batch/jobs/execution/starting",
                                                                                "JMSDestination=topic://batch/jobs/instance/dispatched",
                                                                                "JMSDestination=topic://batch/jobs/execution/started",
                                                                                "JMSDestination=topic://batch/jobs/execution/step/started",
                                                                                "JMSDestination=topic://batch/jobs/execution/step/completed",
                                                                                "JMSDestination=topic://batch/jobs/execution/completed",
                                                                                "JMSDestination=topic://batch/jobs/instance/completed" };

    //3 partitions job
    public static final String[] msgToWaitOnExecutorBegan = new String[] {
                                                                          "JMSDestination=topic://batch/jobs/execution/starting",
                                                                          "JMSDestination=topic://batch/jobs/instance/dispatched",
                                                                          "JMSDestination=topic://batch/jobs/execution/started",
                                                                          "JMSDestination=topic://batch/jobs/execution/step/started" };

    public static final String[] msgToWaitOnExecutorEnded = new String[] {
                                                                          "JMSDestination=topic://batch/jobs/execution/step/completed",
                                                                          "JMSDestination=topic://batch/jobs/execution/completed",
                                                                          "JMSDestination=topic://batch/jobs/instance/completed"
    };

    public static final String msgToWaitPartitionStarted = new String("JMSDestination=topic://batch/jobs/execution/partition/started");

    public static final String msgToWaitPartitionEnded = new String("JMSDestination=topic://batch/jobs/execution/partition/completed");

    /**
     * there are multiple step events and checkpoint events, we just check for one
     */
    public static final String[] msgToWaitExecutorCheckpoint = new String[] {
                                                                             "JMSDestination=topic://batch/jobs/execution/starting",
                                                                             "JMSDestination=topic://batch/jobs/instance/dispatched",
                                                                             "JMSDestination=topic://batch/jobs/execution/started",
                                                                             "JMSDestination=topic://batch/jobs/execution/step/started",
                                                                             "JMSDestination=topic://batch/jobs/execution/step/checkpoint",
                                                                             "JMSDestination=topic://batch/jobs/execution/step/completed",
                                                                             "JMSDestination=topic://batch/jobs/execution/completed",
                                                                             "JMSDestination=topic://batch/jobs/instance/completed" };

    /**
     * Job log events will when a new job log part file is created or when a job is removed from the thread
     */
    public static final String msgToWaitJobLogEvent = "JMSDestination=topic://batch/jobs/execution/jobLogPart";

    /**
     * test_simpleSplitFlow.xml has 1 split, which has 2 flows, and 2 steps in each flow.
     * the steps can finish in any order, so we will test by searching for # of occurrence
     */

    public static final String msgToWaitStepStarted = "JMSDestination=topic://batch/jobs/execution/step/started";
    public static final String msgToWaitStepCompleted = "JMSDestination=topic://batch/jobs/execution/step/completed";
    public static final String msgToWaitSplitFlowStarted = "JMSDestination=topic://batch/jobs/execution/split-flow/started";
    public static final String msgToWaitSplitFlowEnded = "JMSDestination=topic://batch/jobs/execution/split-flow/ended";

}
