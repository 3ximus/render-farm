import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;

public class LoadBalancer {
	public static final String CONTEXT = "/r.html";
	public static final int PORT = 80;
	public static final String WEBSERVER_NODE_IMAGE_ID = "ami-27c6a047";
	public static final int WEBSERVER_NODE_PORT = 8000;

	public static final double INSTRUCTIONS_PER_SECOND = 480E6; // experimental value
	private static long timeOfLastQuery = 0;

	private static Interface_AmazonEC2 ec2;

	private static Map<Instance, Double> instructionPerInstance;

	public static void main(String[] args) throws Exception {
		ec2 = new Interface_AmazonEC2();
		instructionPerInstance = new HashMap<Instance, Double>();
		HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
		server.createContext(CONTEXT, new QueryHandler());
		server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
		server.start();
		// get this instance IP
		InputStream publicIPRequest = new URL("http://checkip.amazonaws.com").openStream();
		String publicIP = new Scanner(publicIPRequest).useDelimiter("\\A").next();

		System.out.println("Load Balancer Online at [ " + publicIP.substring(0, publicIP.length() - 1)
				+ " ] . Press Enter to terminate.");

		System.in.read(); // halt, press any key to kill the server
		System.out.println("Terminating Load Balancer...");
		server.stop(0);
	}

	/** Query handler class */
	static class QueryHandler implements HttpHandler {
		@Override
		public void handle(HttpExchange t) throws IOException {
			String request = t.getRequestURI().getQuery();
			System.out.println("\033[1;32mGot a request: \033[0m" + request);

			// update instance Map
			Set<Instance> instances = ec2.getInstances();
			for (Instance in : instances) // update map with new instances
				if (in.getState().getName().equals("running") && in.getImageId().equals(WEBSERVER_NODE_IMAGE_ID) && containsInstance(instructionPerInstance.keySet(), in) == null)
					instructionPerInstance.put(in, new Double(0));

			for (Iterator<Instance> i = instructionPerInstance.keySet().iterator(); i.hasNext();) { // remove instances that are not available anymore
				Instance in = i.next();
				Instance found = containsInstance(instances, in);
				if (found == null || ! found.getState().getName().equals("running"))
					i.remove();
			}

			// **** SELECT INSTANCE **** //
			Instance selectedInstance = selectInstance(request);

			byte[] response;
			ByteArrayOutputStream output = new ByteArrayOutputStream();
			if (selectedInstance == null) {
				response = "No available instance to satisfy your request...".getBytes();
				System.out.println("No available instance to satisfy request");
			} else {
				System.out.println("Forwarding request to: " + selectedInstance.getInstanceId() + ", at: " + selectedInstance.getPublicDnsName());
				URL url = new URL("http://" + selectedInstance.getPublicDnsName() + ":" + WEBSERVER_NODE_PORT + "/r.html?" + request);
				try (InputStream inputStream = url.openStream()) {
					int n = 0;
					byte[] buffer = new byte[1024];
					while (-1 != (n = inputStream.read(buffer))) {
						output.write(buffer, 0, n);
					}
				}
				response = output.toByteArray();

			}
			OutputStream os = t.getResponseBody();
			Headers headers = t.getResponseHeaders();
			headers.add("Content-Type", "image");
			t.sendResponseHeaders(200, response.length);
			os.write(response);
			os.close();
		}
	}

	/**
	 * Select an instance to by least loaded one, in case of tie the one with lowest CPU load is selected
	 */
	public static Instance selectInstance(String query) {
		Instance selected = null;
		Double estimatedInstructionCount = getRequestEstimatedInstructions(query);
		estimateExecutedInstructions();
		if (estimatedInstructionCount > 0) {
			Double minimum = null;
			for (Map.Entry<Instance, Double> d : instructionPerInstance.entrySet())
				if (minimum == null || d.getValue() < minimum) {
					minimum = d.getValue();
					selected = d.getKey();
				}
		} else selected = getLowestCPULoadInstance(); // use this only if no instance is about to be available soon

		if (selected != null) {
			System.out.println("IPI Table:"); // print the instructions per instance map
			for (Map.Entry<Instance, Double> entry : instructionPerInstance.entrySet())
				System.out.println((entry.getKey().getInstanceId().equals(selected.getInstanceId()) ? "\033[1m> " : "  ") + entry.getKey().getInstanceId() + " - " + entry.getValue() + (entry.getKey().getInstanceId().equals(selected.getInstanceId()) ? " + " + estimatedInstructionCount : "") + "\033[0m");
			// add instructions count to map
			addTaskToInstanceCounter(selected, estimatedInstructionCount);
		}
		return selected;
	}

	/**
	 * Subtracts estimated number of instructions executed based on the average number
	 *  of instructions executed per second
	 */
	private static synchronized void estimateExecutedInstructions() {
		if (timeOfLastQuery == 0) {
			timeOfLastQuery = System.currentTimeMillis(); // update time of last query
			return;
		}

		long elapsedTime = System.currentTimeMillis() - timeOfLastQuery;
		double estimatedInstructionsExecuted = INSTRUCTIONS_PER_SECOND * elapsedTime/1000F;

		// subtract estimated instructions executed
		for (Map.Entry<Instance, Double> entry : instructionPerInstance.entrySet()) {
			if (entry.getValue() < estimatedInstructionsExecuted) instructionPerInstance.put(entry.getKey(), new Double(0));
			else instructionPerInstance.put(entry.getKey(), entry.getValue() - estimatedInstructionsExecuted);
		}
		timeOfLastQuery = System.currentTimeMillis(); // update time of last query
	}

