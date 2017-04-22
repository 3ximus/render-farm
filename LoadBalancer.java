import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.cloudwatch.model.Datapoint;

public class LoadBalancer {
	public static final String CONTEXT = "/r.html";
	public static final int PORT = 80;

	public static EC2_Measures measures;

	public static void main(String[] args) throws Exception {
		measures = new EC2_Measures();
		HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
		server.createContext(CONTEXT, new QueryHandler());
		server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
		server.start();
	}

	/** Query handler class */
	static class QueryHandler implements HttpHandler {
		@Override
		public void handle(HttpExchange t) throws IOException {
			System.out.print("Got a request: ");
			String request = t.getRequestURI().getQuery();

			OutputStream os = t.getResponseBody();

			Map<Instance, Datapoint> results = measures.getMeasures();
			for (Map.Entry<Instance, Datapoint> result_entry : results.entrySet()) {
				os.write(new String("<br> CPU Usage for " + result_entry.getKey().getInstanceId() + " = " + result_entry.getValue().getAverage()).getBytes());
			}
			os.close();
		}
	}
}
