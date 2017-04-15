import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.io.OutputStream;
import java.net.InetSocketAddress;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

// java -Djava.awt.headless=true -cp src raytracer.Main test05.txt test05.bmp 400 300 400 300 400 300
// http://ec2-35-177-250-90.eu-west-2.compute.amazonaws.com:8000/r.html?f=aaa&sc=400&sr=300&wc=400&wr=300&coff=400&roff=300
public class WebServer {
    public static final String CONTEXT = "/r.html";
    public static final int PORT = 8000;
    public static final List<String> required_params = new ArrayList<String>();

    public static void main(String[] args) throws Exception {
        required_params.add("f");
        required_params.add("sc");
        required_params.add("sr");
        required_params.add("wc");
        required_params.add("sr");
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
            String response = "This was the query:<br>";
	    Map<String, String> params = queryToMap(t.getRequestURI().getQuery());
	    if(params == null) {
                response += "NO PARAMETERS";
            } else {
                for(Map.Entry<String, String> entry: params.entrySet()) {
	            response += " - <b>" + entry.getKey() + "</b>: " + entry.getValue() + "<br>";
                }
            }
            
            boolean has_all_params = true;
            int response_code = 200;
            for(String p: required_params) {
               if(params.containsKey(p) == false) {
                   has_all_params = false;
                   response_code = 400;
                   System.out.println(p);
                   break;
               }
            }
            
            if(has_all_params == true) {
                response += "SUCCESS";
            } else {
                response += "MISSING_PARAMS";
            } 

            t.sendResponseHeaders(response_code, response.length());
            OutputStream os = t.getResponseBody();
            os.write(response.getBytes());
            os.close();
        }
    }
    
    public static Map<String, String> queryToMap(String query){
        Map<String, String> result = new HashMap<String, String>();
        if(query == null) {
            return result;
        }
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
