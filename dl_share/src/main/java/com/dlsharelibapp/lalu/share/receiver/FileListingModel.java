package com.dlsharelibapp.lalu.share.receiver;

public class FileListingModel {

    String filename;
    String extension;
    long filesize;


    public FileListingModel(String filename, String extension, long filesize) {
        this.filename = filename;
        this.extension = extension;
        this.filesize = filesize;
    }

    public String getFilename() {
        return filename;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public String getExtension() {
        return extension;
    }

    public void setExtension(String extension) {
        this.extension = extension;
    }

    public long getFilesize() {
        return filesize;
    }

    public void setFilesize(long filesize) {
        this.filesize = filesize;
    }


}
