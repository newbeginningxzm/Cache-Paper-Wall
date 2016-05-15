package com.bupt.paragon.paperwall;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.LruCache;
import android.view.View;
import android.widget.AbsListView;
import android.widget.GridView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.ImageView;
import android.widget.TextView;

import com.bupt.paragon.ImageLoader.ImageResizer;
import com.bupt.paragon.MyUtils;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;

public class MainActivity extends AppCompatActivity implements OnScrollListener{
    private int imgWidth,imgHeight;
    private boolean isWifi;
    private ImageAdapter mAdpter;
    private GridView mGrid;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        init();
        HashMap map=new HashMap();
    }

    private void testLru(){
        TextView info= (TextView) findViewById(R.id.info);
        info.setVisibility(View.VISIBLE);
        int maxMemory= (int) Runtime.getRuntime().maxMemory() ;
        int cacheSize=maxMemory/8;
        LruCache<String,String> mMemeoryCache=new LruCache<String,String>(cacheSize) {
            @Override
            protected int sizeOf(String key, String value) {
                return value.getBytes().length;
            }
        };
        String uri="http://pic25.nipic.com/20121210/7447430_172514301000_2.jpg";
        String key=hashKeyFromUrl(uri);
        String test="记得在很早之前，我有写过一篇文章Android高效加载大图、多图解决方案，有效避免程序OOM，这篇文章是翻译自Android Doc的，其中防止多图OOM的核心解决思路就是使用LruCache技术。但LruCache只是管理了内存中图片的存储与释放，如果图片从内存中被移除的话，那么又需要从网络上重新加载一次图片，这显然非常耗时。对此，Google又提供了一套硬盘缓存的解决方案：DiskLruCache(非Google官方编写，但获得官方认证)。只可惜，Android Doc中并没有对DiskLruCache的用法给出详细的说明，而网上关于DiskLruCache的资料也少之又少，因此今天我准备专门写一篇博客来详细讲解DiskLruCache的用法，以及分析它的工作原理，这应该也是目前网上关于DiskLruCache最详细的资料了。";
        mMemeoryCache.put(key, test);
        info.setText(mMemeoryCache.get(key));
        Bitmap bitmap= ImageResizer.decodeSampledBitmapFromResource(getResources(),R.drawable.lighthouse,100,100);
        LruCache<String,Bitmap>  mImgCache=new LruCache<String,Bitmap>(cacheSize){
            @Override
            protected int sizeOf(String key, Bitmap value) {
                return value.getRowBytes();
            }
        };
        mImgCache.put(key,bitmap);
        ImageView img= (ImageView) findViewById(R.id.img_lru);
        img.setVisibility(View.VISIBLE);
        img.setImageBitmap(mImgCache.get(key));
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
        return sb.toString();
    }

    private void init(){
        AsyncTask test;
        isWifi= MyUtils.isWifi(this);
        mGrid= (GridView) findViewById(R.id.imgGrid);
        mGrid.setVisibility(View.VISIBLE);
        int screenWidth=MyUtils.getScreenMetrics(this).widthPixels;
        int space = (int) MyUtils.dp2px(this,20);
        imgWidth=(screenWidth-space)/mGrid.getNumColumns();
        imgHeight=imgWidth;
        mAdpter=new ImageAdapter(this,R.layout.paperwall_item,imgWidth,imgHeight);
        mGrid.setAdapter(mAdpter);
        mGrid.setOnScrollListener(this);
        if(!isWifi){
            AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(this);
            dialogBuilder.setMessage("是否允许从网络加载图片，大概需要5MB流量");
            dialogBuilder.setCancelable(false);
            dialogBuilder.setPositiveButton("允许", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    mAdpter.allowAccessNet(true);
                }
            });
            dialogBuilder.setNegativeButton("不允许", null);
            dialogBuilder.show();
        }
    }

    @Override
    public void onScrollStateChanged(AbsListView view, int scrollState) {
        if(scrollState== AbsListView.OnScrollListener.SCROLL_STATE_IDLE)
            mAdpter.setScrollState(true);
        else
            mAdpter.setScrollState(false);
    }

    @Override
    public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {

    }
}
