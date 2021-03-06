/* Copyright 2010-2019 Norconex Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.norconex.commons.lang;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.List;
import java.util.function.Predicate;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for finding names of classes implementing an interface or class
 * in directories or JAR files. In order to find if a class is potential
 * candidate, it is "loaded" first, but into a temporary class loader.
 * Still, if it is important to you that classes do not get loaded, you can
 * use other approaches, such as byte-code scanning.   See
 * <a href="http://commons.apache.org/sandbox/classscan/">Apache ClassScan</a>
 * sandbox project for code that does that.
 *
 * @author Pascal Essiembre
 */
public final class ClassFinder {

    //TODO cache top X mappings for performance


    private static final Logger LOG =
            LoggerFactory.getLogger(ClassFinder.class);

    private ClassFinder() {
        super();
    }

    /**
     * Finds the names of all subtypes of the super class,
     * scanning the roots of this class classpath.
     * This method is null-safe.  If no classes are found,
     * an empty list will be returned.
     * @param superClass the class from which to find subtypes
     * @return list of class names
     * @since 1.4.0
     */
    public static List<String> findSubTypes(Class<?> superClass) {
        return findSubTypes(superClass, null);
    }
    /**
     * Finds the names of all subtypes of the super class,
     * scanning the roots of this class classpath.
     * This method is null-safe.  If no classes are found,
     * an empty list will be returned.
     * @param superClass the class from which to find subtypes
     * @param accept filter to keep classes testing <code>true</code>
     * @return list of class names
     * @since 2.0.0
     */
    public static List<String> findSubTypes(
            Class<?> superClass, Predicate<String> accept) {
        List<String> classes = new ArrayList<>();
        if (superClass == null) {
            return classes;
        }
        Enumeration<URL> roots;
        try {
            roots = ClassFinder.class.getClassLoader().getResources("");
        } catch (IOException e) {
            LOG.error("Cannot obtain class roots for class: "
                    + superClass, e);
            return classes;
        }
        while (roots.hasMoreElements()) {
            URL url = roots.nextElement();
            File root = new File(url.getPath());
            classes.addAll(findSubTypes(root, superClass, accept));
        }
        return classes;
    }

    /**
     * Finds the names of all subtypes of the super class in list
     * of {@link File} supplied.
     * This method is null-safe.  If no classes are found,
     * an empty list will be returned.
     * @param files directories and/or JARs to scan for classes
     * @param superClass the class from which to find subtypes
     * @return list of class names
     * @since 1.4.0
     */
    public static List<String> findSubTypes(
            List<File> files, Class<?> superClass) {
        return findSubTypes(files, superClass, null);
    }
    /**
     * Finds the names of all subtypes of the super class in list
     * of {@link File} supplied.
     * This method is null-safe.  If no classes are found,
     * an empty list will be returned.
     * @param files directories and/or JARs to scan for classes
     * @param superClass the class from which to find subtypes
     * @param accept filter to keep classes testing <code>true</code>
     * @return list of class names
     * @since 2.0.0
     */
    public static List<String> findSubTypes(
            List<File> files, Class<?> superClass, Predicate<String> accept) {
        List<String> classes = new ArrayList<>();
        if (superClass == null || files == null) {
            return classes;
        }
        for (File file : files) {
            classes.addAll(findSubTypes(file, superClass, accept));
        }
        return classes;
    }

