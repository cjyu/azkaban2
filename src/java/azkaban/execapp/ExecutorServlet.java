package azkaban.execapp;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;
import org.codehaus.jackson.map.ObjectMapper;

import azkaban.executor.ConnectorParams;
import azkaban.executor.ExecutableFlow;
import azkaban.executor.ExecutorManagerException;
import azkaban.utils.FileIOUtils.LogData;
import azkaban.utils.JSONUtils;
import azkaban.webapp.servlet.AzkabanServletContextListener;

public class ExecutorServlet extends HttpServlet implements ConnectorParams {
	private static final long serialVersionUID = 1L;
	private static final Logger logger = Logger.getLogger(ExecutorServlet.class.getName());
	public static final String JSON_MIME_TYPE = "application/json";

	private AzkabanExecutorServer application;
	private FlowRunnerManager flowRunnerManager;

	
	public ExecutorServlet() {
		super();
	}
	
	@Override
	public void init(ServletConfig config) throws ServletException {
		application = (AzkabanExecutorServer) config.getServletContext().getAttribute(AzkabanServletContextListener.AZKABAN_SERVLET_CONTEXT_KEY);

		if (application == null) {
			throw new IllegalStateException(
					"No batch application is defined in the servlet context!");
		}

		flowRunnerManager = application.getFlowRunnerManager();
	}

	
	protected void writeJSON(HttpServletResponse resp, Object obj) throws IOException {
		resp.setContentType(JSON_MIME_TYPE);
		ObjectMapper mapper = new ObjectMapper();
		OutputStream stream = resp.getOutputStream();
		mapper.writeValue(stream, obj);
	}

