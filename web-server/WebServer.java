import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.io.IOException;
import java.io.File;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.net.InetSocketAddress;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.Headers;

// http://ec2-35-177-250-90.eu-west-2.compute.amazonaws.com:8000/r.html?f=aaa&sc=400&sr=300&wc=400&wr=300&coff=400&roff=300
// java -Djava.awt.headless=true -cp /home/ec2-user/render-farm/instrument-tools/:/home/ec2-user/render-farm/raytracer/src raytracer.Main test05.txt test05.bmp 400 300 400 300 400 300
public class WebServer {
	public static final boolean DEBUGGING = true;

	public static final String RAYTRACER_CONTEXT = "/r.html";
	public static final String IMAGE_CONTEXT = "/image";
	public static final int PORT = 8000;
	public static final List<String> required_params = new ArrayList<String>();

	public static final String raytracer_classpath = "/home/ec2-user/render-farm/amazon:/home/ec2-user/render-farm/instrument-tools:/home/ec2-user/render-farm/BIT:/home/ec2-user/render-farm/aws-java-sdk-1.11.127/lib/aws-java-sdk-1.11.127.jar:/home/ec2-user/render-farm/aws-java-sdk-1.11.127/third-party/lib/*:/home/ec2-user/render-farm/raytracer/src";
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
		server.createContext(RAYTRACER_CONTEXT, new RaytracerHandler());
		server.createContext(IMAGE_CONTEXT, new ImagesHandler());
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

	public static class RaytracerHandler implements HttpHandler {
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

	public static class ImagesHandler implements HttpHandler {
		@Override
		public void handle(HttpExchange t) throws IOException {
			DebugPrintln("\n------------------------------");
			DebugPrintln("----- NEW IMAGE REQUEST  -----");
			DebugPrintln("------------------------------");
			OutputStream os = t.getResponseBody();

			Map<String, String> params = queryToMap(t.getRequestURI().getQuery());
			if (params == null || params.get("image") == null) {
				DebugPrintln("'/images' context requires the get parameter 'image' with the file to read.");

				String res = "Missing the 'image' parameter";
				t.sendResponseHeaders(400, res.length());
				os.write(res.getBytes());
				os.close();
			} else {
				String file_path = params.get("image");
				File file = new File(output_path + file_path);

				// Set contetnt type as image
				Headers headers = t.getResponseHeaders();
				headers.add("Content-Type", "image");
				t.sendResponseHeaders(200, file.length());

				// Send the image
				Files.copy(file.toPath(), os);
				os.close();
			}
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
			// java -Djava.awt.headless=true -cp  /home/ec2-user/render-farm/instrument-tools/:/home/ec2-user/render-farm/raytracer/src raytracer.Main test05.txt test05.bmp 400 300 400 300 400 300
			ProcessBuilder pBuilder = new ProcessBuilder("java", "-Djava.awt.headless=true", "-cp",
					raytracer_classpath, "raytracer.Main", raytracer_path + f, output_path + result_file_name, sc,
					sr, wc, wr, coff, roff);
			pBuilder.redirectErrorStream(true);
			Map<String, String> pEnv = pBuilder.environment();
			pEnv.put("JAVA_HOME", "/etc/alternatives/java_sdk_1.7.0");
			pEnv.put("JAVA_ROOT", "/etc/alternatives/java_sdk_1.7.0");
			pEnv.put("JDK_HOME", "/etc/alternatives/java_sdk_1.7.0");
			pEnv.put("JRE_HOME", "/etc/alternatives/java_sdk_1.7.0/jre");
			pEnv.put("PATH", "/etc/alternatives/java_sdk_1.7.0/bin");
			pEnv.put("SDK_HOME", "/etc/alternatives/java_sdk_1.7.0");
			pEnv.put("_JAVA_OPTIONS", "-XX:-UseSplitVerifier ");
			Process process = pBuilder.start();

			long pid = 0;
			// get process pid
			if (process.getClass().getName().equals("java.lang.UNIXProcess")) {
				try {
					Field field = process.getClass().getDeclaredField("pid");
					field.setAccessible(true);
					pid = field.getLong(process);
					field.setAccessible(false);
				} catch (Exception e) {
					System.err.println("ERROR: CANT GET PID OF RAYTRACER.");
					System.err.println(e.getMessage());
				}
			}

			if (pid != 0) {
				System.out.println("This is the PID: " + pid);
			}

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
		return "<b>RAY RESULT</b> <br>" + result + "<b>RAY RESULT OUT!</b><br>" + "Get image at: <a href=\"./images?image=" + result_file_name + "\"> Result image</a>";
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
