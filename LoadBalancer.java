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
import com.amazonaws.services.cloudwatch.model.Datapoint;

public class LoadBalancer {
	public static final String CONTEXT = "/r.html";
	public static final int PORT = 80;
	public static final String WEBSERVER_NODE_IMAGE_ID = "ami-2355c943";
	public static final int WEBSERVER_NODE_PORT = 8000;

	public static EC2_Measures measures;

	public static void main(String[] args) throws Exception {
		measures = new EC2_Measures();
		HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
		server.createContext(CONTEXT, new QueryHandler());
		server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
		server.start();
		System.out.println("Load Balancer Online. Press Enter to terminate.");
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


			Map<Instance, Datapoint> results = measures.getMeasures();
			double minimum = 200;
			Instance available_instance = null;
			for (Map.Entry<Instance, Datapoint> result_entry : results.entrySet()) {
				// read data from webserver nodes only
				if (result_entry.getKey().getImageId().equals(WEBSERVER_NODE_IMAGE_ID)
						&& (result_entry.getValue().getAverage() < minimum)) {
					available_instance = result_entry.getKey();
				}
			}

			String response = new String();
			if (available_instance == null) {
				response = "No available instance to satisfy your request...";
				System.out.println("No available instance to satisfy request");
			} else {
				System.out.println("Forwarding request to: " + available_instance.getInstanceId() + ", at: "
						+ available_instance.getPublicDnsName());
				System.out.println("\tURL: " + "http://" + available_instance.getPublicDnsName() + ":"
						+ WEBSERVER_NODE_PORT + "/r.html?" + request);
				InputStream forward_response_steram = new URL(
						"http://" + available_instance.getPublicDnsName() + ":" + WEBSERVER_NODE_PORT + "/r.html?" + request).openStream();
				response = new Scanner(forward_response_steram).useDelimiter("\\A").next();
			}
			OutputStream os = t.getResponseBody();
			t.sendResponseHeaders(200, response.length());
			os.write(response.getBytes());
			os.close();
		}
	}
}
