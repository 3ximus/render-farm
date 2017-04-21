import java.io.IOException;
import java.net.InetSocketAddress;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

public class LoadBalancer {
	public static final String CONTEXT = "/r.html";
	public static final int PORT = 80;
	public static final List<String> required_params = new ArrayList<String>();

	public static void main(String[] args) throws Exception {
		required_params.add("f");
		required_params.add("sc");
		required_params.add("sr");
		required_params.add("wc");
		required_params.add("wr");
		required_params.add("coff");
		required_params.add("roff");
		HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
		server.createContext(CONTEXT, new MyHandler());
		server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
		server.start();
	}

	static class MyHandler implements HttpHandler {
		@Override
		public void handle(HttpExchange t) throws IOException {
			System.out.print("Got a request: ");
			String request = t.getRequestURI().getQuery();

			// TODO handle the request
		}
	}
}
