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
	private static final String TABLE_NAME = "raytracer_stats";


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
		ec2 = new Interface_AmazonEC2();
		ec2.createTable(TABLE_NAME);

		RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean();
		long pid = Long.valueOf(runtimeBean.getName().split("@")[0]);

		try {
			File f = new File(TMP_QUERY_BASE_FILENAME + pid);
			Scanner sc = new Scanner(f);
			String[] query = sc.nextLine().split("_");
			sc.close();
			f.delete();
			ec2.addTableEntry(TABLE_NAME,
					ec2.makeItem(query[0],
						new TableEntry("sc", query[1]), new TableEntry("sr", query[2]),
						new TableEntry("wc", query[3]), new TableEntry("wr", query[4]),
						new TableEntry("coff", query[5]), new TableEntry("roff", query[6]),
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
