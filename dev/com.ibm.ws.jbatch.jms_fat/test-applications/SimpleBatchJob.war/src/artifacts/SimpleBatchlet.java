/*
 * IBM Confidential
 *
 * OCO Source Materials
 *
 * WLP Copyright IBM Corp. 2014
 *
 * The source code for this program is not published or otherwise divested 
 * of its trade secrets, irrespective of what has been deposited with the 
 * U.S. Copyright Office.
 */
package artifacts;

import javax.batch.api.AbstractBatchlet;
import javax.batch.runtime.context.StepContext;
import javax.inject.Inject;
import javax.naming.InitialContext;

/**
 * Returns step name as exit status
 */
public class SimpleBatchlet extends AbstractBatchlet {

    @Inject
    StepContext ctx;

    @Override
    public String process() throws Exception {
        Object serverName = new InitialContext().lookup("serverName");

        return serverName.toString();
    }
}
