import BIT.highBIT.*;
import java.io.File;
import java.io.FileNotFoundException;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.util.Enumeration;
import java.util.Scanner;
import java.util.Vector;

public class DynamicStats {
	private static double bb_count = 0;
	private static double instr_count = 0;
	private static Interface_AmazonEC2 ec2;
	private static final String TMP_QUERY_BASE_FILENAME = "/tmp/raytracer_";

	public static void doDynamic(File in_dir, File out_dir) {
		String filelist[] = in_dir.list();

		for (int i = 0; i < filelist.length; i++) {
			String filename = filelist[i];
			if (filename.endsWith(".class")) {
				String in_filename = in_dir.getAbsolutePath() + System.getProperty("file.separator") + filename;
				String out_filename = out_dir.getAbsolutePath() + System.getProperty("file.separator") + filename;
				ClassInfo ci = new ClassInfo(in_filename);
				for (Enumeration e = ci.getRoutines().elements(); e.hasMoreElements();) {
					Routine routine = (Routine) e.nextElement();
					for (Enumeration b = routine.getBasicBlocks().elements(); b.hasMoreElements();) {
						BasicBlock bb = (BasicBlock) b.nextElement();
						bb.addBefore("DynamicStats", "instructionCount", new Integer(bb.size()));
					}
				}
				ci.addAfter("DynamicStats", "printStats", "null");
				ci.write(out_filename);
			}
		}
	}

	/**
	 * Creates a table called dynamic stats with execution
	 *  stats every time the instrumented code is executed
	 */
	public static synchronized void printStats(String foo) {

		RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean();
		long pid = Long.valueOf(runtimeBean.getName().split("@")[0]);

		try {
			File f = new File(TMP_QUERY_BASE_FILENAME + pid);
			Scanner sc = new Scanner(f);
			String query = sc.nextLine();
			String[] q_params = query.split("_");
			sc.close();
			f.delete();
			String tableName = q_params[0] + "_statsTable";
			ec2 = new Interface_AmazonEC2();
			ec2.createTable(tableName); // create table if it doesnt exist
			ec2.addTableEntry(tableName,
					ec2.makeItem(query,
						new TableEntry("sc", q_params[1]), new TableEntry("sr", q_params[2]),
						new TableEntry("wc", q_params[3]), new TableEntry("wr", q_params[4]),
						new TableEntry("coff", q_params[5]), new TableEntry("roff", q_params[6]),
						new TableEntry("resolution", Integer.toString(Integer.valueOf(q_params[3]) * Integer.valueOf(q_params[4]))),
						new TableEntry("bb_count", String.format("%.0f", bb_count)),
						new TableEntry("instr_count", String.format("%.0f", instr_count))));
		} catch (FileNotFoundException fnfe) {
			System.out.println("Request results were not saved because PID file does not exist.");
		}
	}

	public static synchronized void instructionCount(int incr) {
		instr_count += incr;
		bb_count++;
	}

	public static void main(String argv[]) {
		if (argv.length != 2) {
			System.err.println("This tool must receive input and output directory as arguments.");
			System.exit(1);
		}
		try {
			File in_dir = new File(argv[0]);
			File out_dir = new File(argv[1]);

			if (in_dir.isDirectory() && out_dir.isDirectory()) {
				doDynamic(in_dir, out_dir);
			} else {
				System.err.println("Given Arguments aren't directories.'");
				System.exit(1);
			}
		} catch (NullPointerException e) {
			System.exit(1);
		}
	}
}
