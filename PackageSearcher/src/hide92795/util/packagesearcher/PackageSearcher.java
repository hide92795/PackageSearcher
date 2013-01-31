package hide92795.util.packagesearcher;

import java.util.HashSet;
import bsh.ClassPathException;
import bsh.classpath.BshClassPath;

public class PackageSearcher {
	private static BshClassPath bcp;
	private static boolean init;

	private PackageSearcher() {
	}

	public static void init() throws ClassPathException {
		if (!init) {
			bcp = BshClassPath.getUserClassPath();
		}
	}

	public static HashSet<String> search(String packageName) {
		if (init) {
			return bcp.getClassesForPackage(packageName);
		}
		throw new RuntimeException("Must be initialized!");
	}
}
