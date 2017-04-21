import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.io.InputStream;
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

// http://ec2-35-177-250-90.eu-west-2.compute.amazonaws.com:8000/r.html?f=aaa&sc=400&sr=300&wc=400&wr=300&coff=400&roff=300
// java -Djava.awt.headless=true -cp src raytracer.Main test05.txt test05.bmp 400 300 400 300 400 300
public class WebServer {
	public static final String CONTEXT = "/r.html";
	public static final int PORT = 8000;
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
			String response = "This was the query:<br>";
			Map<String, String> params = queryToMap(t.getRequestURI().getQuery());
			if(params == null) {
					response += "NO PARAMETERS";
				} else {
					for(Map.Entry<String, String> entry: params.entrySet()) {
						response += " - <b>" + entry.getKey() + "</b>: " + entry.getValue() + "<br>";
						// System.out.println(entry.getKey() + ": " + entry.getValue());
					}
				}

				boolean has_all_params = true;
				int response_code = 200;
				for(String p: required_params) {
				   if(params.containsKey(p) == false) {
					   has_all_params = false;
					   response_code = 400;
					   System.out.println("Request missing: " + p);
					   break;
				   }
				}

			if(has_all_params == true) {
				// Will add an error message, or, if successful, will return the response html.
				String res = "Exception caught.";
			try {
				res = callRaytracer(params.get("f"), params.get("sc"), params.get("sr"), params.get("wc"), params.get("wr"), params.get("coff"), params.get("roff"));
			} catch(Exception e) {
				System.out.println(e.getMessage());
			}
			response += "ALL_PARAMS_RECEIVED <br>" + res;
			System.out.println("ALL_PARAMS_RECEIVED" + res);

			} else {
				System.out.println("MISSING_PARAMS");
				response += "MISSING_PARAMS";
			}
			t.sendResponseHeaders(response_code, response.length());
			OutputStream os = t.getResponseBody();
			os.write(response.getBytes());
			os.close();
		}
	}

	public static String callRaytracer(String f, String sc, String sr, String wc, String wr, String coff, String roff) {
		if(f.contains("/") || f.contains("\\")) {
			return "NO, NO, NO... No path traversals for you!";
		}

		String result_file_name = f + "_" + sc + "_" + sr + "_" + wc + "_" + wr + "_" + coff + "_" + roff + ".bmp";
		// System.out.println("FILENAME: " + result_file_name);
		String raytracer_path = "/home/ec2-user/render-farm/raytracer/";
		String output_path = "/home/ec2-user/render-farm/web-server/res/";
		String result = "NULL";
		try {
			// java -Djava.awt.headless=true -cp src raytracer.Main test05.txt test05.bmp 400 300 400 300 400 300
			ProcessBuilder pBuilder = new ProcessBuilder("java", "-Djava.awt.headless=true",  "-cp", raytracer_path + "src", "raytracer.Main", raytracer_path + f, output_path + result_file_name, sc, sr, wc, wr, coff, roff);
			pBuilder.redirectErrorStream(true);
			Process process = pBuilder.start();
			BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
			StringBuilder builder = new StringBuilder();

			String line = null;
			while ((line = reader.readLine()) != null) {
			builder.append(line);
			builder.append("\n<br>");
			}
			result = builder.toString();

			} catch(Exception e) {
			return "RAY INSUCCESS: " + e.getMessage();
		}
		return "<b>RAY RESULT</b> <br>" + result + "<b>RAY RESULT OUT!</b><br>";
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
			} else{
				result.put(pair[0], "");
			}
		}
		return result;
	}

}
