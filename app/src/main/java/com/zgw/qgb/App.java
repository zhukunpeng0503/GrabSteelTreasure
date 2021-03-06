package com.zgw.qgb;

import android.app.Application;
import android.content.Context;
import android.support.annotation.NonNull;
import android.support.multidex.MultiDex;
import android.support.v7.app.AppCompatDelegate;

import com.alibaba.android.arouter.launcher.ARouter;
import com.squareup.leakcanary.LeakCanary;
import com.zgw.qgb.helper.ActivityMgr;
import com.zgw.qgb.helper.DebugHelper;
import com.zgw.qgb.helper.RudenessScreenHelper;

import java.util.Locale;


/**
 */

public class App extends Application {
    private static App instance;

    @Override public void onCreate() {
        super.onCreate();
        //支持vector drawable
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);
        instance = this;
        init();
    }

    @NonNull
    public static App getInstance() {
        return instance;
    }

    public static Context getContext() {
        return getInstance().getApplicationContext();
    }

    private void init() {


        MultiDex.install(this);
        DebugHelper.getInstance().syscIsDebug(this);
        LeakCanary.install(this);
        //AppHelper.updateAppLanguage(this); 未完成
        ActivityMgr.getInstance().init(this);
        RudenessScreenHelper.getInstance().init(this,720)/*.activate()*/;
        //FabricHelper.getInstance().init(this);
        //Timber.plant(isDebug() ? new Timber.DebugTree() : new CrashlyticsTree());

        initARouter();
    }

    private void initARouter() {
        if (isDebug()) {           // 这两行必须写在init之前，否则这些配置在init过程中将无效
            ARouter.openLog();     // 打印日志
            ARouter.openDebug();   // 开启调试模式(如果在InstantRun模式下运行，必须开启调试模式！线上版本需要关闭,否则有安全风险)
        }
        ARouter.init(instance); // 尽可能早，推荐在Application中初始化
    }

    public boolean isDebug() {
        return DebugHelper.getInstance().isDebug();
    }
    public static Locale getLocale() {
        return Locale.CHINA;
    }

}