/**
 * Copyright @ 2015 Quan Nguyen
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package net.sourceforge.lept4j.util;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Logger;
import java.util.logging.Level;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import com.sun.jna.Native;
import com.sun.jna.Platform;

import net.sourceforge.lept4j.Leptonica;

/**
 * Loads native libraries from JAR or project folder.
 *
 * @author O.J. Sousa Rodrigues
 * @author Quan Nguyen
 */
public class LoadLibs {

    private static final String JNA_LIBRARY_PATH = "jna.library.path";
    public static final String LEPT4J_TEMP_DIR = new File(System.getProperty("java.io.tmpdir"), "lept4j").getPath();

    /**
     * Native library name.
     */
    public static final String LIB_NAME = "liblept1753";
    public static final String LIB_NAME_NON_WIN = "lept";

    private final static Logger logger = Logger.getLogger(LoadLibs.class.getName());

    static {
        System.setProperty("jna.encoding", "UTF8");
        String userCustomizedPath = System.getProperty(JNA_LIBRARY_PATH);
        File targetTempFolder = extractNativeResources(Platform.RESOURCE_PREFIX);
        if (targetTempFolder != null && targetTempFolder.exists()) {
            if (null == userCustomizedPath || userCustomizedPath.isEmpty()) {
                System.setProperty(JNA_LIBRARY_PATH, targetTempFolder.getPath());
            } else {
                System.setProperty(JNA_LIBRARY_PATH, userCustomizedPath + File.pathSeparator + targetTempFolder.getPath());
            }
            logger.log(Level.INFO, "Leptonica: Bundled library for "  + Platform.RESOURCE_PREFIX + " copied to "
                    + targetTempFolder.getPath() + " which was added to jna.library.path.");
        } else {
            logger.log(Level.INFO, "Leptonica: No bundled system library found, expecting it to be in default system library location"
                    + (userCustomizedPath==null ? "" : " or " + userCustomizedPath) + '.');
        }
    }

    /**
     * Loads Leptonica library via JNA.
     *
     * @return Leptonica instance being loaded using
     * <code>Native.loadLibrary()</code>.
     */
    public static Leptonica getLeptonicaInstance() {
        final Leptonica leptonica = (Leptonica) Native.loadLibrary(getLeptonicaLibName(), Leptonica.class);
        if (leptonica==null || leptonica.getLeptonicaVersion()==null) {
            logger.log(Level.WARNING, "Leptonica: error accessing library.");
        } else {
            logger.log(Level.INFO, "Leptonica loaded in version " + leptonica.getLeptonicaVersion().getString(0));
        }
        return leptonica;
    }

    /**
     * Gets native library name.
     *
     * @return the name of the Leptonica library to be loaded using the
     * <code>Native.register()</code>.
     */
    public static String getLeptonicaLibName() {
        return Platform.isWindows() ? LIB_NAME : LIB_NAME_NON_WIN;
    }

    /**
     * Extracts Leptonica resources to temp folder.
     *
     * @param dirname resource location
     * @return target location
     */
    public static File extractNativeResources(String dirname) {
        try {
            File targetTempDir = new File(LEPT4J_TEMP_DIR, dirname);

            URL leptResourceUrl = LoadLibs.class.getResource(dirname.startsWith("/") ? dirname : "/" + dirname);
            if (leptResourceUrl == null) {
                return null;
            }

            URLConnection urlConnection = leptResourceUrl.openConnection();

            /**
             * Either load from resources from jar or project resource folder.
             */
            if (urlConnection instanceof JarURLConnection) {
                return copyJarResourceToDirectory((JarURLConnection) urlConnection, targetTempDir) ? targetTempDir : null;
            } else {
                File libDir = new File(leptResourceUrl.getPath());
                if (libDir.exists() && libDir.isDirectory()) {
                    FileUtils.copyDirectory(new File(leptResourceUrl.getPath()), targetTempDir);
                    return targetTempDir;
                } else {
                    return null;
                }
            }
        } catch (Exception e) {
            logger.log(Level.WARNING, e.getMessage(), e);
        }

        return null;
    }

    /**
     * Copies resources from the jar file of the current thread and extract it
     * to the destination directory.
     *
     * @param jarConnection
     * @param destDir
     */
    static boolean copyJarResourceToDirectory(JarURLConnection jarConnection, File destDir) {
        boolean foundResources = false;
        try {
            JarFile jarFile = jarConnection.getJarFile();
            String jarConnectionEntryName = jarConnection.getEntryName() + "/";

            /**
             * Iterate all entries in the jar file.
             */
            for (Enumeration<JarEntry> e = jarFile.entries(); e.hasMoreElements();) {
                JarEntry jarEntry = e.nextElement();
                String jarEntryName = jarEntry.getName();

                /**
                 * Extract files only if they match the path.
                 */
                if (jarEntryName.startsWith(jarConnectionEntryName)) {
                    String filename = jarEntryName.substring(jarConnectionEntryName.length());
                    File targetFile = new File(destDir, filename);
                    foundResources = true;

                    if (jarEntry.isDirectory()) {
                        targetFile.mkdirs();
                    } else {
                        if (!targetFile.exists() || targetFile.length() != jarEntry.getSize()) {
                            try (InputStream is = jarFile.getInputStream(jarEntry);
                                    OutputStream out = FileUtils.openOutputStream(targetFile)) {
                                IOUtils.copy(is, out);
                            }
                        }
                    }
                }
            }
        } catch (IOException e) {
            logger.log(Level.WARNING, e.getMessage(), e);
        }
        return foundResources;
    }
}
