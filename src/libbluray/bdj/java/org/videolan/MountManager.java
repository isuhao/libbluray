/*
 * This file is part of libbluray
 * Copyright (C) 2010  William Hahne
 * Copyright (C) 2014  Petri Hintukainen
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library. If not, see
 * <http://www.gnu.org/licenses/>.
 */

package org.videolan;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * This class handle mounting jar files so that their contents can be accessed.
 *
 * @author William Hahne
 *
 */
public class MountManager {

    public static String mount(int jarId) throws MountException {
        return mount(jarId, true);
    }

    protected static String mount(int jarId, boolean classFiles) throws MountException {
        String jarStr = jarIdToString(jarId);

        logger.info("Mounting JAR: " + jarStr);

        if (jarStr == null)
            throw new IllegalArgumentException();

        synchronized (mountPoints) {

            // already mounted ?
            MountPoint mountPoint = (MountPoint)mountPoints.get(new Integer(jarId));
            if (mountPoint != null) {
                logger.info("JAR " + jarId + " already mounted");
                mountPoint.incRefCount();

                if (classFiles && !mountPoint.classFiles()) {
                    logger.info("JAR " + jarId + " not complete, remounting");
                } else {
                    return mountPoint.getMountPoint();
                }
            }

        String path = System.getProperty("bluray.vfs.root") + "/BDMV/JAR/" + jarStr + ".jar";

        JarFile jar = null;
        try {
            jar = new JarFile(path);
            if (mountPoint == null) {
                mountPoint = new MountPoint(jarStr, classFiles);
            }
        } catch (IOException e) {
            e.printStackTrace();
            if (jar != null) {
                try {
                    jar.close();
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
            }
            throw new MountException();
        }

        try {
            byte[] buffer = new byte[32*1024];
            Enumeration entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = (JarEntry)entries.nextElement();
                File out = new File(mountPoint.getMountPoint() + File.separator + entry.getName());

                if (entry.isDirectory()) {
                    out.mkdirs();
                } else if (!classFiles && entry.getName().endsWith(".class")) {
                    //logger.info("skip " + entry.getName());
                } else {
                    /* make sure path exists */
                    out.getParentFile().mkdirs();

                    logger.info("   mount: " + entry.getName());

                    InputStream inStream = jar.getInputStream(entry);
                    OutputStream outStream = new FileOutputStream(out);

                    int length;
                    while ((length = inStream.read(buffer)) > 0) {
                        outStream.write(buffer, 0, length);
                    }

                    inStream.close();
                    outStream.close();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            mountPoint.remove();
            throw new MountException();
        }

        if (mountPoint.classFiles() != classFiles) {
            if (mountPoint.classFiles()) {
                logger.error("assertion failed");
            } else {
                logger.info("Remounting FULL JAR " + jarId + " complete.");
                mountPoint.setClassFiles();
            }
        } else {
            logger.info("Mounting " + (classFiles ? "FULL" : "PARTIAL") +
                        " JAR " + jarId + " complete.");

            mountPoints.put(new Integer(jarId), mountPoint);
        }

        return mountPoint.getMountPoint();
        }
    }

    public static void unmount(int jarId) {
        logger.info("Unmounting JAR: " + jarId);

        Integer id = new Integer(jarId);
        MountPoint mountPoint;

        synchronized (mountPoints) {
            mountPoint = (MountPoint)mountPoints.get(id);
            if (mountPoint == null) {
                logger.info("JAR " + jarId + " not mounted");
                return;
            }

            if (mountPoint.decRefCount() < 1) {
                mountPoints.remove(id);
            }
        }
    }

    public static void unmountAll() {
        logger.info("Unmounting all JARs");

        Object[] dirs;

        synchronized (mountPoints) {
            dirs = mountPoints.values().toArray();
            mountPoints.clear();
        }
        if (dirs != null) {
            for (int i = 0; i < dirs.length; i++) {
                ((MountPoint)dirs[i]).remove();
            }
        }
    }

    public static String getMount(int jarId) {
        Integer id = new Integer(jarId);
        MountPoint mountPoint;

        synchronized (mountPoints) {
            mountPoint = (MountPoint)mountPoints.get(id);
        }
        if (mountPoint != null) {
            return mountPoint.getMountPoint();
        } else {
            logger.info("JAR " + jarId + " not mounted");
        }
        return null;
    }

    private static String jarIdToString(int jarId) {
        if (jarId < 0 || jarId > 99999)
            return null;
        return BDJUtil.makeFiveDigitStr(jarId);
    }

    private static Map mountPoints = new HashMap();
    private static final Logger logger = Logger.getLogger(MountManager.class.getName());

    private static class MountPoint {
        public MountPoint(String id, boolean classFiles) throws IOException {
            this.dir      = CacheDir.create("mount", id);
            this.refCount = 1;
            this.classFiles = classFiles;
        }

        public synchronized String getMountPoint() {
            if (dir != null) {
                return dir.getAbsolutePath();
            }
            return null;
        }

        public synchronized void remove() {
            if (dir != null) {
                CacheDir.remove(dir);
                dir = null;
                refCount = 0;
            }
        }

        public synchronized int incRefCount() {
            return ++refCount;
        }

        public synchronized int decRefCount() {
            refCount--;
            if (refCount < 1) {
                remove();
            }
            return refCount;
        }

        public boolean classFiles() {
            return classFiles;
        }
        public boolean setClassFiles() {
            return classFiles == true;
        }

        private File dir;
        private int  refCount;
        private boolean classFiles;
    };
}
