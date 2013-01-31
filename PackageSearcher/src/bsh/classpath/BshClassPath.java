/*****************************************************************************
 *                                                                           *
 *  This file is part of the BeanShell Java Scripting distribution.          *
 *  Documentation and updates may be found at http://www.beanshell.org/      *
 *                                                                           *
 *  Sun Public License Notice:                                               *
 *                                                                           *
 *  The contents of this file are subject to the Sun Public License Version  *
 *  1.0 (the "License"); you may not use this file except in compliance with *
 *  the License. A copy of the License is available at http://www.sun.com    *
 *                                                                           *
 *  The Original Code is BeanShell. The Initial Developer of the Original    *
 *  Code is Pat Niemeyer. Portions created by Pat Niemeyer are Copyright     *
 *  (C) 2000.  All Rights Reserved.                                          *
 *                                                                           *
 *  GNU Public License Notice:                                               *
 *                                                                           *
 *  Alternatively, the contents of this file may be used under the terms of  *
 *  the GNU Lesser General Public License (the "LGPL"), in which case the    *
 *  provisions of LGPL are applicable instead of those above. If you wish to *
 *  allow use of your version of this file only under the  terms of the LGPL *
 *  and not to allow others to use your version of this file under the SPL,  *
 *  indicate your decision by deleting the provisions above and replace      *
 *  them with the notice and other provisions required by the LGPL.  If you  *
 *  do not delete the provisions above, a recipient may use your version of  *
 *  this file under either the SPL or the LGPL.                              *
 *                                                                           *
 *  Patrick Niemeyer (pat@pat.net)                                           *
 *  Author of Learning Java, O'Reilly & Associates                           *
 *  http://www.pat.net/~pat/                                                 *
 *                                                                           *
 *****************************************************************************/

package bsh.classpath;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import bsh.ClassPathException;
import bsh.StringUtil;

/**
 * A BshClassPath encapsulates knowledge about a class path of URLs.
 * It can maps all classes the path which may include:
 * jar/zip files and base dirs
 * A BshClassPath may composite other BshClassPaths as components of its
 * path and will reflect changes in those components through its methods
 * and listener interface.
 * Classpath traversal is done lazily when a call is made to
 * getClassesForPackage() or getClassSource()
 * or can be done explicitily through insureInitialized().
 * Feedback on mapping progress is provided through the MappingFeedback
 * interface.
 * Design notes:
 * Several times here we traverse ourselves and our component paths to
 * produce a composite view of some thing relating to the path. This would
 * be an opportunity for a visitor pattern.
 */
public class BshClassPath {
	String name;

	/** The URL path components */
	private List<URL> path;
	/** Ordered list of components BshClassPaths */
	private List<BshClassPath> compPaths;

	/** Set of classes in a package mapped by package name */
	private Map<String, HashSet<String>> packageMap;
	/** Map of source (URL or File dir) of every clas */
	private Map<String, Object> classSource;
	/** The packageMap and classSource maps have been built. */
	private boolean mapsInitialized;



	// constructors

	public BshClassPath(String name) {

		this.name = name;
		reset();
	}

	public BshClassPath(String name, URL[] urls) {

		this(name);
		add(urls);
	}

	// end constructors

	// mutators

	public void setPath(URL[] urls) {
		reset();
		add(urls);
	}

	public void add(URL[] urls) {
		path.addAll(Arrays.asList(urls));
		if (mapsInitialized) {
			map(urls);
		}
	}

	public void add(URL url) throws IOException {
		path.add(url);
		if (mapsInitialized) {
			map(url);
		}
	}

	/**
	 * Return the set of class names in the specified package
	 * including all component paths.
	 */
	public synchronized HashSet<String> getClassesForPackage(String pack) {

		insureInitialized();
		HashSet<String> set = new HashSet<String>();
		HashSet<String> c = packageMap.get(pack);
		if (c != null) {
			set.addAll(c);
		}

		if (compPaths != null) {
			for (int i = 0; i < compPaths.size(); i++) {
				c = ((BshClassPath) compPaths.get(i)).getClassesForPackage(pack);
				if (c != null) {
					set.addAll(c);
				}
			}
		}
		return set;
	}




	/**
	 * If the claspath map is not initialized, do it now.
	 * If component maps are not do them as well...
	 * Random note:
	 * Should this be "insure" or "ensure". I know I've seen "ensure" used
	 * in the JDK source. Here's what Webster has to say:
	 * Main Entry:ensure Pronunciation:in-'shur
	 * Function:transitive verb Inflected
	 * Form(s):ensured; ensuring : to make sure,
	 * certain, or safe : GUARANTEE synonyms ENSURE,
	 * INSURE, ASSURE, SECURE mean to make a thing or
	 * person sure. ENSURE, INSURE, and ASSURE are
	 * interchangeable in many contexts where they
	 * indicate the making certain or inevitable of an
	 * outcome, but INSURE sometimes stresses the
	 * taking of necessary measures beforehand, and
	 * ASSURE distinctively implies the removal of
	 * doubt and suspense from a person's mind. SECURE
	 * implies action taken to guard against attack or
	 * loss.
	 */
	public void insureInitialized() {

		insureInitialized(true);
	}

