package com.dlsharelibapp.lalu.share.receiver;

import android.app.IntentService;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.ResultReceiver;
import android.support.v4.app.NotificationCompat;

import com.dlsharelibapp.lalu.FileSave;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.Random;


/**
 * Created by sreelal on 5/3/18.
 */

public class DownloadService extends IntentService {
    public static final int UPDATE_PROGRESS = 8344;
    private int notificationId;

    private NotificationCompat.Builder mBuilder;
    private NotificationManager mNotifyManager;


    public DownloadService() {
        super("DownloadService");
        Random rand = new Random();

        notificationId = rand.nextInt(150) + 1;
    }
    public  void startNotification()
    {
       try {



           mNotifyManager = (NotificationManager) this
                   .getSystemService(Context.NOTIFICATION_SERVICE);
           mBuilder = new NotificationCompat.Builder(this);
           mBuilder.setContentTitle("Picture Download")
                   .setContentText("Download in progress")
                   .setSmallIcon(android.R.mipmap.sym_def_app_icon)
                   .setPriority(NotificationCompat.PRIORITY_LOW);
           mBuilder.setAutoCancel(true);
       }catch (Exception e){e.printStackTrace();}}
    public void notification(int progress,int notificationId) {

       try {
           mBuilder.setProgress(100, progress, false);
           mNotifyManager.notify(notificationId, mBuilder.build());
       }catch (Exception e){e.printStackTrace();}



    }
    @Override
    protected void onHandleIntent(Intent intent) {
        String urlToDownload = intent.getStringExtra("url");
        String namefile = intent.getStringExtra("filename");
        int position = intent.getIntExtra("position",0);

        startNotification();
        File temFile=null;

// Issue the initial notification with zero progress




        ResultReceiver receiver = (ResultReceiver) intent.getParcelableExtra("receiver");
        try {
            URL url = new URL(urlToDownload);
            URLConnection connection = url.openConnection();
            connection.connect();
            int fileLength = connection.getContentLength();
            // TODO: 6/6/18 1 change
            File attachamnet= FileSave.contentFile();
            if(!attachamnet.exists())
            {
                attachamnet.mkdir();
            }
             temFile=new File(attachamnet,namefile);
            InputStream input = new BufferedInputStream(connection.getInputStream());
            OutputStream output = new FileOutputStream(temFile);

            byte data[] = new byte[1024];
            long total = 0;
            int count;

            while ((count = input.read(data)) != -1) {
                total += count;
                // publishing the progress....
                Bundle resultData = new Bundle();
                resultData.putInt("progress" ,(int) (total * 100 / fileLength));
                receiver.send(UPDATE_PROGRESS, resultData);
                output.write(data, 0, count);

            //   notification((int) (total * 100 / fileLength),position);
            }

            output.flush();
            output.close();
            input.close();
        } catch (IOException e) {
            e.printStackTrace();
        }

        Bundle resultData = new Bundle();
        resultData.putInt("progress" ,100);
        resultData.putString("filepath" ,temFile+"");
        receiver.send(UPDATE_PROGRESS, resultData);
    }



}