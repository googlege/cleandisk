package de.homedev.cleandisk;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Random;

/**
 * @author Mikhalev, Viatcheslav
 * @email  slava.mikhalev@gmail.com
 * @version 1.1 ------Single Thread Version----
 *
 */
public class MainSimple {
	private static final int STEP_SIZE = 1024 * 1024 * 4 * 5; // 20MByte
	private static final long MAX_FILE_SIZE = 1024l * 1024 * 1024 * 3; // 3GByte
	private static final byte[] DATA = new byte[STEP_SIZE];

	private void random(byte[] b) {
		Random r = new Random();
		for (int i = 0; i < b.length; i = i + 4) {
			int x = r.nextInt();
			b[i] = (byte) (x >> 24);
			b[i + 1] = (byte) (x >> 16);
			b[i + 2] = (byte) (x >> 8);
			b[i + 3] = (byte) (x);
		}
	}

	private void createFile(File file, byte[] b, long maxFileSize, boolean doubleOverride) throws IOException {
		System.out.println("Writing " + file.getName() + " with new random data");
		random(b);
		FileOutputStream os = null;
		int stepSize = b.length;
		long fileSize = stepSize;
		try {
			os = new FileOutputStream(file, false);
			while (fileSize < maxFileSize) {
				os.write(b, 0, stepSize);
				fileSize = fileSize + stepSize;
			}
		} finally {
			if (os != null) {
				try {
					os.close();
				} catch (IOException e) {
					e.printStackTrace();
				}

			}
			if (doubleOverride) {
				file.delete();
				createFile(file, b, maxFileSize, false);
			}
		}
	}

	/**
	 * It is hypothetical possible reading with special hardware previous value
	 * from memory cell, if last stored value(s) are known. If you override data
	 * 2 times, previous stored value is 100% unknown!
	 */
	public static void main(final String[] args) {
		if ((args == null) || (args.length < 1)) {
			System.out.println("Sample: java -Xms64m de.homedev.cleandisk.MainSimple C: false");
			System.out.println("C: - disk you have to override");
			System.out.println("false - override 2 times (true or false)");
			System.exit(1);
		}
		boolean doubleOverride = false;
		String diskName = args[0];
		if (args.length > 1) {
			doubleOverride = Boolean.parseBoolean(args[1]);
		}
		File disk = new File(diskName);
		if (!disk.exists()) {
			System.out.println("Disk '" + disk + "' does'n exist.");
			System.exit(1);
		}
		if (!disk.isDirectory()) {
			System.out.println("Disk '" + disk + "' does'n exist.");
			System.exit(1);
		}
		if (!disk.canWrite()) {
			System.out.println("Can't write to " + disk);
			System.exit(1);
		}
		MainSimple main = new MainSimple();
		long startTime = System.currentTimeMillis();
		int i = 0, j = 0;
		File dir = new File(disk, "cleandisk");
		if (!dir.exists()) {
			dir.mkdirs();
		}
		File file = null;
		try {
			while (true) {
				file = new File(dir, "f" + i++ + ".dat");
				while (file.exists()) {
					file = new File(dir, "f" + i++ + ".dat");
				}
				main.createFile(file, DATA, MAX_FILE_SIZE, doubleOverride);
				j++;
			}
		} catch (IOException t) {
			System.err.println(t.getMessage());
		}
		long endTime = System.currentTimeMillis();
		long timeMs = endTime - startTime;
		double writtenGB = MAX_FILE_SIZE / (1024d * 1024 * 1024) * j + file.length() / (1024d * 1024 * 1024);
		if (doubleOverride) {
			writtenGB = writtenGB * 2;
		}
		double timeMin = timeMs / (1000d * 60);
		System.out.format("Used: %3.2fMin written ca.:%3.2fGByte perfomance ca.:%3.2fMB/Min\n", timeMin, writtenGB, writtenGB / timeMin * 1024);
		System.out.println("Normal program termination. Delete directory 'cleandisk'");
	}
}