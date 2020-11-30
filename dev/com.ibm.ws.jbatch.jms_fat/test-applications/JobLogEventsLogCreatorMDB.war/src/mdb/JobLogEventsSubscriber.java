/*
 * Copyright 2016 International Business Machines Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package mdb;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringReader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Logger;

import javax.ejb.MessageDriven;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.TextMessage;
import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;

/**
 * An MDB to subscribe to job log events and create the job log directory structure
 */
@MessageDriven
public class JobLogEventsSubscriber implements MessageListener {

    private final Logger logger = Logger.getLogger("JobLogEventsSubscriber");
    private static final String[] testTopicRoot = new String[] { "batch", "Fred", "" };
    private static final String msgToWaitJobLogEvent = "topic://batch/jobs/execution/jobLogPart";

    @Override
    public void onMessage(Message msg) {
        try {

            String destination = msg.getJMSDestination().toString();
            //Only want the job log events. This can also be set in the server.xml where the app is configured
            int j = 0;
            while (j < testTopicRoot.length) {
                logger.fine("Received event : " + destination + " checking against resolved type: " + resolveTopicRoot(testTopicRoot[j], msgToWaitJobLogEvent));

                if (destination.equalsIgnoreCase(resolveTopicRoot(testTopicRoot[j], msgToWaitJobLogEvent))) {
                    TextMessage txtMsg = (TextMessage) msg;

                    JsonObject jobLogEventObject = Json.createReader(new StringReader(txtMsg.getText())).readObject();

                    //These 4 variables should never be null when retrieved from a job log event message
                    long instanceID = jobLogEventObject.getJsonNumber("instanceId").longValue();
                    long execID = jobLogEventObject.getJsonNumber("executionId").longValue();
                    int partNumber = jobLogEventObject.getInt("partNumber");
                    String appName = jobLogEventObject.getString("appName");

                    //The job log event message returns the log content as an array of all the lines
                    StringBuilder sb = new StringBuilder();
                    JsonArray logContentArray = jobLogEventObject.getJsonArray("contents");
                    //Rebuild the entire log file line by line
                    for (int i = 0; i < logContentArray.size(); i++) {
                        String line = logContentArray.getString(i);
                        sb.append(line);
                        sb.append(System.getProperty("line.separator"));
                    }
                    String logContent = sb.toString();

                    //Create the base path to resemble the following example directory structure:
                    //Example -> %Path_Of_Listening_Server%\JobLogEvents\simple_batch_job\2016-05-14\instance.1\execution.1
                    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
                    String currentDate = dateFormat.format(new Date(txtMsg.getJMSTimestamp()));
                    String basePath = "JobLogEvents" + File.separator + appName + File.separator + currentDate + File.separator + "instance." + instanceID + File.separator
                                      + "execution." + execID;

                    String splitName = null;
                    String flowName = null;
                    String stepName = null;
                    Integer partitionNum = null;
                    File logDirectory = null;

                    //If a partition number exists then there will be a step name as well
                    if (jobLogEventObject.containsKey("partitionNumber")) {
                        partitionNum = jobLogEventObject.getInt("partitionNumber");
                        stepName = jobLogEventObject.getString("stepName");
                        logDirectory = new File(basePath + File.separator + stepName + File.separator + partitionNum);
                    }
                    //If a flow name exists then there will be a split name as well
                    else if (jobLogEventObject.containsKey("flowName")) {
                        splitName = jobLogEventObject.getString("splitName");
                        flowName = jobLogEventObject.getString("flowName");
                        logDirectory = new File(basePath + File.separator + splitName + File.separator + flowName);
                    }
                    //If the event is not a partition log or a split-flow log then it must be a top level job log
                    else {
                        logDirectory = new File(basePath);
                    }

                    //Create the directory structure
                    boolean directoryExistsOrIsCreated = true;
                    if (logDirectory != null && !logDirectory.isDirectory()) {
                        directoryExistsOrIsCreated = logDirectory.mkdirs();
                    }

                    //Create the log file under the created or existing directory
                    if (logDirectory != null && directoryExistsOrIsCreated) {
                        try {
                            PrintWriter out = new PrintWriter(logDirectory.getAbsolutePath() + File.separator + "part" + partNumber + ".log");
                            out.print(logContent);
                            out.close();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }

                    //stop looping topic matched
                    break;
                }

                j++;
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
