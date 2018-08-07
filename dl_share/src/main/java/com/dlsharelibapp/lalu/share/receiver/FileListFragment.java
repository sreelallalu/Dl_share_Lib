/*
 * Copyright 2017 Srihari Yachamaneni
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.dlsharelibapp.lalu.share.receiver;

import android.app.DownloadManager;
import android.app.NotificationManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.ResultReceiver;
import android.support.annotation.Nullable;
import android.support.v4.content.FileProvider;
import android.support.v7.app.NotificationCompat;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.dlsharelibapp.lalu.FileSave;
import com.dlsharelibapp.lalu.R;
import com.dlsharelibapp.lalu.share.utils.DividerItemDecoration;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.ref.WeakReference;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import static android.content.Context.DOWNLOAD_SERVICE;

/**
 * Lists all files available to p_p_download by making network calls using {@link ContactSenderAPITask}
 * <p>
 * Functionalities include:
 * <ul>
 * <li>Adds file downloads to {@link DownloadManager}'s Queue</li>
 * * <li>Checks Sender API availability and throws error after certain retry limit</li>
 * </ul>
 * <p>
 * Created by Sri on 21/12/16.
 */

public class FileListFragment extends android.support.v4.app.Fragment {
    private static final String TAG = "FileListFragment";

    public static final String PATH_FILES = "http://%s:%s/files";
    public static final String PATH_STATUS = "http://%s:%s/status";
    public static final String PATH_FILE_DOWNLOAD = "http://%s:%s/file/%s";

    private String mSenderIp = null, mSenderSSID;
    private ContactSenderAPITask mUrlsTask;
    private ContactSenderAPITask mStatusCheckTask;

    private String mPort, mSenderName;

    static final int CHECK_SENDER_STATUS = 100;
    static final int SENDER_DATA_FETCH = 101;

    RecyclerView mFilesListing;
    ProgressBar mLoading;
    TextView mEmptyListText;

    private SenderFilesListingAdapter mFilesAdapter;

    private UiUpdateHandler uiUpdateHandler;

    private static final int SENDER_DATA_FETCH_RETRY_LIMIT = 3;
    private int senderDownloadsFetchRetry = SENDER_DATA_FETCH_RETRY_LIMIT, senderStatusCheckRetryLimit = SENDER_DATA_FETCH_RETRY_LIMIT;

