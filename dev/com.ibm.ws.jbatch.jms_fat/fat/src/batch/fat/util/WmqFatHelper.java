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

import static org.junit.Assert.fail;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.Iterator;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.ibm.mq.MQException;
import com.ibm.mq.MQQueueManager;
import com.ibm.mq.constants.MQConstants;
import com.ibm.mq.headers.MQDataException;
import com.ibm.mq.headers.pcf.PCFException;
import com.ibm.mq.headers.pcf.PCFMessage;
import com.ibm.mq.headers.pcf.PCFMessageAgent;
import com.ibm.websphere.simplicity.RemoteFile;
import com.ibm.websphere.simplicity.log.Log;
import componenttest.topology.impl.LibertyServer;
import componenttest.topology.impl.LibertyServerFactory;

/**
 * Helper class to handle updating server.xml with appropriate mq info, connecting to mq, create queue and channel
 * This helper is adapted from WMQFATTest.java. The manageWMQQueues() is updated to clear
 * the queue if there is message left over before deleting.
 */
public class WmqFatHelper {

    protected static final LibertyServer server = LibertyServerFactory.getLibertyServer("BatchJmsWmqClientMode");

    private static ArrayList<Hashtable<String, String>> backups = new ArrayList<Hashtable<String, String>>();
    private static Hashtable<String, Hashtable<String, String>> ASProps;
    private static Hashtable<String, Hashtable<String, String>> CFProps;
    private static Hashtable<String, Hashtable<String, String>> QueueProps;
    private static ArrayList<String> QueueNames = new ArrayList<String>();
    private static ArrayList<String> ChannelNames = new ArrayList<String>();
    private static final String MQ_CONTROL_CHANNEL = "CONTROL_CHANNEL";
    private static String currentMQHost = "";
    private static Integer currentMQPort = 0;
    private static String prefix;
    protected static boolean connectedToWMQ = false;

    /**
     * There are 2 servers hosting WMQ, and their names are listed in the WMQServers.xml file
     * 
     * This logic is copy form com.ibm.ws.messaging.jms.wmq_fat
     * 
     * @throws Exception
     */
    private static void readBackup() throws Exception {
        RemoteFile file = server.getFileFromLibertyServerRoot("WMQServers.xml");

        log("readBackup", "Found backup file");

        InputStream is = file.openForReading();

        DocumentBuilder db = DocumentBuilderFactory.newInstance()
                        .newDocumentBuilder();
        Document doc = db.parse(is);

        Element root = doc.getDocumentElement();

        NodeList entries = root.getElementsByTagName("server");
        for (int i = 0; i < entries.getLength(); i++) {
            log("readBackup", "Found a backup server");
            // Create a hashtable for this backup server
            Hashtable<String, String> backupEntry = new Hashtable<String, String>();
            Node entry = entries.item(i);

            // Get all the properties under this backup server and put them into
            // the hashtable
            NamedNodeMap attrs = entry.getAttributes();
            for (int j = 0; j < attrs.getLength(); j++) {
                Node field = attrs.item(j);
                backupEntry.put(field.getNodeName(), field.getNodeValue());
                log("readBackup", "Found backup entry <" + field.getNodeName()
                                  + "=" + field.getNodeValue() + ">");
            }

            // Add the backup into the list of backup servers
            backups.add(backupEntry);
        }
    }

