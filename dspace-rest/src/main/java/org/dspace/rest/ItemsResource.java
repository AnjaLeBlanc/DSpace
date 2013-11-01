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
import javax.servlet.http.HttpServletRequest;
import org.dspace.handle.HandleManager;
import org.dspace.rest.common.DSpaceObject;
import org.dspace.search.DSQuery;
import org.dspace.search.QueryArgs;
import org.dspace.search.QueryResults;

import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;

import org.dspace.content.ItemIterator;
import org.dspace.core.ConfigurationManager;
import org.dspace.core.Constants;
import org.dspace.rest.common.ItemReturn;
import org.dspace.storage.rdbms.DatabaseManager;
import org.dspace.storage.rdbms.TableRow;
import org.dspace.storage.rdbms.TableRowIterator;
import org.dspace.usage.UsageEvent;
import org.dspace.utils.DSpace;
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
	
	private static final boolean writeStatistics;
	private static final int maxPagination;
	
	static{
		writeStatistics=ConfigurationManager.getBooleanProperty("rest","stats",false);
		maxPagination=ConfigurationManager.getIntProperty("rest", "max_pagination");
	}
	
	 /** log4j category */
    private static final Logger log = Logger.getLogger(ItemsResource.class);
    //ItemList - Not Implemented

    private static org.dspace.core.Context context;
    
    @GET
    @Path("/")
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public org.dspace.rest.common.ItemReturn list(
    		@QueryParam("expand") String expand,
    		@QueryParam("limit") Integer size, 
    		@QueryParam("offset") Integer offset,
    		@Context HttpServletRequest request)  throws WebApplicationException {
    	
    	try {
            if(context == null || !context.isValid()) {
                context = new org.dspace.core.Context();
                //Failed SQL is ignored as a failed SQL statement, prevent: current transaction is aborted, commands ignored until end of transaction block
                context.getDBConnection().setAutoCommit(true);
            }
            //make sure maximum count per page is more than allowed
            if(size==null || size>maxPagination){
            	size=maxPagination;
            }
            if(offset==null){
            	offset=0;
            }
            ArrayList<org.dspace.rest.common.Item> selectedItems= new ArrayList<org.dspace.rest.common.Item>();
            ItemIterator items = org.dspace.content.Item.findAll(context);
            int count=0;
            int added=0;
            org.dspace.content.Item item;
            while(items.hasNext() && added<size){
            	item = items.next();
            	if(count>=offset && added<(offset+size)){
            		if(AuthorizeManager.authorizeActionBoolean(context, item, org.dspace.core.Constants.READ)) {
                        selectedItems.add(new org.dspace.rest.common.Item(item, expand, context));
                        added++;
                    }
            	}
            	count++;
            }
            
            org.dspace.rest.common.Context item_context = new org.dspace.rest.common.Context();
            item_context.setLimit(size);
            item_context.setOffset(offset);
            StringBuffer requestURL = request.getRequestURL();
            String queryString = request.getQueryString();

            if (queryString == null) {
            	item_context.setQuery(requestURL.toString());
            } else {
            	item_context.setQuery(requestURL.append('?').append(queryString).toString());
            }
            
            //get item count
            String myQuery = "SELECT count(*) as count FROM item WHERE in_archive='1' ";
                   
            TableRow row = DatabaseManager.querySingle(context, myQuery);
            if(row!=null){
            	item_context.setTotal_count(row.getLongColumn("count"));
            }
            
            ItemReturn item_return= new ItemReturn();
            item_return.setContext(item_context);
            item_return.setItem(selectedItems);
            
            return(item_return);
           
            
            
    	 } catch (SQLException e)  {
             log.error(e.getMessage());
             throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
         }
    	
    }

    @GET
    @Path("/{item_id}")
    @Produces({MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML})
    public org.dspace.rest.common.Item getItem(@PathParam("item_id") Integer item_id, @QueryParam("expand") String expand,
    		@QueryParam("userIP") String user_ip, @QueryParam("userAgent") String user_agent, @QueryParam("xforwarderfor") String xforwarderfor,
    		@Context HttpHeaders headers, @Context HttpServletRequest request) throws WebApplicationException {
    	
    	
        try {
            if(context == null || !context.isValid()) {
                context = new org.dspace.core.Context();
                //Failed SQL is ignored as a failed SQL statement, prevent: current transaction is aborted, commands ignored until end of transaction block
                context.getDBConnection().setAutoCommit(true);
            }

            org.dspace.content.Item item = org.dspace.content.Item.find(context, item_id);

            if(AuthorizeManager.authorizeActionBoolean(context, item, org.dspace.core.Constants.READ)) {
            	if(writeStatistics){
    				writeStats(item_id, user_ip, user_agent, xforwarderfor, headers, request);
    			}
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
    public org.dspace.rest.common.ItemReturn search(
    		@QueryParam("q") String query,
    		@QueryParam("expand") String expand,
    		@QueryParam("limit") Integer limit, 
    		@QueryParam("offset") Integer offset,
    		@Context HttpServletRequest request) throws WebApplicationException{
        try {
            if(context == null || !context.isValid()) {
                context = new org.dspace.core.Context();
                //Failed SQL is ignored as a failed SQL statement, prevent: current transaction is aborted, commands ignored until end of transaction block
                context.getDBConnection().setAutoCommit(true);
            }

            if(limit==null || limit>maxPagination){
            	limit=maxPagination;
            }
            if(offset==null){
            	offset=0;
            }
            
            QueryArgs queryArgs = new QueryArgs();
            queryArgs.setQuery(query);
            QueryResults queryResults = DSQuery.doQuery(context, queryArgs);

            List<String> handleList = queryResults.getHitHandles();
            List<org.dspace.rest.common.Item> dsoList = new ArrayList<org.dspace.rest.common.Item>();
            int added=0;
            int count=0;
            for(String handle : handleList) {
                org.dspace.content.DSpaceObject dso = HandleManager.resolveToObject(context, handle);
                if(dso instanceof  org.dspace.content.Item){
                	if(count>=offset && added<(offset+limit)){
	                	org.dspace.content.Item item = ( org.dspace.content.Item)dso;
	                	if(AuthorizeManager.authorizeActionBoolean(context, item, org.dspace.core.Constants.READ)) {
	                	   dsoList.add(new org.dspace.rest.common.Item(item, expand, context));
	                	   added++;
	                	}
                	} 
                	if(added>=limit){
                		break;
                	}
                }
                count++;
            }
            
            org.dspace.rest.common.ItemReturn item_return = new org.dspace.rest.common.ItemReturn();
            org.dspace.rest.common.Context item_context = new org.dspace.rest.common.Context();
            item_context.setLimit(limit);
            item_context.setOffset(offset);
            item_context.setTotal_count(handleList.size());
            StringBuffer requestURL = request.getRequestURL();
            String queryString = request.getQueryString();

            if (queryString == null) {
            	item_context.setQuery(requestURL.toString());
            } else {
            	item_context.setQuery(requestURL.append('?').append(queryString).toString());
            }
            item_return.setContext(item_context);
            item_return.setItem(dsoList);
            

            return item_return;

        } catch (SQLException e) {
            log.error(e.getMessage());
            throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
        } catch (IOException e) {
            log.error(e.getMessage());
            throw new WebApplicationException(Response.Status.INTERNAL_SERVER_ERROR);
        }
    }
    
    private void writeStats(Integer item_id, String user_ip, String user_agent,
			String xforwarderfor, HttpHeaders headers,
			HttpServletRequest request) {
		
    	try{
    		 org.dspace.content.DSpaceObject item =  org.dspace.content.DSpaceObject.find(context, Constants.ITEM, item_id);
    		
    		if(user_ip==null || user_ip.length()==0){
    			new DSpace().getEventService().fireEvent(
	                     new UsageEvent(
	                                     UsageEvent.Action.VIEW,
	                                     request,
	                                     context,
	                                     item));
    		} else{
	    		new DSpace().getEventService().fireEvent(
	                     new UsageEvent(
	                                     UsageEvent.Action.VIEW,
	                                     user_ip,
	                                     user_agent,
	                                     xforwarderfor,
	                                     context,
	                                     item));
    		}
    		log.debug("fired event");
    		
		} catch(SQLException ex){
			log.error("SQL exception can't write usageEvent \n" + ex);
		}
    		
	}
}
