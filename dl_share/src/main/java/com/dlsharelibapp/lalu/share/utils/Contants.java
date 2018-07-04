package com.dlsharelibapp.lalu.share.utils;

import android.util.Base64;

/**
 * Created by sreelal on 7/3/18.
 */

public class Contants {
public static int PORT=52287;
public static String SENDER="dliteracy";
public static String SENDER_BASE="digital";

public static String HOTSPOT_NAME=SENDER+"-"+uniq();


   public static String uniq(){

       return Base64.encodeToString((SENDER_BASE).getBytes(),Base64.DEFAULT);
   }


}
