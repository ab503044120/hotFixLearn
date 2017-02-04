package org.huihui.resourcefix;

import android.content.Context;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.os.Build;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.HashMap;
import java.util.logging.Logger;

/**
 * User: Administrator
 * Date: 2017-02-04 {HOUR}:42
 */
public class ResourceLoader {
    private static final String TAG = "ResourceLoader";

    public static boolean patchResources(Context context, File patchResource) {
        try {
            if (context == null || patchResource == null){
                return false;
            }
            if (!patchResource.exists()) {
                return false;
            }
            //通过构造函数new一个AssetManager对象
            AssetManager newAssetManager = AndroidHack.AssetManager_construct.invoke().statically();
            //调用AssetManager对象的addAssetPath方法添加patch资源
            int cookie = AndroidHack.AssetManager_addAssetPath.invokeWithParam(patchResource.getAbsolutePath()).on(newAssetManager);
            //添加成功时cookie必然大于0
            if (cookie == 0) {
                Logger.e(TAG, "Could not create new AssetManager");
                return false;
            }
            // 在Android 19以前需要调用这个方法，但是Android L后不需要，实际情况Andorid L上调用也不会有问题，因此这里不区分版本
            // Kitkat needs this method call, Lollipop doesn't. However, it doesn't seem to cause any harm
            // in L, so we do it unconditionally.
            AndroidHack.AssetManager_ensureStringBlocks.invoke().on(newAssetManager);

            //获取内存中的Resource对象的弱引用
            Collection<WeakReference<Resources>> references;

            if (Build.VERSION.SDK_INT >= 24) {
                // Android N，获取的是一个ArrayList，直接赋值给references对象
                // Find the singleton instance of ResourcesManager
                Object resourcesManager = AndroidHack.ResourcesManager_getInstance.invoke().statically();
                //noinspection unchecked
                references = (Collection<WeakReference<Resources>>) AndroidHack.ResourcesManager_mResourceReferences.on(resourcesManager).get();
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                //Android 19以上 获得的是一个ArrayMap，调用其values方法后赋值给references
                // Find the singleton instance of ResourcesManager
                Object resourcesManager = AndroidHack.ResourcesManager_getInstance.invoke().statically();
                @SuppressWarnings("unchecked")
                ArrayMap<?, WeakReference<Resources>> arrayMap = AndroidHack.ResourcesManager_mActiveResources.on(resourcesManager).get();
                references = arrayMap.values();
            } else {
                //Android 19以下，通过ActivityThread获取得到的是一个HashMap对象，通过其values方法获得对象赋值给references
                Object activityThread = AndroidHack.getActivityThread();
                @SuppressWarnings("unchecked")
                HashMap<?, WeakReference<Resources>> map = (HashMap<?, WeakReference<Resources>>) AndroidHack.ActivityThread_mActiveResources.on(activityThread).get();
                references = map.values();
            }
            //遍历获取到的Ressources对象的弱引用，将其AssetManager对象替换为我们的patch的AssetManager
            for (WeakReference<Resources> wr : references) {
                Resources resources = wr.get();
                // Set the AssetManager of the Resources instance to our brand new one
                if (resources != null) {
                    if (Build.VERSION.SDK_INT >= 24) {
                        Object resourceImpl = AndroidHack.Resources_ResourcesImpl.get(resources);
                        AndroidHack.ResourcesImpl_mAssets.set(resourceImpl, newAssetManager);
                    } else {
                        AndroidHack.Resources_mAssets.set(resources, newAssetManager);
                    }
                    resources.updateConfiguration(resources.getConfiguration(), resources.getDisplayMetrics());
                }
            }
            return true;
        } catch (Throwable throwable) {
            Logger.e(TAG, throwable);
            throwable.printStackTrace();
        }
        return false;
    }
}  