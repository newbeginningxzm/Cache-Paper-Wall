package com.bupt.paragon.ImageLoader;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.Environment;
import android.os.Looper;
import android.os.Message;
import android.os.StatFs;
import android.util.Log;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import libcore.io.DiskLruCache;

import com.bupt.paragon.MyUtils;

/**
 * Created by Paragon on 2016/3/25.
 */
public class ImageLoader {
    private Context mContext;
    private LruCache<String,Bitmap> mMemoryCache;
    private DiskLruCache mDiskLruCache;
    private boolean mIsDiskLruCacheCreated=false;
    private ImageResizer mImageResizer=new ImageResizer();

    private static String TAG = "com.bupt.paragon.ImageLoader";
    private static final int SET_IMAGE=1;
    private static long DISKCACHESIZE=1024*1024*50;
    private static int DISKCACHEINDXE=0;
    private static int IO_BUFFER_SIZE=1024*8;

    private static final int CPU_COUNT=Runtime.getRuntime().availableProcessors();
    private static final int CORE_POOL_SIZE=CPU_COUNT+1;
    private static final int MAX_POOL_SIZE=2*CPU_COUNT+1;
    private static final long KEEP_ALIVE=60L;

    private android.os.Handler mMainHandler =new android.os.Handler(Looper.getMainLooper()){
        @Override
        public void handleMessage(Message msg) {
            switch(msg.what){
                case SET_IMAGE:
                    LoadResult result= (LoadResult) msg.obj;
                    ImageView img=result.img;
                    String url= (String) img.getTag();
                    if(url.equals(result.url)){
                        img.setImageBitmap(result.bitmap);
                    }else{
                        Log.w(TAG,"The Bitmap Loaded is Not Right One!");
                    }
            }
        }
    };

    private static final ThreadFactory sThreadFactory = new ThreadFactory() {
        private AtomicInteger mCount = new AtomicInteger(0);
        @Override
        public Thread newThread(Runnable r) {
            return new java.lang.Thread(r,"ImageLoader #"+mCount.getAndIncrement());
        }
    };

    public static ThreadPoolExecutor THREAD_POOL_EXCUTOR = new ThreadPoolExecutor(
            CORE_POOL_SIZE,
            MAX_POOL_SIZE,
            KEEP_ALIVE,
            TimeUnit.SECONDS,
            new LinkedBlockingDeque<Runnable>(),
            sThreadFactory);

