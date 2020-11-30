package mdb;

import javax.ejb.MessageDriven;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.TextMessage;

@MessageDriven
public class TopicSpaceMDB implements MessageListener {

    @Override
    public void onMessage(Message message) {
        try {
            TextMessage msg = (TextMessage) message;

            //System.out.println("TopicSpaceMDB:" + message);

            System.out.println("Message Received in MDB !!!" + msg.getText());

            System.out.println("JMSDestination=" + msg.getJMSDestination().toString());
        } catch (Exception x) {
            throw new RuntimeException(x);
        }
    }
}
