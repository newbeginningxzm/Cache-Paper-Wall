package com.bupt.paragon.ImageLoader;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.FileDescriptor;

/**
 * Created by Paragon on 2016/3/25.
 */
public class ImageResizer {
    public static Bitmap decodeSampledBitmapFromResource(Resources res,int resId,int reqWidth,int reqHeight){
        BitmapFactory.Options options=new BitmapFactory.Options();
        options.inJustDecodeBounds=true;
        BitmapFactory.decodeResource(res,resId,options);
        options.inSampleSize=calculateInSampleSize(options,reqWidth,reqHeight);
        options.inJustDecodeBounds=false;
        return BitmapFactory.decodeResource(res,resId,options);
    }

    public static Bitmap decodeSampledBitmapFromFileDescriptor(FileDescriptor fd,int reqWidth,int reqHeight){
        BitmapFactory.Options options=new BitmapFactory.Options();
        options.inJustDecodeBounds=true;
        BitmapFactory.decodeFileDescriptor(fd,null,options);
        options.inSampleSize=calculateInSampleSize(options,reqWidth,reqHeight);
        options.inJustDecodeBounds=false;
        return BitmapFactory.decodeFileDescriptor(fd,null,options);
    }


    private static int calculateInSampleSize(BitmapFactory.Options options,int reqWidth,int reqHeight){
        int inSampleSize=1;
        int resWidth=options.outWidth;
        int resHeight=options.outHeight;
        while(resWidth/inSampleSize>resWidth&&resWidth/inSampleSize>reqHeight){
            inSampleSize*=2;
        }
        if(resWidth/inSampleSize==resWidth&&resWidth/inSampleSize==reqHeight)
            return inSampleSize;
        return inSampleSize*2;
    }

}
