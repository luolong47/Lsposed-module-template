package de.robv.android.xposed;

import de.robv.android.xposed.callbacks.XC_LoadPackage;

public interface IXposedHookLoadPackage {
    void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) throws Throwable;
}