	/**
	 * Query the Database to see if this query has been done, if so return its average time to compute,
	 *  otherwise aproximate teh value with similar queries
	 * @param String query to estimate values
	 * @return number of instructions for the query (estimated or real if possible)
	 */
	public static Double getRequestEstimatedInstructions(String query) {
		Map<String, String> mq = queryToMap(query);
		String q = mq.get("f") + "_" + mq.get("sc") + "_" + mq.get("sr") + "_" + mq.get("wc") + "_" + mq.get("wr") + "_" + mq.get("coff") + "_" + mq.get("roff");
		String tableName = mq.get("f") + "_statsTable";
		Map<String, AttributeValue> result = ec2.scanTableByQuery(tableName, q);
		if (result != null) { // query was done before
			System.out.println("Found same query in DB...");
			return Double.valueOf(result.get("instr_count").getS());
		}

		String resolution = Integer.toString(Integer.valueOf(mq.get("wc")) * Integer.valueOf(mq.get("wr")));
		// NOTE instead of average use maximum???
		// ELSE, calculate average of queries with the same image resolution
		List<Map<String, AttributeValue>> eqVals = ec2.scanTableEqualValues(tableName, "resolution", resolution);
		if (eqVals != null) {
			System.out.println("Estimating with average of same resolution...");
			Double total = new Double(eqVals.size()), acumulator = new Double(0);
			for (Map<String, AttributeValue> item : eqVals)
				acumulator += Double.valueOf(item.get("instr_count").getS());
			return acumulator / total; // return average
		}

		// ELSE, estimate with interpolation of bound queries
		List<Map<String, AttributeValue>> boundVals = ec2.scanTableBoundValues(tableName, "resolution", resolution);
		if (boundVals == null) {
			boundVals = ec2.scanTableForExtremeValues(tableName, "resolution");
			System.out.println("Estimating with extreme values...");
		} else System.out.println("Estimating with linear interpolation of closer bounds...");

		if (boundVals != null) {
			Double instructionUpperBound = Double.valueOf(boundVals.get(0).get("instr_count").getS());  // y3
			Double resolutionUpperBound = Double.valueOf(boundVals.get(0).get("resolution").getS());  // x3
			Double instructionLowerBound = Double.valueOf(boundVals.get(1).get("instr_count").getS()); // y1
			Double resolutionLowerBound = Double.valueOf(boundVals.get(1).get("resolution").getS()); // x1
			// y2 = ((x2 - x1)(y3 - y1))/(x3 - x1) + y1
			return ((Double.valueOf(resolution) - resolutionLowerBound)
					* (instructionUpperBound - instructionLowerBound)) / (resolutionUpperBound - resolutionLowerBound)
					+ instructionLowerBound;
		}

		return new Double(10E9); // all else fails use medium size request ( average of 10Billion instructions...)
	}

	/**
	 * Return the available WebServerNode Instance with least CPU Load
	 */
	public static Instance getLowestCPULoadInstance() {
		Map<Instance, Double> results = ec2.getCPULoad();
		Double minimum = new Double(200); // high enough for CPU Load in percentage...
		Instance available_instance = null;

		System.out.println("CPU Load:");
		for (Map.Entry<Instance, Double> result_entry : results.entrySet()) {
			// read data from webserver nodes only
			if (result_entry.getKey().getImageId().equals(WEBSERVER_NODE_IMAGE_ID)) {
				System.out.println(String.format("  %s - %.3f %%", result_entry.getKey().getInstanceId() , result_entry.getValue()));
				if ((result_entry.getValue() < minimum)) {
					available_instance = result_entry.getKey();
					minimum = result_entry.getValue();
				}
			}
		}
		return available_instance;
	}

	/**
	 * Add the estimated instruction count to the instance instruction counter
	 * Also normalizes the array to keep lower values and avoid overflow
	 * @param Instance instance to add instruction number
	 * @param Double number of instruction to add
	 */
	public static synchronized void addTaskToInstanceCounter(Instance ins, Double instructionNumber) {
		instructionPerInstance.put(ins, instructionPerInstance.get(ins) + instructionNumber);

 		// normalize to avoid HUGE numbers
		double minimum = 0;
		for (Double val : instructionPerInstance.values()) // find minimum
			if (minimum == 0 || val < minimum) minimum = val;

		if (minimum != 0) // normalize
			for (Map.Entry<Instance, Double> entry : instructionPerInstance.entrySet())
				instructionPerInstance.put(entry.getKey(), entry.getValue() - minimum);
	}

	/**
	 * Transform web query to Map<String, String>
	 */
	public static Map<String, String> queryToMap(String query) {
		Map<String, String> result = new HashMap<String, String>();
		if (query == null) return result;
		for (String param : query.split("&")) {
			String pair[] = param.split("=");
			if (pair.length > 1) result.put(pair[0], pair[1]);
			else result.put(pair[0], "");
		}
		return result;
	}

	public static Instance containsInstance(Set<Instance> set, Instance target) {
		for (Instance i : set)
			if (i.getInstanceId().equals(target.getInstanceId()))
				return i;
		return null;
	}
}
