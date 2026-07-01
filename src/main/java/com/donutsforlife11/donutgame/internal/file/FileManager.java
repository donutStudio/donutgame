package com.donutsforlife11.donutgame.internal.file;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class FileManager {
    public static List<File> getFolderContentsRecursive(File folder, String extension) {
        List<File> files = new ArrayList<>();

        if (!folder.exists()) {
            folder.mkdirs();
        }

        File[] fileList = folder.listFiles();
        if (fileList == null) {
            return files;
        }

        for (File file : fileList) {
            if (file.isDirectory()) {
                files.addAll(getFolderContentsRecursive(file, extension));
            } else if (extension == null || (extension != null && file.getName().toLowerCase().endsWith(extension))) {
                files.add(file);
            }
        }

        return files;
    }
}
