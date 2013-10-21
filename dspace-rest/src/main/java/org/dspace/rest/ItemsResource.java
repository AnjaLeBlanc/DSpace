/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.rest;

import org.apache.log4j.Logger;
import org.dspace.authorize.AuthorizeManager;
import org.dspace.core.Context;
import org.dspace.handle.HandleManager;
import org.dspace.rest.common.DSpaceObject;
import org.dspace.search.DSQuery;
import org.dspace.search.QueryArgs;
import org.dspace.search.QueryResults;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: peterdietz
 * Date: 9/19/13
 * Time: 4:54 PM
 * To change this template use File | Settings | File Templates.
 */
@Path("/items")
public class ItemsResource {
    Logger log = Logger.getLogger(ItemsResource.class);
    //ItemList - Not Implemented

    Context context;

    @GET
    @Path("/{item_id}")
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public org.dspace.rest.common.Item getItem(@PathParam("item_id") Integer item_id, @QueryParam("expand") String expand) throws WebApplicationException{
        try {
            if(context == null || !context.isValid()) {
                context = new Context();
                //Failed SQL is ignored as a failed SQL statement, prevent: current transaction is aborted, commands ignored until end of transaction block
                context.getDBConnection().setAutoCommit(true);
            }

            org.dspace.content.Item item = org.dspace.content.Item.find(context, item_id);

            if(AuthorizeManager.authorizeActionBoolean(context, item, org.dspace.core.Constants.READ)) {
                return new org.dspace.rest.common.Item(item, expand, context);
            } else {
                throw new WebApplicationException(Response.Status.UNAUTHORIZED);
            }

        } catch (SQLException e)  {
            log.error(e.getMessage());
            throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    // /items/search?q=Albert Einstein
    @GET
    @Path("/search")
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public DSpaceObject[] search(@QueryParam("q") String query) {
        try {
            if(context == null || !context.isValid()) {
                context = new Context();
                //Failed SQL is ignored as a failed SQL statement, prevent: current transaction is aborted, commands ignored until end of transaction block
                context.getDBConnection().setAutoCommit(true);
            }

            //TODO, I'm not 100% sure that results are limited to just Items...

            QueryArgs queryArgs = new QueryArgs();
            queryArgs.setQuery(query);
            QueryResults queryResults = DSQuery.doQuery(context, queryArgs);

            List<String> handleList = queryResults.getHitHandles();
            List<DSpaceObject> dsoList = new ArrayList<DSpaceObject>();
            for(String handle : handleList) {
                org.dspace.content.DSpaceObject dso = HandleManager.resolveToObject(context, handle);
                dsoList.add(new DSpaceObject(dso));
            }

            return dsoList.toArray(new org.dspace.rest.common.DSpaceObject[0]);

        } catch (SQLException e) {
            log.error(e.getMessage());
            throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
        } catch (IOException e) {
            log.error(e.getMessage());
            throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
        }
    }
}
