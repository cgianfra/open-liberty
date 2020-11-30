/*
 * Copyright 2012 International Business Machines Corp.
 * 
 * See the NOTICE file distributed with this work for additional information
 * regarding copyright ownership. Licensed under the Apache License, 
 * Version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.ibm.jbatch.artifacts.specialized;

import java.util.logging.Logger;

import javax.batch.api.AbstractBatchlet;
import javax.batch.api.BatchProperty;
import javax.inject.Inject;
import javax.naming.InitialContext;

/**
 * A batchlet to print out extra lines in the log to allow for easier control of the job log size
 */
@javax.inject.Named("logControlBatchlet")
public class LogControlBatchlet extends AbstractBatchlet {

    private final static Logger logger = Logger.getLogger("LogControlBatchlet");

    private volatile boolean stopRequested = false;

    @Inject
    @BatchProperty(name = "log.lines.to.print")
    String logLinesToPrint;
    private int log_lines_to_print = 10; //default is 10 lines

    @Inject
    @BatchProperty(name = "force.failure")
    String forceFailure;

    /**
     * Main entry point.
     */
    @Override
    public String process() throws Exception {

        logger.fine("process: entry");

        Object serverName = new InitialContext().lookup("serverName");

        if (forceFailure != null && !forceFailure.isEmpty()) {
            //Fail the job
            throw new RuntimeException("Forcing the job to fail");
        }

        if (logLinesToPrint != null) {
            log_lines_to_print = Integer.parseInt(logLinesToPrint);
        }

        int i;
        //Multiplying by 10 to add more granularity and check stopRequested more frequently
        for (i = 0; i < log_lines_to_print; ++i) {
            logger.fine("Print number " + i + " to the log to manipulate the size of the log file");
        }

        String status = "LogControlBatchlet:i=" + i + ";stopRequested=" + stopRequested;
        logger.fine("process: exitStatus: " + status);

        return serverName.toString();

    }

    /**
     * Called if the batchlet is stopped by the container.
     */
    @Override
    public void stop() throws Exception {
        logger.fine("stop:");
        stopRequested = true;
    }

}