    private ImageLoader(Context context,boolean ClearWhenUpdate){
        mContext=context;
        int maxMemory= (int) (Runtime.getRuntime().maxMemory()/1024);
        int cacheSize=maxMemory/8;
        Log.e(TAG,"Memory Cache Size is:"+cacheSize);
        mMemoryCache = new LruCache<String,Bitmap>(cacheSize){
            @Override
            protected int sizeOf(String key, Bitmap value) {
                return value.getByteCount()/1024;
            }
        };
        File diskCacheDir=getCacheDir("bitmap");
        if(!diskCacheDir.exists()){
            diskCacheDir.mkdirs();
        }
        if(DISKCACHESIZE<=getUsableSpace(diskCacheDir)){
            try {
                if(ClearWhenUpdate){
                    mDiskLruCache=DiskLruCache.open(diskCacheDir,getVersion(context),1,DISKCACHESIZE);
                    mIsDiskLruCacheCreated=true;
                }
                mDiskLruCache=DiskLruCache.open(diskCacheDir,1,1,DISKCACHESIZE);
                mIsDiskLruCacheCreated=true;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static ImageLoader build(Context context,boolean clearWhenUpdate){
        return new ImageLoader(context,clearWhenUpdate);
    }

    public Bitmap loadBitmap(String url,int reqWidth,int reqHeight){
        if(MyUtils.isEmpty(url))
            return null;
        Bitmap bitmap=loadBitmapFromMemoryCache(url);
        if(bitmap!=null){
            Log.d(TAG,"Load Bitmap From MemoryCache Url:"+url);
            return bitmap;
        }
        Log.d(TAG,"Load Bitmap From MemoryCache Failed! Url:"+url);
        bitmap=loadBitmapFromDiskCache(url,reqWidth,reqHeight);
        if(bitmap!=null){
            Log.d(TAG,"Load Bitmap From DiskCache size:"+bitmap.getRowBytes()+"b");
            return bitmap;
        }
        try {
            bitmap=loadBitmapFromHttp(url,reqWidth,reqHeight);
            Log.d(TAG,"Load Bitmap From Http Url:"+url);
        } catch (IOException e) {
            e.printStackTrace();
            Log.d(TAG, "Load Bitmap From Http Failed!Url:" + url);
        }
        if(bitmap==null&&!mIsDiskLruCacheCreated){
            loadBitmapFromUrl(url,reqWidth,reqHeight);
            Log.w(TAG, "DiskLruCache Has Not Been Created!");
        }
        return bitmap;
    }

    public void bindMap(final String url, final int reqWidth, final int reqHeight, final ImageView img){
        img.setTag(url);
        Bitmap bitmap=loadBitmapFromMemoryCache(url);
        if(bitmap!=null){
            img.setImageBitmap(bitmap);
            return;
        }

        Runnable loadBitmapTask = new Runnable() {
            @Override
            public void run() {
                Bitmap bitmap=loadBitmap(url,reqWidth,reqHeight);
                if(bitmap!=null){
                    LoadResult result=new LoadResult(bitmap,img,url);
                    mMainHandler.obtainMessage(SET_IMAGE,result).sendToTarget();
                }
            }
        };
        THREAD_POOL_EXCUTOR.execute(loadBitmapTask);
    }

    private void addBitmapToMemoryCache(String key,Bitmap bitmap){
        if(mMemoryCache.get(key)==null){
            mMemoryCache.put(key,bitmap);
            Log.d(TAG,"Memory Cache Used："+mMemoryCache.size()+"b");
        }


    }

    private Bitmap getBitmapFromMemoryCache(String key){
        return mMemoryCache.get(key);
    }

    private Bitmap loadBitmapFromMemoryCache(String url){
        final String key=hashKeyFromUrl(url);
//        Bitmap bitmap=getBitmapFromMemoryCache(key);
//        return bitmap;
        return mMemoryCache.get(key);
    }

    private Bitmap loadBitmapFromDiskCache(String url,int reqWidth,int reqHeight){
        if(Looper.getMainLooper()==Looper.myLooper()){
            Log.w(TAG,"Load Bitmap In UI Thread is not Recommanded!");
        }
        if(mDiskLruCache==null){
            return null;
        }
        Bitmap bitmap=null;
        String key=hashKeyFromUrl(url);
        try {
            DiskLruCache.Snapshot snapshot=mDiskLruCache.get(key);
            if(snapshot!=null){
                FileInputStream in= (FileInputStream) snapshot.getInputStream(DISKCACHEINDXE);
                bitmap = mImageResizer.decodeSampledBitmapFromFileDescriptor(in.getFD(),reqWidth,reqHeight);
                if(bitmap!=null)
                    addBitmapToMemoryCache(key,bitmap);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return bitmap;
    }

    private Bitmap loadBitmapFromHttp(String url,int reqWidth,int reqHeight) throws IOException {
        if(Looper.getMainLooper()==Looper.myLooper()){
            throw new RuntimeException("Can Not Visit Net In Main Thread!");
        }
        if(MyUtils.isEmpty(url))
            return null;
        if(mDiskLruCache==null){
            return null;
        }
        String key=hashKeyFromUrl(url);
        DiskLruCache.Editor editor=mDiskLruCache.edit(key);
        if(editor!=null){
            OutputStream out=editor.newOutputStream(DISKCACHEINDXE);
            if(downLoadUrlToStream(url,out)){
                editor.commit();
            }else{
                editor.abort();
            }
            mDiskLruCache.flush();
        }
        return loadBitmapFromDiskCache(url,reqWidth,reqHeight);
    }

    private Bitmap loadBitmapFromUrl(String urlString,int reqWidth,int reqHeight){
        if(MyUtils.isEmpty(urlString))
            return null;
        BufferedInputStream in=null;
        HttpURLConnection connection=null;
        URL url=null;
        Bitmap bitmap=null;
        try {
            url=new URL(urlString);
            connection= (HttpURLConnection) url.openConnection();
            in=new BufferedInputStream(connection.getInputStream(),IO_BUFFER_SIZE);
            bitmap = BitmapFactory.decodeStream(in);
        } catch (IOException e) {
            e.printStackTrace();
            Log.w(TAG,"Failed To Get Bitmap From Url!");
        }finally{
            MyUtils.close(in);
            if(connection!=null)
                connection.disconnect();
        }
        return bitmap;
    }


    private boolean downLoadUrlToStream(String urlString,OutputStream outputStream) {
        if(MyUtils.isEmpty(urlString)){
            return false;
        }
        HttpURLConnection connection=null;
        BufferedInputStream in=null;
        BufferedOutputStream out=null;
        try {
            URL url=new URL(urlString);
            connection = (HttpURLConnection) url.openConnection();
            in = new BufferedInputStream(connection.getInputStream(),IO_BUFFER_SIZE);
            out= new BufferedOutputStream(outputStream,IO_BUFFER_SIZE);
            int t;
            while((t=in.read())!=-1){
                out.write(t);
            }
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            Log.d(TAG,"Load Bitmap From Url Failed!");
        }finally {
            MyUtils.close(in);
            MyUtils.close(out);
            if(connection!=null)
                connection.disconnect();
        }
        return false;
    }

    public File getCacheDir(String uniqueName){
        return getCacheDir(mContext,uniqueName);
    }

    public File getCacheDir(){
        return getCacheDir("");
    }

    public static  File getCacheDir(Context context,String uniqueName){
        boolean externalStorageAvaliable=Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)||!Environment.isExternalStorageRemovable();
        File cacheDir=null;
        if(externalStorageAvaliable){
            cacheDir = context.getExternalCacheDir();
        }else{
            cacheDir = context.getCacheDir();
        }
        return new File(cacheDir+File.separator+uniqueName);
    }

    private long getUsableSpace(File path){
        if(Build.VERSION.SDK_INT>= Build.VERSION_CODES.GINGERBREAD){
            return path.getUsableSpace();
        }
        StatFs stats=new StatFs(path.getPath());
        return stats.getBlockSize()*stats.getAvailableBlocks();
    }

    private int getVersion(Context context){
        try {
            PackageInfo info=context.getPackageManager().getPackageInfo(context.getPackageName(),0);
            return info.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        return 1;
    }

    private String hashKeyFromUrl(String url){
        String cacheKey=null;
        try {
            MessageDigest md= MessageDigest.getInstance("MD5");
            md.update(url.getBytes());
            cacheKey=bytesToHexString(md.digest());
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return cacheKey;
    }
        private String bytesToHexString(byte[] bytes){
            StringBuilder sb=new StringBuilder();
            for(int i=0;i<bytes.length;i++){
                String hex=Integer.toHexString(0xff&bytes[i]);
                if(hex.length()==1)
                    sb.append(0);
                sb.append(hex);
            }
//            if(sb.length()>20)
//                sb.delete(20,sb.length()-1);
            return sb.toString();
        }

    private static class LoadResult{
        private Bitmap bitmap;
        private ImageView img;
        private String url;
        private LoadResult(Bitmap bitmap,ImageView img,String url){
            this.bitmap=bitmap;
            this.img=img;
            this.url=url;
        }
    }
}
