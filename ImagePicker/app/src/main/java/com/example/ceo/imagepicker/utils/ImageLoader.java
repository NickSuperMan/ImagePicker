package com.example.ceo.imagepicker.utils;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.DisplayMetrics;
import android.util.LruCache;
import android.view.ViewGroup;
import android.widget.ImageView;

import java.lang.reflect.Field;
import java.util.LinkedList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

/**
 * Created by roy on 2015/12/24.
 */
public class ImageLoader {

    public static ImageLoader mInstance;

    /**
     * 图片缓存
     */
    private LruCache<String, Bitmap> mLruCache;

    /**
     * 线程池
     */
    private ExecutorService mThreadPool;

    private static final int DEAFULT_THREAD_COUNT = 1;
    /**
     * 队列的调度方式
     */
    private Type mType = Type.LIFO;
    /**
     * 任务队列
     */
    private LinkedList<Runnable> mTaskQueue;
    /**
     * 后台轮询线程
     */
    private Thread mPoolThread;
    private Handler mPoolThreadHandler;

    /**
     * UI线程中的Handler
     */
    private Handler mUIHandler;

    private Semaphore mSemaphorePoolThreadHandler = new Semaphore(0);
    private Semaphore mSemaphorePoolThread;

    public enum Type {
        FIFO, LIFO;
    }


    private ImageLoader(int threadCount, Type type) {
        init(threadCount, type);
    }

