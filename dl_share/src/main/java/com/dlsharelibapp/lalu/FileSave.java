package com.dlsharelibapp.lalu;

import android.os.Environment;

import java.io.File;

/**
 * Created by sreelal on 29/12/17.
 */

public class FileSave {
    static File storageDir;

    public final static String CIRTIFICATE="Cirtificate";
    public final static String CONTENTS="Contents";
    public final static String ROOT_FOLDER="digital_literacy";

    public static File attachment() {
        try {
            storageDir = Environment.getExternalStoragePublicDirectory(ROOT_FOLDER);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return storageDir;
    }

    public static File contentFile() {
        if (!attachment().exists()) {
            attachment().mkdir();
        } else {

            File file = new File(attachment(), CONTENTS);
            if (!file.exists()) {
                file.mkdir();
            }
            return file;
        }
        return null;
    }

    public static File cirtificate() {
        if (!attachment().exists()) {
            attachment().mkdir();
        } else {

            File file = new File(attachment(), CIRTIFICATE);
            if (!file.exists()) {
                file.mkdir();
            }
            return file;
        }
        return null;
    }
}