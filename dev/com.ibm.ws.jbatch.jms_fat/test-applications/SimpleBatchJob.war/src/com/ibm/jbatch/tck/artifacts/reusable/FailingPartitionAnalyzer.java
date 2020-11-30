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

import java.util.logging.Logger;

import javax.batch.api.partition.AbstractPartitionAnalyzer;
import javax.batch.runtime.BatchStatus;
import javax.batch.runtime.context.JobContext;
import javax.batch.runtime.context.StepContext;
import javax.inject.Inject;
import javax.inject.Named;

/**
 *
 */
@Named("failingPartitionAnalyzer")
public class FailingPartitionAnalyzer extends AbstractPartitionAnalyzer {

    Logger logger = Logger.getLogger(FailingPartitionAnalyzer.class.getName());

    @Inject
    private JobContext jobCtx;
    @Inject
    private StepContext stepCtx;

    @Override
    public void analyzeStatus(BatchStatus batchStatus, String exitStatus) throws Exception {

        if (batchStatus.equals(BatchStatus.FAILED) && exitStatus.equals("FAILED") && stepCtx.getBatchStatus().equals(BatchStatus.STARTED)) {
            logger.fine("analyzeStatus: Analyzed failing partition ");

            stepCtx.setExitStatus("Analyzed failing partition");

        }
    }
}
