package de.robv.android.xposed;

public abstract class XC_MethodHook {
    protected void beforeHookedMethod(MethodHookParam param) throws Throwable { }
    protected void afterHookedMethod(MethodHookParam param) throws Throwable { }

    public static final class MethodHookParam {
        public Object[] args;
        private Object result;
        public Object getResult() { return result; }
        public void setResult(Object r) { result = r; }
    }
}
