package com.mtlogic;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.json.JSONException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/api")
public class StoreMessage {
	final Logger logger = LoggerFactory.getLogger(StoreMessage.class);
	
	@Path("/message")
	@POST
	@Consumes(MediaType.APPLICATION_JSON)
	@Produces(MediaType.TEXT_PLAIN)
	public Response storeMessage(String payload) throws JSONException 
	{	
		logger.info(">>>ENTERED storeMessage()");
		
		Response response = null;
		StoreMessageService storeMessageService = null;
		int responseCode = 200;
		
		try {
			storeMessageService = new StoreMessageService();
			storeMessageService.storeMessage(payload);
		} catch (Exception e) {
			logger.error("Message could not be processed: " + e.getMessage());
			e.printStackTrace();
			response = Response.status(422).entity("Message could not be processed: " + e.getMessage()).build();
		}
		
		if (response == null) {
			response = Response.status(responseCode).entity("SUCCESS").build();
		}
		
		logger.info("<<<EXITED storeMessage()");
		return response;
	}
	
	@Path("/message/cache")
	@GET
	@Produces(MediaType.TEXT_PLAIN)
	public Response retrieveCachedMessage(@QueryParam("payload") String payload) throws JSONException 
	{	
		logger.info(">>>ENTERED retrieveCachedMessage()");
		
		Response response = null;
		StoreMessageService storeMessageService = null;
		int responseCode = 200;
		String message = null;
		try {
			storeMessageService = new StoreMessageService();
			message = storeMessageService.retrieveMessageFromCache(payload.replace("+", ""));
		} catch (Exception e) {
			logger.error("Message could not be processed: " + e.getMessage());
			e.printStackTrace();
			response = Response.status(422).entity("Message could not be processed: " + e.getMessage()).build();
		}
		
		if (response == null) {
			response = Response.status(responseCode).entity(message).build();
		}
		
		logger.info("<<<EXITED retrieveCachedMessage()");
		return response;
	}
	
}