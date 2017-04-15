import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

// java -Djava.awt.headless=true -cp src raytracer.Main test05.txt test05.bmp 400 300 400 300 400 300
public class WebServer {
    public static final String CONTEXT = "/r.html";
    public static final int PORT = 8000;
    
    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.createContext(CONTEXT, new MyHandler());
        server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
        server.start();
    }

    static class MyHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            String response = "This was the query:" + t.getRequestURI().getQuery() 
                               + "##";
            t.sendResponseHeaders(200, response.length());
            OutputStream os = t.getResponseBody();
            os.write(response.getBytes());
            os.close();
        }
    }

}
