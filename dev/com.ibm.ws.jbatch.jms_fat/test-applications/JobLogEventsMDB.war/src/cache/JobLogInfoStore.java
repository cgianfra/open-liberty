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

import java.util.HashMap;
import java.util.Map;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Named;

/**
 * A custom Status Store implementation to store the status of returned topic messages
 */
@Named
@ApplicationScoped
public class JobLogInfoStore {

    //Keeps the total count of all job log events received
    private int jobLogTotalPartCounter = 0;

    //Keeps a count of how many job log events received for each partition, flow name, or TLJ
    private final Map<String, Integer> namedCount = new HashMap<String, Integer>();

    private final Object countLock = new Object();

    public int getJobLogCountTotal() {
        return jobLogTotalPartCounter;
    }

    public void incrementTotalCount() {
        jobLogTotalPartCounter++;
    }

    public int getNamedCount(String name) {
        synchronized (countLock) {
            Integer count = namedCount.get(name);
            if (count == null) {
                return 0;
            } else {
                return count;
            }
        }
    }

    public void updateNamedCount(String name) {
        synchronized (countLock) {
            Integer count = namedCount.get(name);
            if (count == null) {
                namedCount.put(name, 1);
            } else {
                namedCount.put(name, count + 1);
            }
        }
    }

    public void clear() {
        jobLogTotalPartCounter = 0;
        namedCount.clear();
    }

}
