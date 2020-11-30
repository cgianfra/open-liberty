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
package jobLogEventsConfig;

import java.util.HashSet;
import java.util.Set;

import javax.inject.Named;
import javax.ws.rs.core.Application;

import resource.JobLogPartsResource;

/**
 * Application class to bind the resources to the RestServlet
 */
@Named("myJobLogEventApplication")
public class MyJobLogEventApplication extends Application {

    @Override
    public Set<Class<?>> getClasses() {
        Set<Class<?>> classes = new HashSet<Class<?>>();
        classes.add(JobLogPartsResource.class);

        return classes;
    }

}