	/**
	 * @param topPath
	 *            indicates that this is the top level classpath
	 *            component and it should send the startClassMapping message
	 */
	protected synchronized void insureInitialized(boolean topPath) {

		// If we are the top path and haven't been initialized before
		// inform the listeners we are going to do expensive map
		if (topPath && !mapsInitialized) {
			startClassMapping();
		}

		// initialize components
		if (compPaths != null) {
			for (int i = 0; i < compPaths.size(); i++) {
				((BshClassPath) compPaths.get(i)).insureInitialized(false);
			}
		}

		// initialize ourself
		if (!mapsInitialized) {
			map((URL[]) path.toArray(new URL[0]));
		}

		if (topPath && !mapsInitialized) {
			endClassMapping();
		}

		mapsInitialized = true;
	}


	/**
	 * call map(url) for each url in the array
	 */
	synchronized void map(URL[] urls) {

		for (int i = 0; i < urls.length; i++) {
			try {
				map(urls[i]);
			} catch (IOException e) {
				String s = "Error constructing classpath: " + urls[i] + ": " + e;
				errorWhileMapping(s);
			}
		}
	}

	synchronized void map(URL url) throws IOException {

		String name = url.getFile();
		File f = new File(name);

		if (f.isDirectory()) {
			classMapping("Directory " + f.toString());
			map(traverseDirForClasses(f), new DirClassSource(f));
		} else if (isArchiveFileName(name)) {
			classMapping("Archive: " + url);
			map(searchJarForClasses(url), new JarClassSource(url));
		} else {
			String s = "Not a classpath component: " + name;
			errorWhileMapping(s);
		}
	}

	private void map(String[] classes, Object source) {

		for (int i = 0; i < classes.length; i++) {
			mapClass(classes[i], source);
		}
	}

	private void mapClass(String className, Object source) {

		// add to package map
		String[] sa = splitClassname(className);
		String pack = sa[0];
		HashSet<String> set = packageMap.get(pack);
		if (set == null) {
			set = new HashSet<String>();
			packageMap.put(pack, set);
		}
		set.add(className);

		// Add to classSource map
		Object obj = classSource.get(className);
		// don't replace previously set (found earlier in classpath or
		// explicitly set via setClassSource() )
		if (obj == null) {
			classSource.put(className, source);
		}
	}

	/**
	 * Clear everything and reset the path to empty.
	 */
	private synchronized void reset() {

		path = new ArrayList<URL>();
		compPaths = null;
		clearCachedStructures();
	}

	/**
	 * Clear anything cached. All will be reconstructed as necessary.
	 */
	private synchronized void clearCachedStructures() {

		mapsInitialized = false;
		packageMap = new HashMap<String, HashSet<String>>();
		classSource = new HashMap<String, Object>();
	}

	// Begin Static stuff

	static String[] traverseDirForClasses(File dir) throws IOException {

		List<String> list = traverseDirForClassesAux(dir, dir);
		return list.toArray(new String[0]);
	}

	static List<String> traverseDirForClassesAux(File topDir, File dir) throws IOException {

		List<String> list = new ArrayList<String>();
		String top = topDir.getAbsolutePath();

		File[] children = dir.listFiles();
		for (int i = 0; i < children.length; i++) {
			File child = children[i];
			if (child.isDirectory()) {
				list.addAll(traverseDirForClassesAux(topDir, child));
			} else {
				String name = child.getAbsolutePath();
				if (isClassFileName(name)) {
					/*
					 * Remove absolute (topdir) portion of path and leave
					 * package-class part
					 */
					if (name.startsWith(top)) {
						name = name.substring(top.length() + 1);
					} else {
						throw new IOException("problem parsing paths");
					}

					name = canonicalizeClassName(name);
					list.add(name);
				}
			}
		}


		return list;
	}

	/**
	 * Get the class file entries from the Jar
	 */
	static String[] searchJarForClasses(URL jar) throws IOException {

		Vector<String> v = new Vector<String>();
		InputStream in = jar.openStream();
		ZipInputStream zin = new ZipInputStream(in);

		ZipEntry ze;
		while ((ze = zin.getNextEntry()) != null) {
			String name = ze.getName();
			if (isClassFileName(name)) {
				v.addElement(canonicalizeClassName(name));
			}
		}
		zin.close();

		String[] sa = new String[v.size()];
		v.copyInto(sa);
		return sa;
	}

	public static boolean isClassFileName(String name) {

		return (name.toLowerCase().endsWith(".class"));
	}

	public static boolean isArchiveFileName(String name) {

		name = name.toLowerCase();
		return (name.endsWith(".jar") || name.endsWith(".zip"));
	}

