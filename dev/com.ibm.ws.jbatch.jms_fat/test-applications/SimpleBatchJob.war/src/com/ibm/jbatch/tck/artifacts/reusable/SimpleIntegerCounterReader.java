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
package com.ibm.jbatch.tck.artifacts.reusable;

import java.io.Serializable;
import java.util.logging.Logger;

import javax.batch.api.BatchProperty;
import javax.batch.api.chunk.ItemReader;

/**
 *
 */
public class SimpleIntegerCounterReader implements ItemReader {

    private final static Logger logger = Logger.getLogger("com.ibm.ws.jbatch_jms_fat");

    Integer next = 0;

    @BatchProperty(name = "max")
    String maxStr = "90";

    int max = 0;

    @Override
    public void open(Serializable checkpoint) throws Exception {

        max = Integer.parseInt(maxStr);

        if (checkpoint != null) {
            next = (Integer) checkpoint;
        }
        logger.fine("In open(), next = " + next);
    }

    @Override
    public void close() throws Exception {
        // TODO Auto-generated method stub
    }

    @Override
    public Object readItem() throws Exception {
        logger.fine("readItem: " + next.toString());
        if (next < max) {
            return next++;
        } else {
            logger.fine("Reached max, exiting.");
            return null;

        }
    }

    @Override
    public Serializable checkpointInfo() throws Exception {
        return next;
    }

}
