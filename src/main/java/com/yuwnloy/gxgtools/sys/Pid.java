package com.yuwnloy.gxgtools.sys;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;


/*
 * 
 * Create by www
 * 
 * Author: Nathan Wong
 *
 * 2012-11-6
 *
 * Copyright (c) 2011 
 */
public final class Pid {

	private Pid() {
		super();
	}

	/**
	 * 
	 * @author nathan wong
	 * @created 2012-1-18
	 * 
	 * @return java pid
	 */
	public static final String getPID() {
		String pid = System.getProperty("pid");
		if (pid == null) {
			RuntimeMXBean runtimeMXBean = ManagementFactory.getRuntimeMXBean();
			String processName = runtimeMXBean.getName();
			if (processName.indexOf('@') != -1) {
				pid = processName.substring(0, processName.indexOf('@'));
			} else {
				pid = getPIDFromOS();
			}
			System.setProperty("pid", pid);
		}
		return pid;
	}

	/**
	 * get pid from os
	 * <p>
	 * for windows，refer to :http://www.scheibli.com/projects/getpids/index.html
	 * 
	 * @author lichengwu
	 * @created 2012-1-18
	 *
	 * @return
	 */
	private static String getPIDFromOS() {

		String pid = null;

		String[] cmd = null;

		File tempFile = null;

		String osName = System.getProperty("os.name");
		//for windows
		if (osName.toLowerCase().contains("windows")) {
			FileInputStream fis = null;
			FileOutputStream fos = null;

			try {
				//create temp getpids.exe
				tempFile = File.createTempFile("getpids", ".exe");
				System.err.println("created temp file.");
				File getpids = new File(Pid.class.getClassLoader().getResource("getpids.exe").getFile());
				fis = new FileInputStream(getpids);
				fos = new FileOutputStream(tempFile);
				byte[] buf = new byte[1024];
				while (fis.read(buf) != -1) {
					fos.write(buf);
				}
				// get path of getpids.exe
				cmd = new String[] { tempFile.getAbsolutePath() };
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			} finally {
				if (tempFile != null) {
					tempFile.deleteOnExit();
				}
				try{
					if(fis != null){
						fis.close();
					}
				}catch(Exception e){}
				try{
					if(fos != null){
						fos.close();
					}
				}catch(Exception e){}
			}
		}
		// for non-windows
		else {
			cmd = new String[] { "/bin/sh", "-c", "echo $$ $PPID" };
		}
		InputStream is = null;
		ByteArrayOutputStream baos = null;
		try {
			byte[] buf = new byte[1024];
			Process exec = Runtime.getRuntime().exec(cmd);
			is = exec.getInputStream();
			baos = new ByteArrayOutputStream();
			while (is.read(buf) != -1) {
				baos.write(buf);
			}
			String ppids = baos.toString();
			// for windows refer to：http://www.scheibli.com/projects/getpids/index.html
			pid = ppids.split(" ")[1];
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			if (tempFile != null) {
				tempFile.deleteOnExit();
			}
			try{
				if(is != null){
					is.close();
				}
			}catch(Exception e){}
			try{
				if(baos != null){
					baos.close();
				}
			}catch(Exception e){}
		}
		return pid;
	}
}