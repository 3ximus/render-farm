import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;

public class LoadBalancer {
	public static final String CONTEXT = "/r.html";
	public static final int PORT = 80;
	public static final String WEBSERVER_NODE_IMAGE_ID = "ami-952543f5";
	public static final int WEBSERVER_NODE_PORT = 8000;

	public static Interface_AmazonEC2 ec2;

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

		// PLACEHOLDER remove this
		System.out.println("Use this example: http://" + publicIP.substring(0, publicIP.length() - 1) + "/r.html?f=test04.txt&sc=400&sr=300&wc=400&wr=300&coff=0&roff=0");

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

			for (Instance in : ec2.getInstances()) // update map with new instances
				if (in.getImageId().equals(WEBSERVER_NODE_IMAGE_ID) && ! instructionPerInstance.containsKey(in))
					instructionPerInstance.put(in, new Double(0));

			// **** SELECT INSTANCE **** //
			Instance selectedInstance = selectInstance(request);

			String response = new String();
			if (selectedInstance == null) {
				response = "No available instance to satisfy your request...";
				System.out.println("No available instance to satisfy request");
			} else {
				System.out.println("Forwarding request to: " + selectedInstance.getInstanceId() + ", at: "
						+ selectedInstance.getPublicDnsName());
				//System.out.println("\tURL: " + "http://" + selectedInstance.getPrivateIpAddress() + ":"
				//		+ WEBSERVER_NODE_PORT + "/r.html?" + request);
				InputStream forward_response_stream = new URL(
						"http://" + selectedInstance.getPublicDnsName() + ":" + WEBSERVER_NODE_PORT + "/r.html?" + request).openStream();
				response = new Scanner(forward_response_stream).useDelimiter("\\A").next();
			}
			OutputStream os = t.getResponseBody();
			t.sendResponseHeaders(200, response.length());
			os.write(response.getBytes());
			os.close();
		}
	}

	/**
	 * Select an instance to by least loaded one, in case of tie the one with lowest CPU load is selected
	 */
	public static Instance selectInstance(String query) {
		Instance selected = null;
		Double estimatedInstructionCount = getRequestEstimatedInstructions(query);
		if (estimatedInstructionCount > 0) {
			Double minimum = null;
			for (Map.Entry<Instance, Double> d : instructionPerInstance.entrySet())
				if (minimum == null || d.getValue() < minimum) {
					minimum = d.getValue();
					selected = d.getKey();
				}
		} else selected = getLowestCPULoadInstance(); // PLACEHOLDER

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
	 * Query the Database to see if this query has been done, if so return its average time to compute,
	 *  otherwise aproximate teh value with similar queries
	 * @param String query to estimate values
	 * @return number of instructions for the query (estimated or real if possible)
	 */
	public static Double getRequestEstimatedInstructions(String query) {
		Map<String, String> mq = queryToMap(query);
		String q = mq.get("f") + "_" + mq.get("sc") + "_" + mq.get("sr") + "_" + mq.get("wc") + "_" + mq.get("wr") + "_" + mq.get("coff") + "_" + mq.get("roff");
		Map<String, AttributeValue> result = ec2.scanTableByQuery(mq.get("f") + "_statsTable", q);
		if (result != null) // query was done before
			return Double.valueOf(result.get("instr_count").getS());

		// TODO  else, estimate the value of the instruction with similar queries
		return new Double(0);
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
	public static void addTaskToInstanceCounter(Instance ins, Double instructionNumber) {
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
}
