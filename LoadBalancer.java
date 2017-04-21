import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.ArrayList;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

public class LoadBalancer {
	public static final String CONTEXT = "/r.html";
	public static final int PORT = 80;

	public static void main(String[] args) throws Exception {
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

			// TODO handle the request
		}
	}

}
