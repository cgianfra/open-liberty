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

import javax.inject.Inject;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import cache.JobLogInfoStore;

/**
 * Rest API resource to get running status of the jobs and partitions
 */
@Path("/")
public class JobLogPartsResource {

    @Inject
    JobLogInfoStore cache;

    @GET
    @Path("/totalCount")
    @Produces(MediaType.APPLICATION_JSON)
    public int totalCount() {
        if (cache != null) {
            return cache.getJobLogCountTotal();
        }
        return 0;
    }

    @GET
    @Path("/namedCount/{name}")
    @Produces(MediaType.APPLICATION_JSON)
    public int namedCount(@PathParam("name") String name) {
        if (cache != null) {
            return cache.getNamedCount(name);
        }
        return 0;
    }

    @DELETE
    @Path("/clear")
    public Response clearCache() {
        cache.clear();
        return Response.status(Status.OK).build();
    }
}
