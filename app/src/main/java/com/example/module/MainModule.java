package com.example.module;


import android.content.res.Configuration;
import android.content.res.Resources;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

// 类名必须和文件名一致，所以这里用 MainModule
public class MainModule implements IXposedHookLoadPackage {

    @Override
    public void handleLoadPackage(final LoadPackageParam lpparam) throws Throwable {
        
        // 排除掉安卓系统自身的框架，防止全局变黑导致系统界面崩溃
        if (lpparam.packageName.equals("android") || lpparam.packageName.startsWith("com.android.")) {
            return;
        }

        XposedBridge.log("通用深色模块已成功注入应用: " + lpparam.packageName);

        try {
            // ---- 核心 Hook 1：强制修改 Resources 资源配置中的 uiMode ----
            XposedHelpers.findAndHookMethod(
                Resources.class, 
                "getConfiguration", 
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        Configuration config = (Configuration) param.getResult();
                        if (config != null) {
                            config.uiMode &= ~Configuration.UI_MODE_NIGHT_MASK;
                            config.uiMode |= Configuration.UI_MODE_NIGHT_YES;
                            param.setResult(config);
                        }
                    }
                }
            );

            // ---- 核心 Hook 2：拦截 App 内部试图锁死浅色模式的行为 ----
            Class<?> appCompatDelegateClass = XposedHelpers.findClassIfExists(
                "androidx.appcompat.app.AppCompatDelegate", 
                lpparam.classLoader
            );
            
            if (appCompatDelegateClass != null) {
                XposedHelpers.findAndHookMethod(
                    appCompatDelegateClass, 
                    "setDefaultNightMode", 
                    int.class, 
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            int mode = (int) param.args[0];
                            if (mode != 2) {
                                param.args[0] = 2; // 强行改为 MODE_NIGHT_YES
                            }
                        }
                    }
                );
            }
        } catch (Throwable t) {
            XposedBridge.log("深色 Hook 运行异常: " + t.getMessage());
        }
    }
}
