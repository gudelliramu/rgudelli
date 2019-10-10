package org.gudelli.archetype.utils;

import java.io.File;

public class FileUtils {

    private static final String SYNCHRONIZED = "SYNCHRONIZED";

    public static File createTempFile(String prefix, String suffix, File parentDir) {

        File result = null;
        String parent = System.getProperty("java.io.tmpdir");
        if (parentDir != null) {
            parent = parentDir.getPath();
        }

        synchronized (SYNCHRONIZED) {
            result = new File(parent, prefix + suffix);
        }

        return result;

    }

}