    private void init(int threadCount, Type type) {
        /**
         * 后台轮询线程
         */
        mPoolThread = new Thread() {
            @Override
            public void run() {
                super.run();

                Looper.prepare();
                mPoolThreadHandler = new Handler() {
                    @Override
                    public void handleMessage(Message msg) {
                        super.handleMessage(msg);

                        mThreadPool.execute(getTask());

                        try {
                            mSemaphorePoolThread.acquire();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                };
                /**
                 * 释放一个信号量
                 */
                mSemaphorePoolThreadHandler.release();
                Looper.loop();
            }
        };
        mPoolThread.start();

        int maxMemory = (int) Runtime.getRuntime().maxMemory();
        int cacheMemory = maxMemory / 8;

        mLruCache = new LruCache<String, Bitmap>(cacheMemory) {
            @Override
            protected int sizeOf(String key, Bitmap value) {
                return value.getRowBytes() * value.getHeight();
            }
        };

        mThreadPool = Executors.newFixedThreadPool(threadCount);
        mTaskQueue = new LinkedList<>();
        mType = type;

        mSemaphorePoolThread = new Semaphore(threadCount);
    }

    /**
     * 从任务队列取出一个方法
     *
     * @return
     */
    private Runnable getTask() {

        if (mType == Type.FIFO) {
            return mTaskQueue.removeFirst();
        } else if (mType == Type.LIFO) {
            return mTaskQueue.removeLast();
        }

        return null;
    }


    public static ImageLoader getInstance(int threadCount, Type type) {
        if (mInstance == null) {
            synchronized (ImageLoader.class) {
                if (mInstance == null) {
                    mInstance = new ImageLoader(threadCount, type);
                }
            }
        }
        return mInstance;
    }

    /**
     * 根据path 为imageview设置图片
     *
     * @param path
     * @param imageView
     */
    public void loadImage(final String path, final ImageView imageView) {

        imageView.setTag(path);

        if (mUIHandler == null) {
            mUIHandler = new Handler() {
                @Override
                public void handleMessage(Message msg) {
                    super.handleMessage(msg);
                    /**
                     * 获取得到的图片，为imageView回调设置图片
                     */
                    ImgBeanHolder beanHolder = (ImgBeanHolder) msg.obj;
                    Bitmap bitmap = beanHolder.bitmap;
                    ImageView imageView = beanHolder.imageView;
                    String path = beanHolder.path;
                    /**
                     * 将path与getTag存储路径进行比较
                     */
                    if (imageView.getTag().toString().equals(path)) {
                        imageView.setImageBitmap(bitmap);
                    }
                }
            };
        }

        Bitmap bm = getBitmapFromLruCache(path);
        if (bm != null) {
            refreshBitmap(bm, imageView, path);
        } else {
            addTask(new Runnable() {
                @Override
                public void run() {
                    /**
                     * 1 获得图片需要显示的大小
                     */

                    ImageSize imageSize = getImageSize(imageView);

                    /**
                     * 2.压缩图片
                     */

                    Bitmap bm = decodeSampledBitmapFromPath(path, imageSize.width, imageSize.height);

                    /**
                     * 3.把图片加入到缓存
                     */

                    addBitmapToLruCache(path, bm);

                    refreshBitmap(bm, imageView, path);

                    mSemaphorePoolThread.release();
                }
            });
        }
    }

    /**
     * 通知线程刷新加载图片
     *
     * @param bm
     * @param imageView
     * @param path
     */
    private void refreshBitmap(Bitmap bm, ImageView imageView, String path) {
        Message message = Message.obtain();
        ImgBeanHolder beanHolder = new ImgBeanHolder();
        beanHolder.bitmap = bm;
        beanHolder.imageView = imageView;
        beanHolder.path = path;
        message.obj = beanHolder;
        mUIHandler.sendMessage(message);
    }

    /**
     * 把bitmap加入到缓存
     *
     * @param path
     * @param bm
     */
    private void addBitmapToLruCache(String path, Bitmap bm) {
        if (getBitmapFromLruCache(path) == null) {
            if (bm != null) {
                mLruCache.put(path, bm);
            }
        }
    }

    /**
     * 压缩图片
     *
     * @param path
     * @param width
     * @param height
     * @return
     */
    private Bitmap decodeSampledBitmapFromPath(String path, int width, int height) {
        /**
         * 获得图片的宽和高，并不把图片加载到内存中
         */
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(path, options);

        options.inSampleSize = caculateInSampleSize(options, width, height);

        /**
         * 使用获取到的InSampleSize再次解析图片
         */
        options.inJustDecodeBounds = false;
        Bitmap bitmap = BitmapFactory.decodeFile(path, options);
        return bitmap;
    }

    /**
     * 根据需求的宽和高和实际的宽和高计算SampleSize
     *
     * @param options
     * @param width
     * @param height
     * @return
     */
    private int caculateInSampleSize(BitmapFactory.Options options, int reqWidth, int reqHeight) {

        int width = options.outWidth;
        int heigt = options.outHeight;

        int inSampleSize = 1;
        if (width > reqWidth || heigt > reqHeight) {
            int widthRadio = Math.round(width / reqWidth);
            int heightRadio = Math.round(heigt / reqHeight);

            inSampleSize = Math.max(widthRadio, heightRadio);
        }
        return inSampleSize;
    }

    /**
     * 根据ImageView获取适当的压缩的宽和高
     *
     * @param imageView
     * @return
     */
    private ImageSize getImageSize(ImageView imageView) {
        ImageSize imageSize = new ImageSize();

        DisplayMetrics displayMetrics = imageView.getContext().getResources().getDisplayMetrics();

        ViewGroup.LayoutParams lp = imageView.getLayoutParams();
        /**
         * 获取imageview的实际宽度
         */
        int width = imageView.getWidth();

        if (width <= 0) {
            /**
             * 获取imageview在layout中声明的宽度
             */
            width = lp.width;
        }
        if (width <= 0) {
            /**
             * 检查最大值
             */
//            width = imageView.getMaxWidth();
            width = getImageViewFiledValue(imageView, "mMaxWidth");
        }
        if (width <= 0) {
            /**
             * 屏幕宽度
             */
            width = displayMetrics.widthPixels;
        }

        /**
         * 获取imageview的实际高度
         */
        int height = imageView.getHeight();

        if (height <= 0) {
            /**
             * 获取imageview在layout中声明的高度
             */
            height = lp.height;
        }
        if (height <= 0) {
            /**
             * 检查最大值
             */
//            height = imageView.getMaxHeight();
            height = getImageViewFiledValue(imageView, "mMaxHeight");
        }
        if (height <= 0) {
            height = displayMetrics.heightPixels;
        }

        imageSize.width = width;
        imageSize.height = height;

        return imageSize;
    }

    /**
     * 利用反射获取到新API中的属性，做到向下兼容
     *
     * @param object
     * @param fieldName
     * @return
     */
    private static int getImageViewFiledValue(Object object, String fieldName) {

        int value = 0;

        try {
            Field field = ImageView.class.getDeclaredField(fieldName);
            field.setAccessible(true);

            int fieldValue = field.getInt(object);
            if (fieldValue > 0 && fieldValue < Integer.MAX_VALUE) {
                value = fieldValue;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return value;
    }


    private synchronized void addTask(Runnable runnable) {
        mTaskQueue.add(runnable);
        try {
            if (mPoolThreadHandler == null)
                mSemaphorePoolThreadHandler.acquire();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        mPoolThreadHandler.sendEmptyMessage(0x110);
    }

    /**
     * 从缓存中得到图片
     *
     * @param path
     * @return
     */
    private Bitmap getBitmapFromLruCache(String path) {
        return mLruCache.get(path);
    }

    private class ImageSize {
        int width;
        int height;
    }

    private class ImgBeanHolder {
        Bitmap bitmap;
        ImageView imageView;
        String path;
    }
}
