package com.parc.ccn.apps;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.logging.Level;

import org.ccnx.ccn.CCNHandle;
import org.ccnx.ccn.Library;
import org.ccnx.ccn.io.CCNFileOutputStream;
import org.ccnx.ccn.io.CCNOutputStream;
import org.ccnx.ccn.io.RepositoryFileOutputStream;
import org.ccnx.ccn.io.RepositoryOutputStream;
import org.ccnx.ccn.protocol.ContentName;
import org.ccnx.ccn.protocol.MalformedContentNameStringException;

import com.parc.ccn.config.ConfigurationException;

public class put_file {
	
	private static int BLOCK_SIZE = 8096;
	private static boolean rawMode = false;
	private static Integer timeout = null;
	private static boolean unversioned = false;

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		int startArg = 0;
		
		for (int i = 0; i < args.length - 2; i++) {
			if (args[i].equals(("-raw"))) {
				if (startArg <= i)
					startArg = i + 1;
				rawMode = true;
			} else if (args[i].equals("-unversioned")) {
				if (startArg <= i)
					startArg = i + 1;
				unversioned = true;
			} else if (args[i].equals("-timeout")) {
				if (args.length < (i + 2)) {
					usage();
				}
				try {
					timeout = Integer.parseInt(args[++i]);
				} catch (NumberFormatException nfe) {
					usage();
				}
				if (startArg <= i)
					startArg = i + 1;
			} else if (args[i].equals("-log")) {
				Level level = null;
				if (args.length < (i + 2)) {
					usage();
				}
				try {
					level = Level.parse(args[++i]);
				} catch (NumberFormatException nfe) {
					usage();
				}
				Library.logger().setLevel(level);
				if (startArg <= i)
					startArg = i + 1;
			} else {
				usage();
			}
				
		}
		
		if (args.length < startArg + 2) {
			usage();
		}
		
		long starttime = System.currentTimeMillis();
		try {
			// If we get one file name, put as the specific name given.
			// If we get more than one, put underneath the first as parent.
			// Ideally want to use newVersion to get latest version. Start
			// with random version.
			ContentName argName = ContentName.fromURI(args[startArg]);
			
			CCNHandle library = CCNHandle.open();
			
			if (args.length == (startArg + 2)) {
				
				File theFile = new File(args[startArg + 1]);
				if (!theFile.exists()) {
					System.out.println("No such file: " + args[startArg + 1]);
					usage();
				}
				Library.logger().info("put_file: putting file " + args[startArg + 1] + " bytes: " + theFile.length());
				
				CCNOutputStream ostream;
				if (rawMode) {
					if (unversioned)
						ostream = new CCNOutputStream(argName, library);
					else
						ostream = new CCNFileOutputStream(argName, library);
				} else {
					if (unversioned)
						ostream = new RepositoryOutputStream(argName, library);
					else
						ostream = new RepositoryFileOutputStream(argName, library);
				}
				if (timeout != null)
					ostream.setTimeout(timeout);
				do_write(ostream, theFile);
				
				System.out.println("Inserted file " + args[startArg + 1] + ".");
				System.out.println("put_file took: "+(System.currentTimeMillis() - starttime)+"ms");
				System.exit(0);
			} else {
				for (int i=startArg + 1; i < args.length; ++i) {
					
					File theFile = new File(args[i]);
					if (!theFile.exists()) {
						System.out.println("No such file: " + args[i]);
						usage();
					}
					
					//FileOutputStream testOut = new FileOutputStream("put_file" + i + ".dat");
					//testOut.write(contents);
					//testOut.flush();
					//testOut.close();
					
					// put as child of name
					ContentName nodeName = ContentName.fromURI(argName, theFile.getName());
					
					// int version = new Random().nextInt(1000);
					// would be version = library.latestVersion(argName) + 1;
					CCNOutputStream ostream;
					
					// Use file stream in both cases to match behavior. CCNOutputStream doesn't do
					// versioning and neither it nor CCNVersionedOutputStream add headers.
					if (rawMode) {
						if (unversioned)
							ostream = new CCNOutputStream(nodeName, library);
						else
							ostream = new CCNFileOutputStream(nodeName, library);
					} else {
						if (unversioned)
							ostream = new RepositoryOutputStream(nodeName, library);
						else
							ostream = new RepositoryFileOutputStream(nodeName, library);
					}
					if (timeout != null)
						ostream.setTimeout(timeout);
					do_write(ostream, theFile);
					
					System.out.println("Inserted file " + args[i] + ".");
				}
				System.out.println("put_file took: "+(System.currentTimeMillis() - starttime)+"ms");
				System.exit(0);
			}
		} catch (ConfigurationException e) {
			System.out.println("Configuration exception in put: " + e.getMessage());
			e.printStackTrace();
		} catch (MalformedContentNameStringException e) {
			System.out.println("Malformed name: " + args[startArg] + " " + e.getMessage());
			e.printStackTrace();
		} catch (IOException e) {
			System.out.println("Cannot read file. " + e.getMessage());
			e.printStackTrace();
		}
		System.exit(1);

	}
	
	private static void do_write(CCNOutputStream ostream, File file) throws IOException {
		long time = System.currentTimeMillis();
		FileInputStream fis = new FileInputStream(file);
		int size = BLOCK_SIZE;
		int readLen = 0;
		byte [] buffer = new byte[BLOCK_SIZE];
		//do {
		Library.logger().info("do_write: " + fis.available() + " bytes left.");
		while((readLen = fis.read(buffer, 0, size)) != -1){	
			//if (size > fis.available())
			//	size = fis.available();
			//if (size > 0) {
			//	fis.read(buffer, 0, size);
			//	ostream.write(buffer, 0, size);
			ostream.write(buffer, 0, readLen);
			Library.logger().info("do_write: wrote " + size + " bytes.");
			Library.logger().info("do_write: " + fis.available() + " bytes left.");
		}
		//} while (fis.available() > 0);
		ostream.close();
		Library.logger().info("finished write: "+(System.currentTimeMillis() - time));
	}
	
	public static void usage() {
		System.out.println("usage: put_file [-raw] [-unversioned] [-timeout millis] [-log level] <ccnname> <filename> [<filename> ...]");
		System.exit(1);
	}

}