    /**
     * Finds the names of all subtypes of the super class for the
     * supplied {@link File}.
     * This method is null-safe.  If no classes are found,
     * an empty list will be returned.
     * If the file is null or does not exists, or if it is not a JAR or
     * directory, an empty string list will be returned.
     * @param file directory or JAR to scan for classes
     * @param superClass the class from which to find subtypes
     * @return list of class names
     * @since 1.4.0
     */
    public static List<String> findSubTypes(File file, Class<?> superClass) {
        return findSubTypes(file, superClass, null);
    }
    /**
     * Finds the names of all subtypes of the super class for the
     * supplied {@link File}.
     * This method is null-safe.  If no classes are found,
     * an empty list will be returned.
     * If the file is null or does not exists, or if it is not a JAR or
     * directory, an empty string list will be returned.
     * @param file directory or JAR to scan for classes
     * @param superClass the class from which to find subtypes
     * @param accept filter to keep classes testing <code>true</code>
     * @return list of class names
     * @since 2.0.0
     */
    public static List<String> findSubTypes(
            File file, Class<?> superClass, Predicate<String> accept) {
        if (superClass == null) {
            return new ArrayList<>();
        }
        if (file == null || !file.exists()) {
            LOG.warn("Trying to find implementing classes from a null or "
                   + "non-existant file: {}", file);
            return new ArrayList<>();
        }
        if (file.isDirectory()) {
            return findSubTypesFromDirectory(
                    new File(file.getAbsolutePath() + File.separatorChar),
                    superClass, accept);
        }
        if (file.getName().endsWith(".jar")) {
            return findSubTypesFromJar(file, superClass, accept);
        }
        LOG.warn("File not a JAR and not a directory.");
        return new ArrayList<>();
    }

    // predicate is null-safe
    private static List<String> findSubTypesFromDirectory(
            File dir, Class<?> superClass, Predicate<String> predicate) {
        List<String> classes = new ArrayList<>();
        String dirPath = dir.getAbsolutePath();

        Collection<File> classFiles = FileUtils.listFiles(
                dir, new String[] {"class"}, true);
        ClassLoader loader = getClassLoader(dir);
        if (loader == null) {
            return classes;
        }

        for (File classFile : classFiles) {
            String filePath = classFile.getAbsolutePath();
            String className = StringUtils.removeStart(filePath, dirPath);
            className = resolveName(loader, className, superClass, predicate);
            if (className != null) {
                classes.add(className);
            }
        }
        return classes;
    }
    // predicate is null-safe
    private static List<String> findSubTypesFromJar(
            File jarFile, Class<?> superClass, Predicate<String> predicate) {

        List<String> classes = new ArrayList<>();
        ClassLoader loader = getClassLoader(jarFile);
        if (loader == null) {
            return classes;
        }
        JarFile jar = null;
        try {
            jar = new JarFile(jarFile);
            jar.close();
        } catch (IOException e) {
            LOG.error("Invalid JAR: " + jarFile, e);
            return classes;
        }
        Enumeration<JarEntry> entries = jar.entries();
        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            String className = entry.getName();
            className = resolveName(loader, className, superClass, predicate);
            if (className != null) {
                classes.add(className);
            }
        }
        try {
            jar.close();
        } catch (IOException e) {
            LOG.error("Could not close JAR.", e);
        }
        return classes;
    }

    private static ClassLoader getClassLoader(File url) {
        try {
            URL dirURL = url.toURI().toURL();
            return new URLClassLoader(
                    new URL[] {dirURL},
                    ClassFinder.class.getClassLoader());
        } catch (MalformedURLException e) {
            LOG.error("Invalid classpath: " + url, e);
            return null;
        }
    }

    // predicate is null-safe
    private static String resolveName(
            ClassLoader loader, String rawName,
            Class<?> superClass, Predicate<String> predicate) {
        if (!rawName.endsWith(".class")
                || rawName.contains("$")
                || rawName.substring(1).equals("module-info.class")) {
            return null;
        }

        String className = rawName;
        className = className.replaceAll("[\\\\/]", ".");
        className = StringUtils.removeStart(className, ".");
        className = StringUtils.removeEnd(className, ".class");

        if (predicate != null && !predicate.test(className)) {
            return null;
        }

        try {
            Class<?> clazz = loader.loadClass(className);
            // load only concrete implementations
            if (!clazz.isInterface()
                    && !Modifier.isAbstract(clazz.getModifiers())
                    && superClass.isAssignableFrom(clazz)) {
                return clazz.getName();
            }
        } catch (UnsupportedClassVersionError | ClassNotFoundException e) {
            LOG.error("Invalid class: \"{}\"", className, e);
        } catch (NoClassDefFoundError e) {
            LOG.debug("Invalid class: \"{}\"", className, e);
        }
        return null;
    }
}
