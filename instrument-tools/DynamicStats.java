import BIT.highBIT.*;
import java.io.File;
import java.util.Enumeration;
import java.util.Vector;

public class DynamicStats {
	private static int dyn_method_count = 0;
	private static int dyn_bb_count = 0;
	private static int dyn_instr_count = 0;
	private static Interface_AmazonEC2 ec2;

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
					routine.addBefore("DynamicStats", "dynMethodCount", new Integer(1));

					for (Enumeration b = routine.getBasicBlocks().elements(); b.hasMoreElements();) {
						BasicBlock bb = (BasicBlock) b.nextElement();
						bb.addBefore("DynamicStats", "dynInstrCount", new Integer(bb.size()));
					}
				}
				ci.addAfter("DynamicStats", "printDynamic", "null");
				ci.write(out_filename);
			}
		}
	}

	public static synchronized void printDynamic(String foo) {
		ec2 = new Interface_AmazonEC2();
		ec2.createTable("tableName");
		System.out.println("Dynamic information summary:");
		System.out.println("Number of methods:      " + dyn_method_count);
		System.out.println("Number of basic blocks: " + dyn_bb_count);
		System.out.println("Number of instructions: " + dyn_instr_count);
	}

	public static synchronized void dynInstrCount(int incr) {
		dyn_instr_count += incr;
		dyn_bb_count++;
	}

	public static synchronized void dynMethodCount(int incr) {
		dyn_method_count++;
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