    private NotificationCompat.Builder mBuilder;
    private NotificationManager mNotifyManager;

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.p_p_receiver_file_list, null);
        mFilesListing = (RecyclerView) v.findViewById(R.id.files_list);
        mLoading = (ProgressBar) v.findViewById(R.id.loading);
        mEmptyListText = (TextView) v.findViewById(R.id.empty_listing_text);
        mEmptyListText.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mEmptyListText.setVisibility(View.GONE);
                mLoading.setVisibility(View.VISIBLE);
                fetchSenderFiles();
            }
        });
        mFilesListing.setLayoutManager(new LinearLayoutManager(getActivity()));
        mFilesListing.addItemDecoration(new DividerItemDecoration(getResources().getDrawable(R.drawable.p_p_list_driver)));
        mFilesAdapter = new SenderFilesListingAdapter(new ArrayList<FileListingModel>());
        mFilesListing.setAdapter(mFilesAdapter);
        uiUpdateHandler = new UiUpdateHandler(this);
        startNotification();
        return v;
    }

    public void startNotification() {
        try {


            mNotifyManager = (NotificationManager) getActivity()
                    .getSystemService(Context.NOTIFICATION_SERVICE);
            mBuilder = new NotificationCompat.Builder(getActivity());
            mBuilder.setContentTitle("Picture Download")
                    .setContentText("Download in progress")
                    .setSmallIcon(R.drawable.p_p_ripple_img)
                    .setPriority(NotificationCompat.PRIORITY_LOW);
            mBuilder.setAutoCancel(true);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static FileListFragment getInstance(String senderIp, String ssid, String senderName, String port) {
        FileListFragment fragment = new FileListFragment();
        Bundle data = new Bundle();
        data.putString("senderIp", senderIp);
        data.putString("ssid", ssid);
        data.putString("name", senderName);
        data.putString("port", port);
        Log.e("Bundle", data.toString());
        fragment.setArguments(data);
        return fragment;
    }

    @Override
    public void onAttach(Context activity) {
        super.onAttach(activity);
        if (null != getArguments()) {
            mSenderIp = getArguments().getString("senderIp");
            mSenderSSID = getArguments().getString("ssid");
            mPort = getArguments().getString("port");
            mSenderName = getArguments().getString("name");
            Log.e(TAG, "sender ip: " + mSenderIp + " " + mSenderName + " " + mPort);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        fetchSenderFiles();
        checkSenderAPIAvailablity();
    }

    private void fetchSenderFiles() {
        mLoading.setVisibility(View.VISIBLE);
        if (null != mUrlsTask)
            mUrlsTask.cancel(true);
        mUrlsTask = new ContactSenderAPITask(SENDER_DATA_FETCH);


        mUrlsTask.execute(String.format(PATH_FILES, mSenderIp, mPort));
    }

    private void checkSenderAPIAvailablity() {
        if (null != mStatusCheckTask)
            mStatusCheckTask.cancel(true);
        mStatusCheckTask = new ContactSenderAPITask(CHECK_SENDER_STATUS);
        mStatusCheckTask.execute(String.format(PATH_STATUS, mSenderIp, mPort));
    }

    @Override
    public void onPause() {
        super.onPause();
        if (null != mUrlsTask)
            mUrlsTask.cancel(true);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (null != uiUpdateHandler)
            uiUpdateHandler.removeCallbacksAndMessages(null);
        if (null != mStatusCheckTask)
            mStatusCheckTask.cancel(true);
    }

    public String getSenderSSID() {
        return mSenderSSID;
    }

    public String getSenderIp() {
        return mSenderIp;
    }

    private void loadListing(String contentAsString) {

        Log.e("files_list",contentAsString==null?"nullfile":contentAsString);
        Type collectionType = new TypeToken<List<FileListingModel>>() {
        }.getType();
        List<FileListingModel> files = new Gson().fromJson(contentAsString, collectionType);


        mLoading.setVisibility(View.GONE);

        if (null == files || files.size() == 0) {
            mEmptyListText.setText("No Downloads found.\n Tap to Retry");
            mEmptyListText.setVisibility(View.VISIBLE);
        } else {
            mEmptyListText.setVisibility(View.GONE);
           mFilesAdapter.updateData(files);

        }


    }


    private void onDataFetchError() {
        mLoading.setVisibility(View.GONE);
        mEmptyListText.setVisibility(View.VISIBLE);
        mEmptyListText.setText("Error occurred while fetching data.\n Tap to Retry");
    }

    private long postDownloadRequestToDM(Uri uri, String fileName) {


        DownloadManager downloadManager = (DownloadManager) getActivity().getSystemService(DOWNLOAD_SERVICE);
        DownloadManager.Request request = new DownloadManager.Request(uri);

        //Setting title of request
        request.setTitle(fileName);

        //Setting description of request
        request.setDescription("ShareThem");
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);

        //Set the local destination for the downloaded file to a path
        //within the application's external files directory
        request.setDestinationInExternalFilesDir(getActivity(),
                Environment.DIRECTORY_DOWNLOADS, fileName);

        //Enqueue p_p_download and save into referenceId
        return downloadManager.enqueue(request);
    }

    private class SenderFilesListingAdapter extends RecyclerView.Adapter<SenderFilesListItemHolder> {

       List<FileListingModel> file_list;


        SenderFilesListingAdapter(List<FileListingModel> objects) {

            file_list=objects;
        }

        void updateData(List<FileListingModel> objects) {
            file_list=objects;
            notifyDataSetChanged();
        }

        @Override
        public SenderFilesListItemHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View itemView = LayoutInflater.from(parent.getContext()).
                    inflate(R.layout.p_p_receiver_one_item, parent, false);
            return new SenderFilesListItemHolder(itemView);
        }


        @Override
        public int getItemCount() {
            return file_list.size();
        }

        @Override
        public void onBindViewHolder(final SenderFilesListItemHolder holder, final int position) {
            FileListingModel senderFile = file_list.get(position);
            holder.itemView.setTag(senderFile.getFilename());
            holder.title.setText(senderFile.getFilename());
            File file = new File(FileSave.contentFile(), senderFile.getFilename());
            long temp_filesize=0;
             if(file.exists())
             {
                 temp_filesize=file.length();
             }

            if (file.exists() &&  senderFile.getFilesize()== temp_filesize) {
                holder.download.setImageDrawable(holder.download.getResources().getDrawable(R.drawable.p_p_ic_open_file));
                holder.download.setOnClickListener(new FileExit(file, senderFile.getExtension()));

            } else {
                holder.download.setImageDrawable(holder.download.getResources().getDrawable(R.drawable.p_p_download));
                Uri uri = Uri.parse(String.format(PATH_FILE_DOWNLOAD, mSenderIp, mPort, position));
                holder.download.setOnClickListener(new DownLoadFile(uri, senderFile.getFilename(), position, holder));
            }



        }


    }

    private class DownLoadFile implements View.OnClickListener {
        Uri uri;
        String filename;
        int postion;
        SenderFilesListItemHolder holder;

        public DownLoadFile(Uri uri, String fileName, int position, SenderFilesListItemHolder holder) {
            this.filename = fileName;
            this.postion = position;
            this.uri = uri;
            this.holder = holder;


        }

        @Override
        public void onClick(View v) {
            Intent intent = new Intent(getActivity(), DownloadService.class);
            intent.putExtra("url", "" + uri);
            intent.putExtra("filename", filename);
            intent.putExtra("position", postion);
            intent.putExtra("receiver", new DownloadReceiver(new Handler(), holder));
            getActivity().startService(intent);
        }
    }

    private class FileExit implements View.OnClickListener {
        File file;
        String filetype;

        public FileExit(File file, String filetype) {

            this.file = file;
            this.filetype = filetype;
            Log.e("fileextemsion",filetype);

        }

        @Override
        public void onClick(View v) {
            try {

                if (file.exists()) {
                    Uri path;
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
                        path = Uri.fromFile(file);
                    } else {
                        path = FileProvider.getUriForFile(v.getContext(), v.getContext().getApplicationContext().getPackageName() + ".provider", file);
                    }
                    Intent intent = new Intent(Intent.ACTION_VIEW);
                    //intent.setDataAndType(path, "application/pdf");
                    intent.setDataAndType(path, getIntentDataAndType(filetype));
                    intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
                    try {
                        v.getContext().startActivity(intent);
                    } catch (ActivityNotFoundException e1) {
                        Toast.makeText(v.getContext(),
                                "No Application Available to View File",
                                Toast.LENGTH_SHORT).show();
                    }
                }

            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(v.getContext(), "Invalid File", Toast.LENGTH_SHORT).show();

            }

        }
    }

    private String getIntentDataAndType(String filetype) {

        String mimeType = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(filetype);
        mimeType = (mimeType == null) ? "*/*" : mimeType;
        return mimeType;
    }

    private class DownloadReceiver extends ResultReceiver {
        ProgressBar progressBar;
        SenderFilesListItemHolder holder;

        public DownloadReceiver(Handler handler, SenderFilesListItemHolder holder) {
            super(handler);

         /*   mBuilder.setProgress(100, 0, false);
            mNotifyManager.notify(1, mBuilder.build());*/
            this.progressBar = holder.progressBar;
            this.holder = holder;
            this.progressBar.setScrollBarStyle(ProgressBar.SCROLLBARS_OUTSIDE_INSET);
            this.progressBar.setVisibility(View.VISIBLE);
            this.progressBar.setProgress(0);
            this.progressBar.setMax(100);
        }

        @Override
        protected void onReceiveResult(int resultCode, Bundle resultData) {
            super.onReceiveResult(resultCode, resultData);
            if (resultCode == DownloadService.UPDATE_PROGRESS) {
                int progress = resultData.getInt("progress");

                progressBar.setProgress(progress);
              /*  mBuilder.setProgress(100, progress, false);
                mNotifyManager.notify(1, mBuilder.build());*/

                //  notification(progress,1);
                if (progress == 100) {
                    final String filepath = resultData.getString("filepath");

                    progressBar.setVisibility(View.GONE);
                    holder.bgLayout.setBackgroundColor(getActivity().getResources().getColor(R.color.white));
                    mFilesAdapter.notifyDataSetChanged();
                }
            }
        }
    }


    public void notification(int progress, int notificationId) {

        mBuilder.setProgress(100, progress, false);
        mNotifyManager.notify(1, mBuilder.build());

        // mBuilder.setContentText("Download complete");
        //  .setProgress(0, 0, false);

    }

    static class SenderFilesListItemHolder extends RecyclerView.ViewHolder {
        TextView title;
        ImageView download;
        ProgressBar progressBar;
        LinearLayout bgLayout;

        SenderFilesListItemHolder(View itemView) {
            super(itemView);
            title = (TextView) itemView.findViewById(R.id.downloadname);
            download = (ImageView) itemView.findViewById(R.id.downloadBtn);
            progressBar = (ProgressBar) itemView.findViewById(R.id.progressBar);
            bgLayout = (LinearLayout) itemView.findViewById(R.id.down_cardview);
        }
    }


    /**
     * Performs network calls to fetch data/status from Sender.
     * Retries on error for times bases on values of {@link FileListFragment#senderDownloadsFetchRetry}
     */
    private class ContactSenderAPITask extends AsyncTask<String, Void, String> {

        int mode;
        boolean error;

        ContactSenderAPITask(int mode) {
            this.mode = mode;
        }

        @Override
        protected String doInBackground(String... urls) {
            error = false;
            try {
                return downloadDataFromSender(urls[0]);
            } catch (Exception e) {
                e.printStackTrace();
                error = true;
                Log.e(TAG, "Exception: " + e.getMessage());
                return null;
            }
        }

        // onPostExecute displays the results of the AsyncTask.
        @Override
        protected void onPostExecute(String result) {

            switch (mode) {
                case SENDER_DATA_FETCH:
                    if (error) {
                        if (senderDownloadsFetchRetry >= 0) {
                            --senderDownloadsFetchRetry;
                            if (getView() == null || getActivity() == null || null == uiUpdateHandler)
                                return;
                            uiUpdateHandler.removeMessages(SENDER_DATA_FETCH);
                            uiUpdateHandler.sendMessageDelayed(uiUpdateHandler.obtainMessage(mode), 800);
                            return;
                        } else senderDownloadsFetchRetry = SENDER_DATA_FETCH_RETRY_LIMIT;
                        if (null != getView())
                            onDataFetchError();
                    } else if (null != getView())
                        loadListing(result);

                    else Log.e(TAG, "fragment may have been removed, File fetch");
                    break;
                case CHECK_SENDER_STATUS:
                    if (error) {
                        if (senderStatusCheckRetryLimit > 1) {
                            --senderStatusCheckRetryLimit;
                            uiUpdateHandler.removeMessages(CHECK_SENDER_STATUS);
                            uiUpdateHandler.sendMessageDelayed(uiUpdateHandler.obtainMessage(CHECK_SENDER_STATUS), 800);
                        } else if (getActivity() instanceof ReceiverActivity) {
                            senderStatusCheckRetryLimit = SENDER_DATA_FETCH_RETRY_LIMIT;
                            ((ReceiverActivity) getActivity()).resetSenderSearch();
                            Toast.makeText(getActivity(), getString(R.string.p2p_receiver_error_sender_disconnected), Toast.LENGTH_SHORT).show();
                        } else{
                            Log.e(TAG, "Activity is not instance of ReceiverActivity");
                        }

                    } else if (null != getView()) {
                        senderStatusCheckRetryLimit = SENDER_DATA_FETCH_RETRY_LIMIT;
                        uiUpdateHandler.removeMessages(CHECK_SENDER_STATUS);
                        uiUpdateHandler.sendMessageDelayed(uiUpdateHandler.obtainMessage(CHECK_SENDER_STATUS), 1000);
                    } else
                        Log.e(TAG, "fragment may have been removed: Sender api check");
                    break;
            }

        }

        private String downloadDataFromSender(String apiUrl) throws IOException {


            InputStream is = null;
            HttpURLConnection conn = null;
            try {
                URL url = new URL(apiUrl);
                conn = (HttpURLConnection) url.openConnection();
                conn.setReadTimeout(10000 /* milliseconds */);
                conn.setConnectTimeout(15000 /* milliseconds */);
                conn.setRequestMethod("GET");
                conn.setDoInput(true);
                // Starts the query
                conn.connect();

//                int response =

                conn.getResponseCode();

//                Log.d(TAG, "The response is: " + response);
                is = conn.getInputStream();
                // Convert the InputStream into a string
                return readIt(is);
            } catch (SocketTimeoutException e) {
                Log.e("timeout", e.getMessage());
                return "";
            } catch (SocketException e) {
                Log.e("socket ex", e.getMessage());
                return "";

            } finally {
                if (is != null) {
                    is.close();
                }
            }
        }

        private String readIt(InputStream stream) throws IOException {
            Writer writer = new StringWriter();

            char[] buffer = new char[1024];
            try {
                Reader reader = new BufferedReader(
                        new InputStreamReader(stream, "UTF-8"));
                int n;
                while ((n = reader.read(buffer)) != -1) {
                    writer.write(buffer, 0, n);
                }
            } finally {
                stream.close();
            }
            return writer.toString();
        }
    }

    private static class UiUpdateHandler extends Handler {
        WeakReference<FileListFragment> mFragment;

        UiUpdateHandler(FileListFragment fragment) {
            mFragment = new WeakReference<>(fragment);
        }

        @Override
        public void handleMessage(Message msg) {
            FileListFragment fragment = mFragment.get();
            if (null == mFragment)
                return;
            switch (msg.what) {
                case CHECK_SENDER_STATUS:
                    fragment.checkSenderAPIAvailablity();
                    break;
                case SENDER_DATA_FETCH:
                    fragment.fetchSenderFiles();
                    break;
            }
        }
    }



    private class FileSizeCheckingEach  {

        List<String> objectList;

        List<FileListingModel> filelist;
        List<Integer> sizefile_list;

        public FileSizeCheckingEach(ArrayList<String> files) throws InterruptedException {

            this.objectList = files;
            this.filelist = new ArrayList<>();
            sizefile_list=new ArrayList<>();
            for (int i = 0; i < objectList.size(); i++) {

                String senderFile = objectList.get(i);
                String fileName = senderFile.substring(senderFile.lastIndexOf('/') + 1, senderFile.length());
                String extension = fileName.substring(fileName.lastIndexOf("."));
                 Uri uri = Uri.parse(String.format(PATH_FILE_DOWNLOAD, mSenderIp, mPort, objectList.indexOf(senderFile)));
               // filelist.add(new FileListingModel(fileName,extension,uri));

            }






        }
        public int test(final String urlvalue) throws InterruptedException {

            final CountDownLatch latch = new CountDownLatch(1);
            final int[] value = new int[1];
            Thread uiThread = new HandlerThread("UIHandler"){
                @Override
                public void run(){

                    URL url= null;
                    try {
                        url = new URL(urlvalue);
                    } catch (MalformedURLException e) {
                        e.printStackTrace();
                    }
                    URLConnection conection = null;
                    try {
                        conection = url.openConnection();
                        int filesize = conection.getContentLength();
                        value[0] = filesize;
                        conection.connect();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }


                    latch.countDown(); // Release await() in the test thread.
                }
            };
            uiThread.start();
            latch.await(); // Wait for countDown() in the UI thread. Or could uiThread.join();
         return value[0];
        }


        protected Integer doInBackground(List<FileListFragment>... integers) {
            URL url = null;
            try{



                //Uri uri=integers;

                URLConnection conection = url.openConnection();
                conection.connect();
                int filesize = conection.getContentLength();
                return filesize;

            }catch (Exception e){e.printStackTrace();}

            return null;
        }



        protected void onPostExecute(Integer i) {

            if(i!=null)
            {
                sizefile_list.add(i);
                /*if(filelist.size()>sizefile_list.size())
                {
                    if(filelist.get(sizefile_list.size()).getFileuri()!=null)
                    {
                        execute(filelist.get(sizefile_list.size()).getFileuri());
                   }
                }else{

                    for (int j = 0; j <filelist.size() ; j++) {

                        FileListingModel model=filelist.get(i);
                         if(sizefile_list.size()>0&&sizefile_list.get(i)!=null)
                         {
                             model.setFilesize(sizefile_list.get(i));
                             Log.e("data","data_"+sizefile_list.get(i));
                         }

                    }
                    mFilesAdapter.updateData(filelist);
*/
                }


            }

           /* final String senderFile = mObjects.get(position);
            holder.itemView.setTag(senderFile);
            final String fileName = senderFile.substring(senderFile.lastIndexOf('/') + 1, senderFile.length());
            final String extension = fileName.substring(fileName.lastIndexOf("."));
            holder.title.setText(fileName);*/

        }
    }

