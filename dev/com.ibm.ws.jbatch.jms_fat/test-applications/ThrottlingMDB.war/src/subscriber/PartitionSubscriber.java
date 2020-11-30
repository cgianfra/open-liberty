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

import cache.RunningStatusStore;
import utils.ThrottlingMDBConstants;

/**
 * An MDB to get the subscribe to partition events
 */
@MessageDriven
public class PartitionSubscriber implements MessageListener {

    private final Logger logger = Logger.getLogger(ThrottlingMDBConstants.LOGGER);

    @Inject
    RunningStatusStore cache;

    @Override
    public void onMessage(Message msg) {
        try {

            String destination = msg.getJMSDestination().toString();

            JsonObject partition = Json.createReader(new StringReader(((TextMessage) msg).getText())).readObject();
            String partitionKey = getPartitionKey(partition);

            if (isStarted(destination)) {
                cache.partitionStarted(partitionKey);
            } else {
                cache.partitionEnded(partitionKey);
            }

            logger.fine("Recieved event : " + destination);
            logger.fine(partition.toString());

        } catch (JMSException e) {
            e.printStackTrace();
        }

    }

    private String getPartitionKey(JsonObject partition) {
        StringBuilder sb = new StringBuilder();
        sb.append(partition.getInt("partitionNumber"));
        sb.append(":");
        sb.append(partition.getString("stepName"));
        sb.append(":");
        sb.append(partition.getJsonNumber("instanceId").longValue());
        return sb.toString();
    }

    /**
     * @return true if partition/started topic received. false for other partition topics
     */
    private boolean isStarted(String topic) {
        if (topic.contains("started")) {
            return true;
        } else {
            return false;
        }
    }

}
