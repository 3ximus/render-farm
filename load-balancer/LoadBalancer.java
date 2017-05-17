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

public class LoadBalancer {
	public static final String CONTEXT = "/r.html";
	public static final int PORT = 80;
	public static final String WEBSERVER_NODE_IMAGE_ID = "ami-2355c943";
	public static final int WEBSERVER_NODE_PORT = 8000;

	public static Interface_AmazonEC2 ec2;

	public static void main(String[] args) throws Exception {
		ec2 = new Interface_AmazonEC2();
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

			// **** SELECT INSTANCE **** //
			Instance selectedInstance = selectInstance();

			String response = new String();
			if (selectedInstance == null) {
				response = "No available instance to satisfy your request...";
				System.out.println("No available instance to satisfy request");
			} else {
				System.out.println("Forwarding request to: " + selectedInstance.getInstanceId() + ", at: "
						+ selectedInstance.getPublicDnsName());
				System.out.println("\tURL: " + "http://" + selectedInstance.getPrivateIpAddress() + ":"
						+ WEBSERVER_NODE_PORT + "/r.html?" + request);
				InputStream forward_response_stream = new URL(
						"http://" + selectedInstance.getPublicDnsName() + ":" + WEBSERVER_NODE_PORT + "/r.html?" + request).openStream();
				response = new Scanner(forward_response_stream).useDelimiter("\\A").next();
			}
			OutputStream os = t.getResponseBody();
			t.sendResponseHeaders(200, response.length());
			os.write(response.getBytes());
			os.close();
		}

		public Instance selectInstance() {
			// TODO this must be changed to selectInstance to take average request time into account
			return getLowestCPULoadInstance();
		}

		/**
		 * Query the Database to see if this query has been done, if so return its average time to compute
		 */
		public Double getRequestAverageTime(String request) {
			return new Double(0);
		}

		/**
		 * Return the available WebServerNode Instance with least CPU Load
		 */
		public Instance getLowestCPULoadInstance() {
			Map<Instance, Double> results = ec2.getCPULoad();
			double minimum = 200; // high enough for CPU Load in percentage...
			Instance available_instance = null;

			// find the instance with least CPU Load
			for (Map.Entry<Instance, Double> result_entry : results.entrySet()) {
				// read data from webserver nodes only
				if (result_entry.getKey().getImageId().equals(WEBSERVER_NODE_IMAGE_ID)
						&& (result_entry.getValue() < minimum)) {
					available_instance = result_entry.getKey();
				}
			}
			return available_instance;
		}
	}
}