    /**
     * Set up and connect to WMQ server.
     * 
     * FAT environment has more than 1 WMQ server. And we don't know which one is active
     * at any giving time. So this method will read in the available server, update the
     * server.xml with the wmq server info, then try to connect to it. If we can't connect,
     * move on the the next server.
     */
    protected static void setUpWMQ() throws Exception {

        //read in the file that has wmq servers information
        readBackup();

        // True = update queues, null = no backup overrides
        Iterator<Hashtable<String, String>> iterBackups = backups.iterator();
        Hashtable<String, String> nextBackup = (iterBackups.hasNext() ? iterBackups.next() : null);
        updateServerXML(true, nextBackup);
        nextBackup = (iterBackups.hasNext() ? iterBackups.next() : null);
        connectedToWMQ = checkMQIsUp();
        while ((nextBackup != null) && !connectedToWMQ) {
            // False = dont update queues, nextBackup contains name/value pairs
            // of overrides
            updateServerXML(false, nextBackup);
            connectedToWMQ = checkMQIsUp();
            if (connectedToWMQ) {
                log("setUpWMQ", "Connected to backup. Phew");
                log("setUpWMQ", Arrays.toString(ChannelNames.toArray()));
            }
            nextBackup = (iterBackups.hasNext() ? iterBackups.next() : null);
        }
        if (!connectedToWMQ) {
            org.junit.Assert.fail("Unable to connect to any WMQ queue manager in the WMQServer.xml");
        }
        try {
            createWMQObjects();
        } catch (Throwable t) {
            t.printStackTrace();
            org.junit.Assert.fail("Error occured created required queues and channels for WMQ FAT test"
                                  + t);
        }
    }

    private static void createWMQObjects() throws Throwable {
        manageWMQQueues(true);
        manageWMQChannels(true);
    }

    protected static void deleteWMQObjects() throws Throwable {
        manageWMQQueues(false);
        manageWMQChannels(false);
    }

    private static void manageWMQChannels(boolean create) throws Throwable {
        Iterator<String> channelList = ChannelNames.iterator();

        String channelName = (channelList.hasNext() ? channelList.next() : null);
        PCFMessageAgent agent = new PCFMessageAgent(currentMQHost,
                        currentMQPort, MQ_CONTROL_CHANNEL);
        try {
            while (null != channelName) {
                PCFMessage pcfCmd = null;
                if (create) {
                    pcfCmd = new PCFMessage(MQConstants.MQCMD_CREATE_CHANNEL);
                    pcfCmd.addParameter(MQConstants.MQCACH_CHANNEL_NAME,
                                        channelName);
                    pcfCmd.addParameter(MQConstants.MQIACH_CHANNEL_TYPE,
                                        MQConstants.MQCHT_SVRCONN);
                    pcfCmd.addParameter(MQConstants.MQCACH_MCA_USER_ID, "mqm");
                    log("manageWMQChannels", "Creating channel : " + channelName);
                } else {
                    pcfCmd = new PCFMessage(MQConstants.MQCMD_DELETE_CHANNEL);
                    pcfCmd.addParameter(MQConstants.MQCACH_CHANNEL_NAME,
                                        channelName);
                    log("manageWMQChannels", "Deleting channel : " + channelName);
                }
                agent.send(pcfCmd);
                channelName = (channelList.hasNext() ? channelList.next() : null);

            }
        } catch (PCFException pcfe) {
            if (pcfe.reasonCode == MQConstants.MQRCCF_OBJECT_ALREADY_EXISTS && create) {
                log("manageWMQChannels", "The channel \"" + channelName
                                         + "\" already exists on the queue manager.");
            }
            if (pcfe.reasonCode == MQConstants.MQRC_UNKNOWN_CHANNEL_NAME && !create) {
                log("manageWMQChannels", "The channel \"" + channelName
                                         + "\" already been deleted from the queue manager.");
            }
        }
        agent.disconnect();

    }

