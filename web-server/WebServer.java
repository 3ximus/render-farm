import java.io.IOException;
import java.util.Map;
import java.util.HashMap;
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
            String response = "This was the query:<br>";
	    Map<String, String> params = queryToMap(t.getRequestURI().getQuery());
	    if(params == null) {
                response += "NO PARAMETERS";
            } else {
                for(Map.Entry<String, String> entry: params.entrySet()) {
	            response += " - <b>" + entry.getKey() + "</b>: " + entry.getValue() + "<br>";
                }
            }
            t.sendResponseHeaders(200, response.length());
            OutputStream os = t.getResponseBody();
            os.write(response.getBytes());
            os.close();
        }
    }
    
    public static Map<String, String> queryToMap(String query){
        if(query == null) {
            return null;
        }
        Map<String, String> result = new HashMap<String, String>();
        for (String param : query.split("&")) {
            String pair[] = param.split("=");
            if (pair.length>1) {
                result.put(pair[0], pair[1]);
            }else{
                result.put(pair[0], "");
            }
        }
        return result;
    }

}
