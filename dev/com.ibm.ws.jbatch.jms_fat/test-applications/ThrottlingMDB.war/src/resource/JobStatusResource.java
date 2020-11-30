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
package resource;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import cache.RunningStatusStore;

/**
 * Rest API resource to get running status of the jobs and partitions
 */
@Path("/status")
public class JobStatusResource {

    @Inject
    RunningStatusStore cache;

    @GET
    @Path("jobs/started")
    @Produces(MediaType.APPLICATION_JSON)
    public List<Long> getStartedJobs() {
        return cache.getAllStartedJobs();
    }

    @GET
    @Path("partitions/started")
    @Produces(MediaType.APPLICATION_JSON)
    public List<String> getStartedPartitions() {
        return cache.getAllStartedPartitions();
    }

    @GET
    @Path("jobs/ended")
    @Produces(MediaType.APPLICATION_JSON)
    public List<Long> getEndedJobs() {
        return cache.getAllStartedJobs();
    }

    @GET
    @Path("partitions/ended")
    @Produces(MediaType.APPLICATION_JSON)
    public List<String> getEndedPartitions() {
        return cache.getAllStartedPartitions();
    }

    @GET
    @Path("jobs/running")
    @Produces(MediaType.APPLICATION_JSON)
    public List<Long> getRunningJobs() {
        return cache.getAllRunningJobs();
    }

    @GET
    @Path("partitions/running")
    @Produces(MediaType.APPLICATION_JSON)
    public List<String> getRunningPartitions() {
        return cache.getAllRunningPartitions();
    }

    @GET
    @Path("/jobs")
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, List<Long>> getAllJobs() {
        Map<String, List<Long>> jobs = new HashMap<String, List<Long>>();
        jobs.put("started", cache.getAllStartedJobs());
        jobs.put("ended", cache.getAllEndedJobs());
        return jobs;
    }

    @GET
    @Path("/partitions")
    @Produces(MediaType.APPLICATION_JSON)
    public Map<String, List<String>> getAllPartitions() {
        Map<String, List<String>> jobs = new HashMap<String, List<String>>();
        jobs.put("started", cache.getAllStartedPartitions());
        jobs.put("ended", cache.getAllEndedPartitions());
        return jobs;
    }

    @DELETE
    public Response clearCache() {
        cache.clear();
        return Response.status(Status.OK).build();
    }
}
