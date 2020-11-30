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
package com.ibm.jbatch.tck.artifacts.specialized;

import java.util.Random;
import java.util.logging.Logger;

import javax.batch.api.AbstractBatchlet;
import javax.batch.api.BatchProperty;
import javax.inject.Inject;
import javax.naming.InitialContext;

@javax.inject.Named("sleepyBatchlet")
public class SleepyBatchlet extends AbstractBatchlet {

    private final static Logger logger = Logger.getLogger(SleepyBatchlet.class.getName());

    private volatile boolean stopRequested = false;

    private final Random random = new Random();

    @Inject
    @BatchProperty(name = "sleep.time.seconds")
    String sleepTimeSeconds;
    private int sleep_time_seconds = 10; //default is 10 seconds

    @Inject
    @BatchProperty(name = "randomize.sleep.time")
    String randomizeSleepTimeParam;
    private boolean randomizeSleepTime = false;

    @Inject
    @BatchProperty(name = "force_failure")
    private String forceFailure;

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

        if (randomizeSleepTimeParam != null && !randomizeSleepTimeParam.isEmpty()) {
            randomizeSleepTime = Boolean.parseBoolean(randomizeSleepTimeParam);
        }

        if (sleepTimeSeconds != null) {
            sleep_time_seconds = getSleepTime(randomizeSleepTime, Integer.parseInt(sleepTimeSeconds));
            logger.fine("process: sleep for: " + sleepTimeSeconds);
        }

        int i;
        //Multiplying by 10 to add more granularity and check stopRequested more frequently
        for (i = 0; i < sleep_time_seconds * 10 && !stopRequested; ++i) {
            logger.fine("process: [" + i + "] sleeping for a second...");
            Thread.sleep(1 * 100);
        }

        String status = "SleepyBatchlet:i=" + i + ";stopRequested=" + stopRequested;
        logger.fine("process: exitStatus: " + status);

        return serverName.toString();

    }

    /**
     * 
     * @param randomize whether to randomize or not
     * @param sleepTime in seconds
     * @return returns a number between(sleepTime*0.5. sleepTime, sleepTime*1.5);
     */
    private int getSleepTime(boolean randomize, int sleepTime) {

        if (randomize) {
            int var = random.nextInt(15);

            if (var < 5)
                return (5 * sleepTime) / 10;
            else if (var > 10)
                return (10 * sleepTime) / 10;
            else
                return (15 * sleepTime) / 10;
        }
        return sleepTime;
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
