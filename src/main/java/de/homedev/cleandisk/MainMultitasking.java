package de.homedev.cleandisk;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Random;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 
 * @author Mikhalev, Viatcheslav
 * @email  slava.mikhalev@gmail.com
 * @version 1.1 ------Multitasking Version-----
 * 
 * 
 */
public class MainMultitasking {
	private static final int STEP_SIZE = 1024 * 1024 * 4 * 5; // 20MByte
	private static final long MAX_FILE_SIZE = 1024l * 1024l * 1024l * 3l; // 3GByte

	private enum Data {
		DATA1, DATA2;
		private final byte[] data = new byte[STEP_SIZE];
		private boolean readyForWriting = false;
		private final ReentrantLock lock = new ReentrantLock();

		public boolean isReadyForWriting() {
			return readyForWriting;
		}

		public void setReadyForWriting(boolean flag) {
			lock.lock();
			this.readyForWriting = flag;
			lock.unlock();
		}

		public byte[] getData() {
			return data;
		}

		public void populateData() {
			if (!readyForWriting) {
				random(data);
				lock.lock();
				this.readyForWriting = true;
				lock.unlock();
			}
		}
	}

	private Data getOther(Data data) {
		if (data == Data.DATA1) {
			return Data.DATA2;
		}
		return Data.DATA1;
	}

	private static void random(byte[] b) {
		Random r = new Random();
		for (int i = 0; i < b.length; i = i + 4) {
			int x = r.nextInt();
			b[i] = (byte) (x >> 24);
			b[i + 1] = (byte) (x >> 16);
			b[i + 2] = (byte) (x >> 8);
			b[i + 3] = (byte) (x);
		}
	}

	private Data createFile(File file, Data dataToUse, long maxFileSize, Job1 job1, boolean doubleOverride) throws IOException {
		System.out.println("Writing " + file.getName() + " with new random data " + dataToUse.name());
		while (!dataToUse.isReadyForWriting()) {
			try {
				Thread.sleep(1000);
				job1.interrupt();
			} catch (InterruptedException e) {
				System.out.println("Writing data not ready!");
			}
		}
		byte[] data = dataToUse.getData();
		int stepSize = data.length;
		long fileSize = stepSize;
		FileOutputStream os = null;
		try {
			os = new FileOutputStream(file, false);
			while (fileSize < MAX_FILE_SIZE) {
				os.write(data, 0, stepSize);
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
			dataToUse.setReadyForWriting(false);
			dataToUse = getOther(dataToUse);
			if (doubleOverride) {
				file.delete();
				dataToUse = createFile(file, dataToUse, maxFileSize, job1, false);
			}
		}
		return dataToUse;
	}

	class Job1 extends Thread {
		private boolean finish = false;

		@Override
		public void run() {
			while (!finish) {
				Data.DATA1.populateData();
				Data.DATA2.populateData();
				try {
					sleep(5000);
				} catch (InterruptedException e) {

				}
			}
		}

		public void finish() {
			finish = true;
			this.interrupt();
		}
	}

	class Job2 extends Thread {
		private final File dir;
		private final Job1 job1;
		private Data dataToUse = Data.DATA1;
		private final Boolean doubleOverride;

		public Job2(File dir, Job1 job1, boolean doubleOverride) {
			super();
			this.dir = dir;
			this.job1 = job1;
			this.doubleOverride = doubleOverride;
		}

		@Override
		public void run() {
			int i = 0, j = 0;
			File file = null;
			long startTime = System.currentTimeMillis();
			try {
				while (true) {
					if (dataToUse.isReadyForWriting()) {
						file = new File(dir, "f" + i++ + ".dat");
						while (file.exists()) {
							file = new File(dir, "f" + i++ + ".dat");
						}
						dataToUse = createFile(file, dataToUse, MAX_FILE_SIZE, job1, doubleOverride);
						j++;
					} else {
						try {
							sleep(1000);
							job1.interrupt();
						} catch (InterruptedException e) {
						}
					}
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
			job1.finish();
			System.out.println("Normal program termination. Delete directory 'cleandisk'");
		}
	}

	/**
	 * It is hypothetical possible reading with special hardware previous value
	 * from memory cell, if last stored value(s) are known. If you override data
	 * 2 times, previous stored value is 100% unknown!
	 */
	public void appMain(final String[] args) {
		if ((args == null) || (args.length < 1)) {
			System.out.println("Sample: java -Xms64m de.homedev.cleandisk.MainMultitasking C: false");
			System.exit(1);
		}
		boolean doubleOverride = false;
		String diskName = args[0];
		if (args.length > 1) {
			doubleOverride = Boolean.parseBoolean(args[1]);
		}
		File disk = new File(diskName);

		if (!disk.exists()) {
			System.out.println("Disk " + disk + " does'n exist.");
			System.exit(1);
		}
		if (!disk.isDirectory()) {
			System.out.println("Disk " + disk + " does'n exist.");
			System.exit(1);
		}
		if (!disk.canWrite()) {
			System.out.println("Can't write to " + disk);
			System.exit(1);
		}

		File dir = new File(disk, "cleandisk");
		if (!dir.exists()) {
			dir.mkdirs();
		}

		Job1 job1 = new Job1();
		Job2 job2 = new Job2(dir, job1, doubleOverride);
		job1.start();
		job2.start();
	}

	public static void main(final String[] args) {
		(new MainMultitasking()).appMain(args);
	}

}
