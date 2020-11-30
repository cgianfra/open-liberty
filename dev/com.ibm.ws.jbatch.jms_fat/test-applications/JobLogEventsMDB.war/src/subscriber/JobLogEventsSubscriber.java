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
package subscriber;

import java.io.StringReader;
import java.util.logging.Logger;

import javax.ejb.MessageDriven;
import javax.inject.Inject;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.TextMessage;
import javax.json.Json;
import javax.json.JsonObject;

import cache.JobLogInfoStore;
import utils.JobLogEventsMDBConstants;

/**
 * An MDB to subscribe to job log events and help with FAT testing
 */
@MessageDriven
public class JobLogEventsSubscriber implements MessageListener {

    private final Logger logger = Logger.getLogger(JobLogEventsMDBConstants.LOGGER);
    private static final String[] testTopicRoot = new String[] { "batch", "Fred", "" };
    private static final String msgToWaitJobLogEvent = "topic://batch/jobs/execution/jobLogPart";
    private static final String msgToWaitExecutorCheckpoint = "topic://batch/jobs/execution/step/checkpoint";

    @Inject
    JobLogInfoStore cache;

    @Override
    public void onMessage(Message msg) {
        try {

            String destination = msg.getJMSDestination().toString();
            TextMessage txtMsg = (TextMessage) msg;

            int i = 0;
            while (i < testTopicRoot.length) {
                logger.fine("Received event : " + destination + " checking against resolved type: " + resolveTopicRoot(testTopicRoot[i], msgToWaitJobLogEvent));

                if (destination.equalsIgnoreCase(resolveTopicRoot(testTopicRoot[i], msgToWaitJobLogEvent))) {

                    JsonObject jobLogEventObject = Json.createReader(new StringReader(txtMsg.getText())).readObject();

                    //These 4 props should never be null in a job log event message
                    Long instanceID = txtMsg.getLongProperty("com_ibm_ws_batch_internal_jobInstanceId");
                    Long execID = txtMsg.getLongProperty("com_ibm_ws_batch_internal_jobExecutionId");
                    int partNumber = jobLogEventObject.getInt("partNumber");
                    String appName = jobLogEventObject.getString("appName");

                    String splitName = null;
                    String flowName = null;
                    String stepName = null;
                    Integer partitionNum = null;

                    //If a partition number exists then there will be a step name as well
                    if (jobLogEventObject.containsKey("partitionNumber")) {
                        partitionNum = jobLogEventObject.getInt("partitionNumber");
                        stepName = jobLogEventObject.getString("stepName");
                        cache.updateNamedCount(Integer.toString(partitionNum));
                    }

                    //If a flow name exists then there will be a split name as well
                    if (jobLogEventObject.containsKey("flowName")) {
                        splitName = jobLogEventObject.getString("splitName");
                        flowName = jobLogEventObject.getString("flowName");
                        cache.updateNamedCount(flowName);
                    }

                    //If the event is not a partition log or a split-flow log then it must be a top level job log
                    if (partitionNum == null && flowName == null) {
                        cache.updateNamedCount("TopLevelJob");
                    }

                    //Keep track of the total count of all job log events as well
                    cache.incrementTotalCount();

                    logger.fine("Recieved event : " + " instance ID is: " + instanceID + ", execution ID is: " + execID + ", app name is: " + appName + ", part count is: "
                                + partNumber);

                    //stop looping if topic matched
                    break;
                }
                //Handle checkpoint events to check for stepExecutionID
                else if (destination.equalsIgnoreCase(resolveTopicRoot(testTopicRoot[i], msgToWaitExecutorCheckpoint))) {
                    if (txtMsg.propertyExists("com_ibm_ws_batch_internal_stepExecutionId")) {
                        cache.updateNamedCount("stepExecutionIdFromCheckpoint");
                        logger.fine("Recieved event : " + "type is: checkpoint");
                    }

                    //stop looping if topic matched
                    break;
                }

                i++;
            }

        } catch (JMSException e) {
            e.printStackTrace();
        }

    }

    private static String resolveTopicRoot(String topicRoot, String defaultTopicRoot) {
        String x;

        if (topicRoot != null) {
            x = defaultTopicRoot.replaceFirst("batch" + ((topicRoot.isEmpty()) ? "/" : ""), topicRoot);
            return x;
        }
        return defaultTopicRoot;
    }

}
