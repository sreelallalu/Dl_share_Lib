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
package com.dlsharelibapp.lalu.share.sender;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.text.TextUtils;
import android.util.Log;

import com.dlsharelibapp.lalu.R;
import com.dlsharelibapp.lalu.share.receiver.FileListingModel;
import com.google.gson.Gson;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;


/**
 * A Tiny Http Server extended from {@link NanoHTTPD}. Once started, serves selected files from {@link ShareActivity} on an assigned PORT.
 * <p>
 * Created by Sri on 18/12/16.
 */

class ShareServer extends NanoHTTPD {

    private static final String TAG = "ShareServer";

    private static final String MIME_JSON = "application/json";
    private static final String MIME_FORCE_DOWNLOAD = "application/force-p_p_download";
    private static final String MIME_PNG = "image/png";

    private String[] m_filesTobeHosted;
    private FileListener m_clientsFileTransferListener;
    private Context m_context;

    public ShareServer(String host_name, int port) {
        super(host_name, port);
    }

    public ShareServer(Context context, FileListener statusListener, String[] filesToBeHosted) {
        this(null, 0);
        m_context = context;
        m_clientsFileTransferListener = statusListener;
        m_filesTobeHosted = filesToBeHosted;
    }

    public ShareServer(Context context, FileListener statusListener, String[] filesToBeHosted, int port) {
        this(null, port);
        m_context = context;
        m_clientsFileTransferListener = statusListener;
        m_filesTobeHosted = filesToBeHosted;
    }

    @Override
    public void stop() {
        super.stop();
    }

    @Override
    public Response serve(IHTTPSession session) {
        Response res = null;
        try {
            String url = session.getUri();
            Log.d(TAG, "request uri: " + url);
            if (TextUtils.isEmpty(url) || url.equals("/") || url.contains("/open"))
                res = createHtmlResponse();
            else if (url.equals("/status"))
                res = new Response(Response.Status.OK, MIME_PLAINTEXT, "Available");
            else if (url.equals("/apk"))
                res = createApkResponse(session.getHeaders().get("http-client-ip"));
            else if (url.equals("/logo") || url.equals("/favicon.ico"))
                res = createLogoResponse();
            else if (url.equals("/files"))
                res = createFilePathsResponse();
            else if (url.contains("/file/")) {
                int index = Integer.parseInt(url.replace("/file/", ""));
                if (index != -1)
                    res = createFileResponse(m_filesTobeHosted[index], session.getHeaders().get("http-client-ip"));
            }
        } catch (Exception ioe) {
            ioe.printStackTrace();
            res = createErrorResponse(Response.Status.FORBIDDEN, ioe.getMessage());
        } finally {
            if (null == res)
                res = createForbiddenResponse();
        }
        res.addHeader("Accept-Ranges", "bytes");
        res.addHeader("Access-Control-Allow-Origin", "*");
        res.addHeader("Access-Control-Allow-Methods", "POST, GET, OPTIONS");
        return res;
    }
    // increasing targetSdkVersion version might impact behaviour of this library
    // if targetSdkVersion >= 23
    //      1. ShareActivity has to check for System Write permissions to proceed
    //      2. Get Wifi Scan results method needs GPS to be ON and COARSE location permission
    //      library checks the targetSdkVersion to take care of above scenarios
    // if targetSdkVersion > 20
    //      If an application's target SDK version is LOLLIPOP or newer, network communication may not use Wi-Fi even if Wi-Fi is connected;
    //  this might impact when Receiver connectivity to SHAREthem hotspot, library checks for this scenario and prompts user to disable data
    //      For more info: https://developer.android.com/reference/android/net/wifi/WifiManager.html#enableNetwork(int, boolean)

    /**
     * Creates an Error {@link Response} with
     *
     * @param status  error Status like <code>Response.Status.FORBIDDEN</code>
     * @param message error message
     * @return {@link Response}
     */
    private Response createErrorResponse(Response.Status status, String message) {
        Log.e(TAG, "error while creating response: " + message);
        return new Response(status, MIME_PLAINTEXT, message);
    }

    private Response createForbiddenResponse() {
        return createErrorResponse(Response.Status.FORBIDDEN,
                "FORBIDDEN: Reading file failed.");
    }

    /**
     * Creates a success {@link Response} with Shared Files URLS data in @{@link com.google.gson.JsonArray} format
     *
     * @return {@link Response}
     */
    private Response createFilePathsResponse() {
       try {
           List<FileListingModel> listattach = new ArrayList<>();
           if (m_filesTobeHosted.length != 0) {
               for (int i = 0; i < m_filesTobeHosted.length; i++) {
                   File file = new File(m_filesTobeHosted[i]);
                   if (file.exists()) {
                       long filesize = file.length();
                       String filename = file.getName();
                       String extension = filename.substring(filename.lastIndexOf("."));
                       extension=removeLeadingChar(extension,'.');
                       listattach.add(new FileListingModel(filename, extension, filesize));

                   }

               }
           }
           Gson gson=new Gson();
           return new Response(Response.Status.OK, MIME_JSON, gson.toJson(listattach));
       }catch (Exception e){
           e.printStackTrace();
           return  null;
         }


        //  return new Response(Response.Status.OK, MIME_JSON,"{\"polappan\",\"kidilam\"}");
    }
    public static String removeLeadingChar(String str, char ch) {
        int idx = 0;
        while ((idx < str.length()) && (str.charAt(idx) == ch))
            idx++;
        return str.substring(idx);
    }

    /**
     * Creates a success <code>Response</code> with binary data of file in {@link ShareServer#m_filesTobeHosted} with provided index.
     *
     * @param clientIp Receiver IP to which Response is intended for
     * @param fileUrl  url of file among Shared files array
     * @return {@link Response}
     * @throws IOException
     */
    private Response createFileResponse(String fileUrl, String clientIp) throws IOException {
        final File file = new File(fileUrl);
        Log.e(TAG, "resolve info found, file location: " + file.getAbsolutePath() + ", file length: " + file.length() + ", file name: " + file.getName());
        Response res = new Response(Response.Status.OK, MIME_FORCE_DOWNLOAD, clientIp, file, m_clientsFileTransferListener);
        res.addHeader("Content-Length", "" + file.length());
        res.addHeader("Content-Disposition", "attachment; filename='" + file.getName() + "'");
        return res;
    }

    private Response createHtmlResponse() {
        String answer = "";
        try {
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(m_context.getAssets().open("web_talk.html")));
            String line = "";
            while ((line = reader.readLine()) != null) {
                answer += line;
            }
        } catch (IOException ioe) {
            Log.e("NanoHTTPD", ioe.toString());
        }
        return new Response(answer);
    }

    private Response createLogoResponse() {
        try {
            Bitmap bitmap = BitmapFactory.decodeResource(m_context.getResources(), R.drawable.p_p_download);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.PNG, 0 /*ignored for PNG*/, bos);
            byte[] bitmapdata = bos.toByteArray();
            ByteArrayInputStream bs = new ByteArrayInputStream(bitmapdata);
            Response res = new Response(Response.Status.OK, MIME_PNG, bs);
            res.addHeader("Accept-Ranges", "bytes");
            return res;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return createForbiddenResponse();
    }

    private Response createApkResponse(String ip) throws IOException {
        Response res = null;
        final Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        mainIntent.setPackage(m_context.getPackageName());
        ResolveInfo info = m_context.getPackageManager().resolveActivity(mainIntent, 0);
        if (null != info) {
            res = createFileResponse(info.activityInfo.applicationInfo.publicSourceDir, ip);
        }
        return res;
    }

}
