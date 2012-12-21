package com.stackmob.example;

import com.stackmob.core.DatastoreException;
import com.stackmob.core.InvalidSchemaException;
import com.stackmob.core.customcode.CustomCodeMethod;
import com.stackmob.core.rest.ProcessedAPIRequest;
import com.stackmob.core.rest.ResponseToProcess;
import com.stackmob.sdkapi.DataService;
import com.stackmob.sdkapi.LoggerService;
import com.stackmob.sdkapi.SDKServiceProvider;
import com.stackmob.sdkapi.SMBoolean;
import com.stackmob.sdkapi.SMCondition;
import com.stackmob.sdkapi.SMEquals;
import com.stackmob.sdkapi.SMGreater;
import com.stackmob.sdkapi.SMInt;
import com.stackmob.sdkapi.SMLessOrEqual;
import com.stackmob.sdkapi.SMObject;
import com.stackmob.sdkapi.SMString;

import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GetMessages implements CustomCodeMethod {

	Map<String, List<SMObject>> returnMap = new HashMap<String, List<SMObject>>();
	
	SMObject currentUser = null;
	boolean isCoach = false;
	
	@Override
	public String getMethodName() {
		return "get_messages";
	}

	@Override
	public List<String> getParams() {
		return Arrays.asList("username");
	}

	void addMessageListToReturnMap(List<SMObject> listToAdd) {
		List <SMObject> messages = returnMap.get("messages");
		if (messages == null) {
			messages = new ArrayList<SMObject>();
			returnMap.put("messages", messages);
		}
		messages.addAll(listToAdd);
	}
	
	ResponseToProcess errorResponse(String errorDescription) {
		HashMap<String, String> errParams = new HashMap<String, String>();
		errParams.put("error", errorDescription);
		return new ResponseToProcess(HttpURLConnection.HTTP_BAD_REQUEST, errParams);
	}
	
	void addCoachData(DataService dataService) throws InvalidSchemaException, DatastoreException {
		List<SMCondition> query = new ArrayList<SMCondition>();
		query.add(new SMEquals("is_coach", new SMBoolean(true)));
		List <SMObject> coachListResult = dataService.readObjects("user", query);
		returnMap.put("coachList", coachListResult);
	}
	
	void addPriceRangeMessages(DataService dataService) throws InvalidSchemaException, DatastoreException {
		SMInt oneSessionPrice = (SMInt) currentUser.getValue().get("onesessionprice");
		List<SMCondition> query = new ArrayList<SMCondition>();
		query.add(new SMLessOrEqual("onesessionprice", oneSessionPrice));
		query.add(new SMEquals("accepted", new SMInt((long) 0)));
		query.add(new SMGreater("onesessionprice", new SMInt((long) -1)));
		List <SMObject> coachListResult = dataService.readObjects("message", query);
		addMessageListToReturnMap(coachListResult);
	}
	
	void fetchCoachMessages(DataService dataService) throws InvalidSchemaException, DatastoreException{
		List<SMCondition> query = new ArrayList<SMCondition>();
		String username = currentUser.getValue().get("username").toString();
		query.add(new SMEquals("coachusername", new SMString(username)));
		List <SMObject> result = dataService.readObjects("message", query);
		addMessageListToReturnMap(result);
	}
	
	void fetchUserMessages(DataService dataService) throws InvalidSchemaException, DatastoreException {
		List<SMCondition> query = new ArrayList<SMCondition>();
		String username = currentUser.getValue().get("username").toString();
		query.add(new SMEquals("username", new SMString(username)));
		List <SMObject> result = dataService.readObjects("message", query);
		addMessageListToReturnMap(result);
	}
	
	
	@Override
	public ResponseToProcess execute(ProcessedAPIRequest request, SDKServiceProvider serviceProvider) {
		String username = request.getParams().get("username");

		DataService dataService = serviceProvider.getDataService();
		LoggerService logger = serviceProvider.getLoggerService(GetMessages.class);
		logger.debug("Start GetMessages request for user " + username);
		
		// Check if correct parameters are given
		if (username == null || username.isEmpty()) {
			return errorResponse("Username can not be empty");
		}

		// Check if there exists user with this name
		List<SMCondition> userQuery = new ArrayList<SMCondition>();
		userQuery.add(new SMEquals("username", new SMString(username)));
		List <SMObject> userResult;
		try {
			userResult = dataService.readObjects("user", userQuery);
			if (userResult.isEmpty() || userResult == null) {
				return errorResponse("No user with given name");
			}
		} catch (InvalidSchemaException e) {
			return errorResponse(e.toString());
		} catch (DatastoreException e) {
			return errorResponse(e.toString());
		}
		
		SMObject user = userResult.get(0);
		currentUser = user;

		// find if user is coach or student
		SMBoolean coach = (SMBoolean) currentUser.getValue().get("is_coach");
		isCoach = coach.getValue();
		
		try {
			if (isCoach) {
				logger.debug("User is coach, load pricerange messages.");
				addPriceRangeMessages(dataService);
				
			}
			logger.debug("Message list after addPriceRangeMessages:" + returnMap.size());

//	 		addCoachData(dataService);

			logger.debug("Fetch coach messages.");			
			fetchCoachMessages(dataService);
			logger.debug("Message list after fetchCoachMessages:" + returnMap.size());

			logger.debug("Fetch user messages");
			fetchUserMessages(dataService);
			logger.debug("Message list after fetchUserMessages:" + returnMap.size());

		} catch (InvalidSchemaException e1) {
			return errorResponse(e1.toString());
		} catch (DatastoreException e1) {
			return errorResponse(e1.toString());
		}
		
		return new ResponseToProcess(HttpURLConnection.HTTP_OK, returnMap);
	}
	
	
}