    private static void manageWMQQueues(boolean create) throws Throwable {
        Iterator<String> queueList = QueueNames.iterator();
        String queueName = (queueList.hasNext() ? queueList.next() : null);
        log("manageWMQQueues", queueList.toString());
        PCFMessageAgent agent = new PCFMessageAgent(currentMQHost,
                        currentMQPort, MQ_CONTROL_CHANNEL);
        try {
            while (null != queueName) {
                PCFMessage pcfCmd = null;
                if (create) {
                    pcfCmd = new PCFMessage(MQConstants.MQCMD_CREATE_Q);
                    pcfCmd.addParameter(MQConstants.MQCA_Q_NAME, queueName);
                    pcfCmd.addParameter(MQConstants.MQIA_Q_TYPE,
                                        MQConstants.MQQT_LOCAL);
                    pcfCmd.addParameter(MQConstants.MQCA_Q_DESC,
                                        "Created for use by Liberty FAT");

                    log("manageWMQQueues", "Creating queue : " + queueName);
                } else {
                    //now delete
                    pcfCmd = new PCFMessage(MQConstants.MQCMD_DELETE_Q);
                    pcfCmd.addParameter(MQConstants.MQCA_Q_NAME, queueName);
                    log("manageWMQQueues", "Deleting queue : " + queueName);
                }
                agent.send(pcfCmd);
                queueName = (queueList.hasNext() ? queueList.next() : null);
            }
        } catch (PCFException pcfe) {
            if (pcfe.reasonCode == MQConstants.MQRCCF_OBJECT_ALREADY_EXISTS) {
                log("manageWMQQueues", "The queue \"" + queueName
                                       + "\" already exists on the queue manager.");
            } else if ((pcfe.reasonCode == MQConstants.MQRC_Q_NOT_EMPTY) && !create) {
                try {
                    log("manageWMQQueues", "clear queue : " + queueName);
                    PCFMessage pcfCmd = new PCFMessage(MQConstants.MQCMD_CLEAR_Q);
                    pcfCmd.addParameter(MQConstants.MQCA_Q_NAME, queueName);
                    agent.send(pcfCmd);

                    pcfCmd = new PCFMessage(MQConstants.MQCMD_DELETE_Q);
                    pcfCmd.addParameter(MQConstants.MQCA_Q_NAME, queueName);
                    log("manageWMQQueues", "Deleting queue : " + queueName);
                    agent.send(pcfCmd);
                } catch (PCFException ex) {
                    log("manageWMQQueues", "Unable to clear and delete queue " + queueName);
                    throw ex;
                }
            } else if (pcfe.reasonCode == MQConstants.MQRC_UNKNOWN_OBJECT_NAME && !create) {
                //it's ok if the queue is not there any more. Some other build must have deleted it
                log("manageWMQQueues", " create= " + create + ", unable to find queue " + queueName);
            } else {
                //Don't throw exception if we can't delete. Next test can re use it.
                pcfe.printStackTrace();
            }
        } catch (MQDataException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            agent.disconnect();
        }
    }

    /**
     * clear queue of any existing message
     * 
     * @throws Throwable
     */
    protected static void clearQueue() throws Exception {
        Iterator<String> queueList = QueueNames.iterator();
        String queueName = (queueList.hasNext() ? queueList.next() : null);
        log("clearQueue", queueList.toString());
        PCFMessageAgent agent = new PCFMessageAgent(currentMQHost,
                        currentMQPort, MQ_CONTROL_CHANNEL);
        try {
            while (null != queueName) {
                PCFMessage pcfCmd = null;
                try {
                    pcfCmd = new PCFMessage(MQConstants.MQCMD_CLEAR_Q);
                    pcfCmd.addParameter(MQConstants.MQCA_Q_NAME, queueName);
                    agent.send(pcfCmd);
                } catch (PCFException ex) {
                    log("clearQueue", "Unable to clear queue " + queueName);
                    ex.printStackTrace();
                    //throw ex;
                }
            }
        } catch (MQDataException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            agent.disconnect();
        }
    }

    private static boolean checkMQIsUp() {
        Hashtable currentQM = CFProps.get(CFProps.keys().nextElement());
        Hashtable<String, Comparable> properties = new Hashtable();
        properties.put("hostname", (String) currentQM.get("hostName"));
        properties
                        .put("port", Integer.parseInt((String) currentQM.get("port")));
        properties.put("channel", MQ_CONTROL_CHANNEL);
        String qmgrName = "qm1";
        boolean wmqRunning = false;
        try {
            log("checkMQIsUp", "Attempting connection with : "
                               + properties.toString());
            MQQueueManager qm = new MQQueueManager(qmgrName, properties);
            wmqRunning = true;
            log("checkMQIsUp", "Succesfully tested connection to current WMQ queue manager");
            currentMQHost = (String) currentQM.get("hostName");
            currentMQPort = Integer.parseInt((String) currentQM.get("port"));
        } catch (MQException e) {
            log("checkMQIsUp", "Failed connection test to current WMQ queue manager : "
                               + e.toString());
        }
        return wmqRunning;
    }

