import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import javax.xml.ws.Response;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.cloudwatch.model.Datapoint;

public class LoadBalancer {
	public static final String CONTEXT = "/r.html";
	public static final int PORT = 8000;
	public static final String WEBSERVER_NODE_IMAGE_ID = "ami-2355c943";

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
		System.exit(0);
	}

	/** Query handler class */
	static class QueryHandler implements HttpHandler {
		@Override
		public void handle(HttpExchange t) throws IOException {
			String request = t.getRequestURI().getQuery();
			System.out.println("Got a request: " + request);

			OutputStream os = null;

			Map<Instance, Datapoint> results = measures.getMeasures();
			for (Map.Entry<Instance, Datapoint> result_entry : results.entrySet()) {
				if (result_entry.getKey().getImageId().equals(WEBSERVER_NODE_IMAGE_ID)) {
					String response = "CPU Usage for " + result_entry.getKey().getInstanceId() + " = "
							+ result_entry.getValue().getAverage() + "    <font size='2'> "
							+ result_entry.getKey().getPublicDnsName() + "   ("
							+ result_entry.getKey().getPublicIpAddress() + ")</font><br>";
					t.sendResponseHeaders(200, response.length());
					os = t.getResponseBody();
					os.write(response.getBytes());
				}
			}
			os.close();
		}
	}
}
