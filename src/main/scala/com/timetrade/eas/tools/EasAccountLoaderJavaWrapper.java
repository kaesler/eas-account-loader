package com.timetrade.eas.tools;

/**
 * Java wrapper application so that Eclipse's "Export runnable jar"
 * can be used to export a runnable jar.
 * To use:
 *   1. Make sure that the Eclipse project has the scala-library.jar
 *      included as an external jar dependency.
 *   2. Create a "Run as Java Application" run config for this.
 *   3. In Eclipse "Export runnable jar" for that run configuration.
 */
public class EasAccountLoaderJavaWrapper {
    public static void main(String[] args) {
        EasAccountLoader.main(args);
    }
}