	@Override
	public void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		HashMap<String,Object> respMap= new HashMap<String,Object>();
		try {
			if (!hasParam(req, ACTION_PARAM)) {
				respMap.put("error", "Parameter action not set");
			}
			else {
				String action = getParam(req, ACTION_PARAM);
				if (action.equals(UPDATE_ACTION)) {
					handleAjaxUpdateRequest(req, respMap);
				}
				else if (action.equals(PING_ACTION)) {
					respMap.put("status", "alive");
				}
				else {
					int execid = Integer.parseInt(getParam(req, EXECID_PARAM));
					String user = getParam(req, USER_PARAM, null);
					
					if (action.equals(LOG_ACTION)) {
						handleFetchLogEvent(execid, req, resp, respMap);
					}
					else if (action.equals(EXECUTE_ACTION)) {
						handleAjaxExecute(req, respMap, execid);
					}
					else if (action.equals(STATUS_ACTION)) {
						handleAjaxFlowStatus(respMap, execid);
					}
					else if (action.equals(CANCEL_ACTION)) {
						logger.info("Cancel called.");
						handleAjaxCancel(respMap, execid, user);
					}
					else if (action.equals(PAUSE_ACTION)) {
						logger.info("Paused called.");
						handleAjaxPause(respMap, execid, user);
					}
					else if (action.equals(RESUME_ACTION)) {
						logger.info("Resume called.");
						handleAjaxResume(respMap, execid, user);
					}
					else {
						respMap.put("error", "action: '" + action + "' not supported.");
					}
				}
			}
		} catch (Exception e) {
			respMap.put(RESPONSE_ERROR, e.getMessage());
		}
		writeJSON(resp, respMap);
		resp.flushBuffer();
	}
	
	private void handleFetchLogEvent(int execId, HttpServletRequest req, HttpServletResponse resp, Map<String, Object> respMap) throws ServletException {
		String type = getParam(req, "type");
		int startByte = getIntParam(req, "offset");
		int length = getIntParam(req, "length");
		
		resp.setContentType("text/plain");
		resp.setCharacterEncoding("utf-8");
		
		if (type.equals("flow")) {
			LogData result;
			try {
				result = flowRunnerManager.readFlowLogs(execId, startByte, length);
				respMap.putAll(result.toObject());
			} catch (Exception e) {
				respMap.put("error", e.getMessage());
			}
		}
		else {
			String jobId = getParam(req, "jobId");
			try {
				LogData result = flowRunnerManager.readJobLogs(execId, jobId, startByte, length);
				respMap.putAll(result.toObject());
			} catch (Exception e) {
				respMap.put("error", e.getMessage());
			}
		}
	}
	
	@SuppressWarnings("unchecked")
	private void handleAjaxUpdateRequest(HttpServletRequest req, Map<String, Object> respMap) throws ServletException, IOException {
		ArrayList<Object> updateTimesList = (ArrayList<Object>)JSONUtils.parseJSONFromString(getParam(req, UPDATE_TIME_LIST_PARAM));
		ArrayList<Object> execIDList = (ArrayList<Object>)JSONUtils.parseJSONFromString(getParam(req, EXEC_ID_LIST_PARAM));
		
		ArrayList<Object> updateList = new ArrayList<Object>();
		for (int i = 0; i < execIDList.size(); ++i) {
			long updateTime = JSONUtils.getLongFromObject(updateTimesList.get(i));
			int execId = (Integer)execIDList.get(i);
			
			ExecutableFlow flow = flowRunnerManager.getExecutableFlow(execId);
			if (flow == null) {
				Map<String, Object> errorResponse = new HashMap<String,Object>();
				errorResponse.put(RESPONSE_ERROR, "Flow does not exist");
				errorResponse.put(UPDATE_MAP_EXEC_ID, execId);
				updateList.add(errorResponse);
				continue;
			}
			
			if (flow.getUpdateTime() > updateTime) {
				updateList.add(flow.toUpdateObject(updateTime));
			}
		}
		
		respMap.put(RESPONSE_UPDATED_FLOWS, updateList);
	}
	
	private void handleAjaxExecute(HttpServletRequest req, Map<String, Object> respMap, int execId) throws ServletException {
		try {
			flowRunnerManager.submitFlow(execId);
		} catch (ExecutorManagerException e) {
			respMap.put(RESPONSE_ERROR, e.getMessage());
		}
	}
	
	private void handleAjaxFlowStatus(Map<String, Object> respMap, int execid) {
		ExecutableFlow flow = flowRunnerManager.getExecutableFlow(execid);
		if (flow == null) {
			respMap.put(STATUS_PARAM, RESPONSE_NOTFOUND);
		}
		else {
			respMap.put(STATUS_PARAM, flow.getStatus().toString());
			respMap.put(RESPONSE_UPDATETIME, flow.getUpdateTime());
		}
	}
	
	private void handleAjaxPause(Map<String, Object> respMap, int execid, String user) throws ServletException {
		if (user == null) {
			respMap.put(RESPONSE_ERROR, "user has not been set");
			return;
		}
		
		try {
			flowRunnerManager.pauseFlow(execid, user);
			respMap.put(STATUS_PARAM, RESPONSE_SUCCESS);
		} catch (ExecutorManagerException e) {
			e.printStackTrace();
			respMap.put(RESPONSE_ERROR, e.getMessage());
		}
	}
	
	private void handleAjaxResume(Map<String, Object> respMap, int execid, String user) throws ServletException {
		if (user == null) {
			respMap.put(RESPONSE_ERROR, "user has not been set");
			return;
		}
		
		try {
			flowRunnerManager.resumeFlow(execid, user);
			respMap.put(STATUS_PARAM, RESPONSE_SUCCESS);
		} catch (ExecutorManagerException e) {
			e.printStackTrace();
			respMap.put(RESPONSE_ERROR, e.getMessage());
		}
	}
	
	private void handleAjaxCancel(Map<String, Object> respMap, int execid, String user) throws ServletException {
		if (user == null) {
			respMap.put(RESPONSE_ERROR, "user has not been set");
			return;
		}
		
		try {
			flowRunnerManager.cancelFlow(execid, user);
			respMap.put(STATUS_PARAM, RESPONSE_SUCCESS);
		} catch (ExecutorManagerException e) {
			e.printStackTrace();
			respMap.put(RESPONSE_ERROR, e.getMessage());
		}
	}
	
	@Override
	public void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		
	}
	
	/**
	 * Duplicated code with AbstractAzkabanServlet, but ne
	 */
	public boolean hasParam(HttpServletRequest request, String param) {
		return request.getParameter(param) != null;
	}

	public String getParam(HttpServletRequest request, String name)
			throws ServletException {
		String p = request.getParameter(name);
		if (p == null)
			throw new ServletException("Missing required parameter '" + name + "'.");
		else
			return p;
	}
	
	public String getParam(HttpServletRequest request, String name, String defaultVal ) {
		String p = request.getParameter(name);
		if (p == null) {
			return defaultVal;
		}

		return p;
	}

	public int getIntParam(HttpServletRequest request, String name) throws ServletException {
		String p = getParam(request, name);
		return Integer.parseInt(p);
	}
	
	public int getIntParam(HttpServletRequest request, String name, int defaultVal) {
		if (hasParam(request, name)) {
			try {
				return getIntParam(request, name);
			} catch (Exception e) {
				return defaultVal;
			}
		}
		
		return defaultVal;
	}
}