    private static void updateServerXML(Boolean updateQueues,
                                        Hashtable<String, String> backup) throws Exception {
        RemoteFile file = server.getFileFromLibertyServerRoot("server.xml");

        InputStream is = file.openForReading();

        DocumentBuilder db = DocumentBuilderFactory.newInstance()
                        .newDocumentBuilder();
        Document doc = db.parse(is);

        Element root = doc.getDocumentElement();

        ASProps = getWMQInfo(root, "jmsActivationSpec", updateQueues, backup);
        CFProps = getWMQInfo(root, "jmsConnectionFactory", updateQueues, backup);
        QueueProps = getWMQInfo(root, "jmsQueue", updateQueues, backup);

        is.close();

        OutputStream os = file.openForWriting(false);
        Transformer trans = TransformerFactory.newInstance().newTransformer();
        DOMSource docToWrite = new DOMSource(doc);
        StreamResult result = new StreamResult(os);
        trans.transform(docToWrite, result);
        os.close();
    }

    private static Hashtable<String, Hashtable<String, String>> getWMQInfo(
                                                                           Element root, String topLevel, Boolean prefixWMQObjects,
                                                                           Hashtable<String, String> overrides) {

        Hashtable<String, Hashtable<String, String>> props = new Hashtable<String, Hashtable<String, String>>();
        NodeList entries = root.getElementsByTagName(topLevel);
        for (int i = 0; i < entries.getLength(); i++) {
            Hashtable<String, String> wmqObject = new Hashtable<String, String>();
            Node entry = entries.item(i);
            NamedNodeMap attrs = entry.getAttributes();
            Node nameNode = attrs.getNamedItem("jndiName");
            if (nameNode == null) {
                nameNode = attrs.getNamedItem("id");
            }
            String name = nameNode.getNodeValue();
            log("getWMQInfo", "Found object with jndiname of " + name);
            NodeList wmqProps = ((Element) entry)
                            .getElementsByTagName("properties.wmqJms");
            for (int j = 0; j < wmqProps.getLength(); j++) {
                Node properties = wmqProps.item(j);
                NamedNodeMap fields = properties.getAttributes();
                for (int k = 0; k < fields.getLength(); k++) {
                    Node field = fields.item(k);
                    String fieldName = field.getNodeName();

                    // Check for overrides
                    if ((null != overrides)
                        && (overrides.containsKey(fieldName))) {
                        field.setNodeValue(overrides.get(fieldName));
                        log("getWMQInfo", "OVERRIDING " + fieldName + " with "
                                          + overrides.get(fieldName));
                    }

                    if (prefixWMQObjects) {
                        if (field.getNodeName().equals("baseQueueName")) {
                            field.setNodeValue(prefix + "_"
                                               + field.getNodeValue());
                            if (!QueueNames.contains(field.getNodeValue()))
                                QueueNames.add(field.getNodeValue());
                        }
                        if (field.getNodeName().equals("channel")) {
                            field.setNodeValue(prefix + "_"
                                               + field.getNodeValue());
                            if (!ChannelNames.contains(field.getNodeValue())) {
                                ChannelNames.add(field.getNodeValue());
                                log("getWMQInfo", "adding channel name : "
                                                  + field.getNodeValue());
                            }
                        }
                    }

                    wmqObject.put(fieldName, field.getNodeValue());
                    log("getWMQInfo", "Found field <" + fieldName + "="
                                      + field.getNodeValue() + ">");
                }
            }
            props.put(name, wmqObject);
        }
        return props;
    }

    /**
     * 
     */
    protected static void tearDownWMQ() {
        if (connectedToWMQ) {
            try {
                deleteWMQObjects();
            } catch (Throwable t) {
                t.printStackTrace();
                fail("Error occured deleting required queues and channels for WMQ FAT test"
                     + t);
            }
        }

    }

    /**
     * helper for simple logging.
     */
    private static void log(String method, String msg) {
        Log.info(WmqFatHelper.class, method, msg);
    }
}
