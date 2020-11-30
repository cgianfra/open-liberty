package web;

/*
 * IBM Confidential OCO Source Material
 * 5724-H88, 5724-J08, 5724-I63, 5655-W65, 5724-H89, 5722-WE2   Copyright IBM Corp., 2012, 2013
 * The source code for this program is not published or otherwise divested
 * of its trade secrets, irrespective of what has been deposited with the
 * U. S. Copyright Office.
 *
 */

import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.InvocationTargetException;

import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.MapMessage;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.jms.Topic;
import javax.jms.TopicConnection;
import javax.jms.TopicConnectionFactory;
import javax.jms.TopicPublisher;
import javax.jms.TopicSession;
import javax.naming.InitialContext;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.ibm.ws.jbatch.jms.internal.BatchJmsConstants;

@WebServlet("/BatchJmsServlet")
public class BatchJmsServlet extends HttpServlet {

    private static final long serialVersionUID = 7709282314904580334L;

    /**
     * Message written to servlet to indicate that is has been successfully invoked.
     */
    public static final String SUCCESS_MESSAGE = "COMPLETED SUCCESSFULLY";

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String action = request.getParameter("action");
        PrintWriter out = response.getWriter();
        out.println("Starting " + action + "<br>");

        try {
            getClass().getMethod(action, HttpServletRequest.class, HttpServletResponse.class).invoke(this, request, response);
            out.println(action + " COMPLETED SUCCESSFULLY");

        } catch (Throwable x) {
            if (x instanceof InvocationTargetException)
                x = x.getCause();
            out.println("<pre>ERROR in " + action + ":");
            x.printStackTrace(out);
            out.println("</pre>");
        }
    }

    /**
     * Send a MapMessage message, do not set BatchJmsConstants.JOB_OPERATION string property
     * 
     * @param request
     * @param response
     */
    public void sendNoOpMapMessageToQueue(HttpServletRequest request, HttpServletResponse response) {
        String strAppName = request.getParameter("appName");
        String cfJndi = request.getParameter("cfJndi");
        String queueJndi = request.getParameter("queueJndi");

        PrintWriter out = null;
        Connection myConn = null;
        try {
            out = response.getWriter();
            out.println("Publishing a start message for appName=" + strAppName);

            ConnectionFactory cf = (ConnectionFactory) new InitialContext().lookup(cfJndi);
            Queue jmsQ = (Queue) new InitialContext().lookup(queueJndi);

            myConn = cf.createConnection();
            //Create a session within the connection.
            Session jmsSession = myConn.createSession(false, Session.AUTO_ACKNOWLEDGE);

            //Create a message producer.
            MessageProducer myMsgProducer = jmsSession.createProducer(jmsQ);

            // Create and send a message to the queue.
            MapMessage jmsMsg = null;

            jmsMsg = jmsSession.createMapMessage();

            byte[] securityContext = new String("testcontext").getBytes();
            //BatchJmsMessageHelper.setSecurityContextToJmsMessage(securityContext, jmsMsg);
            //BatchJmsMessageHelper.setAmcNameToMessage(jmsMsg, AmcName.parse(strAppName));

            jmsMsg.setBytes(BatchJmsConstants.PROPERTY_NAME_SECURITY_CONTEXT, securityContext);
            jmsMsg.setStringProperty(BatchJmsConstants.PROPERTY_NAME_APP_NAME, strAppName);

            // skip the job operation, so message will be discard on receiving side

            myMsgProducer.send(jmsMsg);
            out.print(jmsMsg.toString());

        } catch (Exception e) {
            out.println("Stack Trace:<br/>");
            e.printStackTrace(out);

        } finally {
            try {
                myConn.close();
            } catch (JMSException e) {

            }
        }
    }

    /**
     * Send a jms ObjectMessage type to the queue.
     * 
     * 
     * @param request
     * @param response
     */
    public void sendInvalidMessageToQueue(HttpServletRequest request, HttpServletResponse response) {
        String strAppName = request.getParameter("appName");
        String operation = request.getParameter("operation");
        PrintWriter out = null;
        Connection myConn = null;

        String cfJndi = request.getParameter("cfJndi");
        String queueJndi = request.getParameter("queueJndi");

        try {
            out = response.getWriter();
            out.println("Publishing a jms sendInvalidMessageToQueue for appName=" + strAppName + "cfJndi=" + cfJndi + "queueJndi=" + queueJndi);

            ConnectionFactory cf = (ConnectionFactory) new InitialContext().lookup(cfJndi);
            Queue jmsQ = (Queue) new InitialContext().lookup(queueJndi);

            myConn = cf.createConnection();
            //Create a session within the connection.
            Session jmsSession = myConn.createSession(false, Session.AUTO_ACKNOWLEDGE);

            //Create a message producer.
            MessageProducer myMsgProducer = jmsSession.createProducer(jmsQ);

            // Create and send a message to the queue.
            TextMessage jmsMsg = jmsSession.createTextMessage();

            jmsMsg.setStringProperty(BatchJmsConstants.PROPERTY_NAME_APP_NAME, strAppName);
            //test
            //BatchJmsMessageHelper.setExecutionIdToJmsMessage(1L, jmsSession.createMapMessage());

            myMsgProducer.send(jmsMsg);
            out.print(jmsMsg.toString());

        } catch (Exception e) {
            out.println("Stack Trace:<br/>");
            e.printStackTrace(out);
        } finally {
            try {
                myConn.close();
            } catch (JMSException e) {

            }
        }
    }

    /**
     * Send a MapMessage message, set BatchJmsConstants.JOB_OPERATION string property
     * 
     * @param request
     * @param response
     */
    public void sendMapMessageToQueue(HttpServletRequest request, HttpServletResponse response) {
        String strAppName = request.getParameter("appName");
        String operation = request.getParameter("operation");
        String cfJndi = request.getParameter("cfJndi");
        String queueJndi = request.getParameter("queueJndi");

        PrintWriter out = null;
        Connection myConn = null;
        try {
            out = response.getWriter();
            out.println("Send a start message for AppName=" + strAppName);

            ConnectionFactory cf = (ConnectionFactory) new InitialContext().lookup(cfJndi);
            Queue jmsQ = (Queue) new InitialContext().lookup(queueJndi);

            myConn = cf.createConnection();
            //Create a session within the connection.
            Session jmsSession = myConn.createSession(false, Session.AUTO_ACKNOWLEDGE);

            //Create a message producer.
            MessageProducer myMsgProducer = jmsSession.createProducer(jmsQ);

            // Create and send a message to the queue.
            MapMessage jmsMsg = jmsSession.createMapMessage();

            byte[] securityContext = new String("testcontext").getBytes();
            jmsMsg.setBytes(BatchJmsConstants.PROPERTY_NAME_SECURITY_CONTEXT, securityContext);

            jmsMsg.setStringProperty(BatchJmsConstants.PROPERTY_NAME_APP_NAME, strAppName);
            jmsMsg.setString(BatchJmsConstants.PROPERTY_NAME_JOB_OPERATION, operation);
            //BatchJmsMessageHelper.setSecurityContextToJmsMessage(securityContext, jmsMsg);
//            BatchJmsMessageHelper.setAmcNameToMessage(jmsMsg, AmcName.parse(strAppName));
//            BatchJmsMessageHelper.setOperationTypeToJmsMessage(operation, jmsMsg);

            myMsgProducer.send(jmsMsg);
            out.print(jmsMsg.toString());

        } catch (Exception e) {
            out.println("Stack Trace:<br/>");
            e.printStackTrace(out);

        } finally {
            try {
                myConn.close();
            } catch (JMSException e) {

            }
        }
    }

    /**
     * Send a MapMessage message, set BatchJmsConstants.JOB_OPERATION string property
     * 
     * @param request
     * @param response
     */
    public void publishTopic(HttpServletRequest request, HttpServletResponse response) {
        String topicName = request.getParameter("topicName");
        String operation = request.getParameter("operation");
        String cfJndi = request.getParameter("cfJndi");
        String topicJndi = request.getParameter("topicJndi");

        PrintWriter out = null;
        TopicConnection myConn = null;
        try {
            out = response.getWriter();
            out.println("Publishing a job Event " + topicName);

            TopicConnectionFactory cf = (TopicConnectionFactory) new InitialContext().lookup(cfJndi);
            Topic topic = (Topic) new InitialContext().lookup(topicJndi);

            myConn = (TopicConnection) cf.createConnection();

            TopicSession session = myConn.createTopicSession(false, javax.jms.Session.AUTO_ACKNOWLEDGE);
            TopicPublisher publisher = session.createPublisher(topic);
            publisher.publish(session.createTextMessage("MDB Topic message: Job Ended"));

        } catch (Exception e) {
            out.println("Stack Trace:<br/>");
            e.printStackTrace(out);

        } finally {
            try {
                myConn.close();
            } catch (JMSException e) {

            }
        }
    }

}
