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
package cache;

import java.util.ArrayList;
import java.util.List;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Named;

/**
 * A custom Status Store implementation to store the status of returned topic messages
 */
@Named
@ApplicationScoped
public class RunningStatusStore {

    private final List<String> startedPartitions = new ArrayList<String>();
    private final List<String> endedPartitions = new ArrayList<String>();

    private final List<Long> startedJobs = (new ArrayList<Long>());
    private final List<Long> endedJobs = (new ArrayList<Long>());

    private final Object partitionLock = new Object();
    private final Object jobLock = new Object();

    public boolean partitionStarted(String partitionKey) {
        synchronized (partitionLock) {
            return startedPartitions.add(partitionKey);
        }
    }

    public boolean partitionEnded(String partitionKey) {
        synchronized (partitionLock) {
            return endedPartitions.add(partitionKey);
        }

    }

    public List<String> getAllRunningPartitions() {
        synchronized (partitionLock) {
            List<String> result = new ArrayList<String>(startedPartitions);
            result.removeAll(endedPartitions);
            return result;

        }
    }

    public boolean jobStarted(long instanceId) {
        synchronized (jobLock) {
            return startedJobs.add(instanceId);
        }
    }

    public boolean jobEnded(long instanceId) {
        synchronized (jobLock) {
            return endedJobs.add(instanceId);
        }
    }

    public List<String> getAllStartedPartitions() {
        synchronized (partitionLock) {
            return startedPartitions;
        }
    }

    public List<String> getAllEndedPartitions() {
        synchronized (partitionLock) {
            return endedPartitions;
        }
    }

    public List<Long> getAllStartedJobs() {
        synchronized (jobLock) {
            return startedJobs;
        }
    }

    public List<Long> getAllEndedJobs() {
        synchronized (jobLock) {
            return endedJobs;
        }
    }

    public List<Long> getAllRunningJobs() {
        synchronized (jobLock) {
            List<Long> result = new ArrayList<Long>(startedJobs);
            result.removeAll(endedJobs);
            return result;

        }
    }

    public void clear() {
        startedPartitions.clear();
        endedPartitions.clear();
        startedJobs.clear();
        endedJobs.clear();
    }

}
