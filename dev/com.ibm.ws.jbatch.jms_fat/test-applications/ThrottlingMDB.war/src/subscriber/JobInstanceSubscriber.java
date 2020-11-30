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
public class JobInstanceSubscriber implements MessageListener {

    private final Logger logger = Logger.getLogger(ThrottlingMDBConstants.LOGGER);

    @Inject
    RunningStatusStore cache;

    @Override
    public void onMessage(Message msg) {
        try {

            String destination = msg.getJMSDestination().toString();
            String txt = ((TextMessage) msg).getText();
            JsonObject jobInstance = Json.createReader(new StringReader(txt)).readObject();

            if (isConsumed(destination)) {
                cache.jobStarted(jobInstance.getJsonNumber("instanceId").longValue());
            } else if (isEnded(destination)) {
                cache.jobEnded(jobInstance.getJsonNumber("instanceId").longValue());
            }

            logger.fine("Recieved event : " + destination);
            logger.fine(jobInstance.toString());

        } catch (JMSException e) {
            e.printStackTrace();
        }

    }

    /**
     * @return true if instance/jms_consumed topic received.
     */
    private boolean isConsumed(String topic) {
        if (topic.contains("jms_consumed")) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * @return true if partition/started topic received. false for other partition topics
     */
    private boolean isEnded(String topic) {
        if (topic.contains("completed") || topic.contains("failed") || topic.contains("stopped")) {
            return true;
        } else {
            return false;
        }
    }

}
