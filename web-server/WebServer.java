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
	public static final boolean DEBUGGING = true;

	public static final String CONTEXT = "/r.html";
	public static final int PORT = 8000;
	public static final List<String> required_params = new ArrayList<String>();

	public static final String raytracer_path = "/home/ec2-user/render-farm/raytracer/";
	public static final String output_path = "/home/ec2-user/render-farm/web-server/res/";

	public static void main(String[] args) throws Exception {
		required_params.add("f");
		required_params.add("sc");
		required_params.add("sr");
		required_params.add("wc");
		required_params.add("wr");
		required_params.add("coff");
		required_params.add("roff");
		HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
		server.createContext(CONTEXT, new RaytracerHandler());
		server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
		server.start();
	}

	public static void DebugPrintln(String s) {
		if (DEBUGGING == true)
			System.out.println(s);
	}

	public static void DebugPrint(String s) {
		if (DEBUGGING == true)
			System.out.print(s);
	}

	static class RaytracerHandler implements HttpHandler {
		@Override
		public void handle(HttpExchange t) throws IOException {
			DebugPrintln("\n------------------------");
			DebugPrintln("----- NEW REQUEST  -----");
			DebugPrintln("------------------------");

			DebugPrint("Received a request with the parameters: ");
			String response = "This was the query:<br>";

			Map<String, String> params = queryToMap(t.getRequestURI().getQuery());
			if (params == null) {
				DebugPrintln("no parameters.");
				response += "NO PARAMETERS";
			} else {
				for (Map.Entry<String, String> entry : params.entrySet()) {
					response += " - <b>" + entry.getKey() + "</b>: " + entry.getValue() + "<br>";
					DebugPrint(entry.getKey() + ": " + entry.getValue() + "; ");
				}
				DebugPrintln("");
			}

			boolean has_all_params = true;
			int response_code = 200;
			for (String p : required_params) {
				if (params.containsKey(p) == false) {
					has_all_params = false;
					response_code = 400;
					DebugPrintln("Request missing the parameter: " + p);
				}
			}

			if (has_all_params == true) {
				String res = null;
				try {
					res = callRaytracer(params.get("f"), params.get("sc"), params.get("sr"), params.get("wc"),
							params.get("wr"), params.get("coff"), params.get("roff"));
				} catch (Exception e) {
					res = "Exception caught.";
					System.out.println("Exception calling the raytracer. Cause: " + e.getMessage());
				}
				response += "ALL_PARAMS_RECEIVED <br>" + res;
				DebugPrintln("All parameters were received in this request.");

			} else {
				DebugPrintln("NOT all parameters were received in this request.");
				response += "MISSING_PARAMS";
			}

			t.sendResponseHeaders(response_code, response.length());
			OutputStream os = t.getResponseBody();
			os.write(response.getBytes());
			os.close();
		}
	}

	public class ImagesHandler implements HttpHandler {
		@Override
		public void handle(HttpExchange t) throws IOException {
			File file = new File(output_path + "test05.txt_400_300_400_300_400_300.bmp");
			t.sendResponseHeaders(200, file.length());
			// TODO set the Content-Type header to image/gif
			Headers headers = t.getResponseHeaders();
			headers.add("Content-Type", "image");

			OutputStream os = t.getResponseBody();
			Files.copy(file.toPath(), os);
			os.close();
		}
	}

	public static String callRaytracer(String f, String sc, String sr, String wc, String wr, String coff, String roff) {
		if (f.contains("/") || f.contains("\\")) {
			return "NO, NO, NO... No path traversals for you!";
		}

		String result_file_name = f + "_" + sc + "_" + sr + "_" + wc + "_" + wr + "_" + coff + "_" + roff + ".bmp";
		// System.out.println("FILENAME: " + result_file_name);
		String result = "NULL";
		try {
			// java -Djava.awt.headless=true -cp src raytracer.Main test05.txt test05.bmp 400 300 400 300 400 300
			ProcessBuilder pBuilder = new ProcessBuilder("java", "-Djava.awt.headless=true", "-cp",
					raytracer_path + "src", "raytracer.Main", raytracer_path + f, output_path + result_file_name, sc,
					sr, wc, wr, coff, roff);
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

		} catch (Exception e) {
			return "RAY INSUCCESS: " + e.getMessage();
		}
		return "<b>RAY RESULT</b> <br>" + result + "<b>RAY RESULT OUT!</b><br>";
	}

	public static Map<String, String> queryToMap(String query) {
		Map<String, String> result = new HashMap<String, String>();
		if (query == null) {
			return result;
		}
		for (String param : query.split("&")) {
			String pair[] = param.split("=");
			if (pair.length > 1) {
				result.put(pair[0], pair[1]);
			} else {
				result.put(pair[0], "");
			}
		}
		return result;
	}
}