	/**
	 * Create a proper class name from a messy thing.
	 * Turn / or \ into ., remove leading class and trailing .class
	 * Note: this makes lots of strings... could be faster.
	 */
	public static String canonicalizeClassName(String name) {

		String classname = name.replace('/', '.');
		classname = classname.replace('\\', '.');
		if (classname.startsWith("class ")) {
			classname = classname.substring(6);
		}
		if (classname.endsWith(".class")) {
			classname = classname.substring(0, classname.length() - 6);
		}
		return classname;
	}

	/**
	 * Split class name into package and name
	 */
	public static String[] splitClassname(String classname) {

		classname = canonicalizeClassName(classname);

		int i = classname.lastIndexOf(".");
		String classn, packn;
		if (i == -1) {
			// top level class
			classn = classname;
			packn = "<unpackaged>";
		} else {
			packn = classname.substring(0, i);
			classn = classname.substring(i + 1);
		}
		return new String[] { packn, classn };
	}

	/**
	 * Return a new collection without any inner class names
	 */
	public static ArrayList<String> removeInnerClassNames(Collection<String> col) {
		ArrayList<String> list = new ArrayList<String>();
		list.addAll(col);
		Iterator<String> it = list.iterator();
		while (it.hasNext()) {
			String name = (String) it.next();
			if (name.indexOf("$") != -1) {
				it.remove();
			}
		}
		return list;
	}

	/**
	 * The user classpath from system property
	 * java.class.path
	 */

	static URL[] userClassPathComp;

	public static URL[] getUserClassPathComponents() throws ClassPathException {

		if (userClassPathComp != null) {
			return userClassPathComp;
		}

		String cp = System.getProperty("java.class.path");
		String[] paths = StringUtil.split(cp, File.pathSeparator);

		URL[] urls = new URL[paths.length];
		try {
			for (int i = 0; i < paths.length; i++) {
				// We take care to get the canonical path first.
				// Java deals with relative paths for it's bootstrap loader
				// but JARClassLoader doesn't.
				urls[i] = new File(new File(paths[i]).getCanonicalPath()).toURI().toURL();
			}
		} catch (IOException e) {
			throw new ClassPathException("can't parse class path: " + e);
		}

		userClassPathComp = urls;
		return urls;
	}

	/**
	 * Get a list of all of the known packages
	 */
	public HashSet<String> getPackagesSet() {

		insureInitialized();
		HashSet<String> set = new HashSet<String>();
		set.addAll(packageMap.keySet());

		if (compPaths != null) {
			for (int i = 0; i < compPaths.size(); i++) {
				set.addAll(((BshClassPath) compPaths.get(i)).packageMap.keySet());
			}
		}
		return set;
	}
	static BshClassPath userClassPath;

	/**
	 * A BshClassPath initialized to the user path
	 * from java.class.path
	 */
	public static BshClassPath getUserClassPath() throws ClassPathException {

		if (userClassPath == null) {
			userClassPath = new BshClassPath("User Class Path", getUserClassPathComponents());
		}
		return userClassPath;
	}

	public abstract static class ClassSource {
		Object source;

		abstract byte[] getCode(String className);
	}

	public static class JarClassSource extends ClassSource {
		JarClassSource(URL url) {
			source = url;
		}

		public URL getURL() {
			return (URL) source;
		}

		/*
		 * Note: we should implement this for consistency, however our
		 * BshClassLoader can natively load from a JAR because it is a
		 * URLClassLoader... so it may be better to allow it to do it.
		 */
		public byte[] getCode(String className) {
			throw new Error("Unimplemented");
		}

		public String toString() {
			return "Jar: " + source;
		}
	}

	public static class DirClassSource extends ClassSource {
		DirClassSource(File dir) {
			source = dir;
		}

		public File getDir() {
			return (File) source;
		}

		public String toString() {
			return "Dir: " + source;
		}

		public byte[] getCode(String className) {
			return readBytesFromFile(getDir(), className);
		}

		public static byte[] readBytesFromFile(File base, String className) {
			String n = className.replace('.', File.separatorChar) + ".class";
			File file = new File(base, n);

			if (file == null || !file.exists()) {
				return null;
			}

			byte[] bytes;
			try {
				FileInputStream fis = new FileInputStream(file);
				DataInputStream dis = new DataInputStream(fis);

				bytes = new byte[(int) file.length()];

				dis.readFully(bytes);
				dis.close();
			} catch (IOException ie) {
				throw new RuntimeException("Couldn't load file: " + file);
			}

			return bytes;
		}

	}

	public static class GeneratedClassSource extends ClassSource {
		GeneratedClassSource(byte[] bytecode) {
			source = bytecode;
		}

		public byte[] getCode(String className) {
			return (byte[]) source;
		}
	}

	public String toString() {
		return "BshClassPath " + name + "(" + super.toString() + ") path= " + path + "\n" + "compPaths = {" + compPaths
				+ " }";
	}

	void startClassMapping() {
		System.out.println("Start ClassPath Mapping");
	}

	void classMapping(String msg) {
		System.out.println("Mapping: " + msg);
	}

	void errorWhileMapping(String s) {
		System.err.println(s);
	}

	void endClassMapping() {
		System.out.println("End ClassPath Mapping");
	}
}
