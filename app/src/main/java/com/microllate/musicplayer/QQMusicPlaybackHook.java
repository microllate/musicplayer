package com.microllate.musicplayer;

import android.util.Log;

import java.lang.reflect.Method;
import java.util.Arrays;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class QQMusicPlaybackHook implements IXposedHookLoadPackage {

    private static final String TAG = "miu-iYellowPage-QQMusic";
    private static final String PKG = "com.tencent.qqmusiclite.universal";

    private static void log(String msg) {
        XposedBridge.log(TAG + ": " + msg);
        Log.d(TAG, msg);
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!PKG.equals(lpparam.packageName)) return;

        log("loaded process=" + lpparam.processName);

        tryHookSongQueryManager(lpparam.classLoader);
        tryHookFreeModeManager(lpparam.classLoader);
    }

    private void tryHookSongQueryManager(ClassLoader cl) {
        try {
            Class<?> c = XposedHelpers.findClass(
                    "com.tencent.qqmusicplayerprocess.audio.playermanager.songurlquery.SongQueryManager", cl);

            Method g = findMethod(c, "g", 1);
            if (g != null) {
                XposedBridge.hookMethod(g, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam p) {
                        Object song = p.args.length > 0 ? p.args[0] : null;
                        log("SongQueryManager.g() song=" + describeSong(song));
                    }
                });
                log("hooked SongQueryManager.g");
            }

            Method targetC = null;
            for (Method m : c.getDeclaredMethods()) {
                if (!"c".equals(m.getName())) continue;
                Class<?>[] t = m.getParameterTypes();
                if (t.length >= 2 && t[0] == String.class) {
                    targetC = m;
                    break;
                }
            }

            if (targetC != null) {
                XposedBridge.hookMethod(targetC, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam p) {
                        String url = p.args.length > 0 ? String.valueOf(p.args[0]) : "null";
                        Object song = p.args.length > 1 ? p.args[1] : null;
                        log("SongQueryManager.c() url=" + url);
                        log("SongQueryManager.c() song=" + describeSong(song));
                        log("SongQueryManager.c() stack=" + shortStack());
                    }
                });
                log("hooked SongQueryManager.c signature=" + targetC);
            } else {
                log("SongQueryManager.c target not found");
            }
        } catch (Throwable t) {
            log("SongQueryManager hook failed: " + Log.getStackTraceString(t));
        }
    }

    private void tryHookFreeModeManager(ClassLoader cl) {
        try {
            Class<?> c = XposedHelpers.findClass(
                    "com.tencent.qqmusiclite.freemode.FreeModeManager", cl);

            Method can = findMethod(c, "canNoLoginFreeModePlayVipSong", 1);
            if (can != null) {
                XposedBridge.hookMethod(can, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam p) {
                        log("canNoLoginFreeModePlayVipSong -> " + p.getResult()
                                + " song=" + describeSong(p.args[0]));
                    }
                });
            }

            Method remain = null;
            for (Method m : c.getDeclaredMethods()) {
                if ("getRemainFreeTime".equals(m.getName())) {
                    remain = m;
                    break;
                }
            }
            if (remain != null) {
                XposedBridge.hookMethod(remain, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam p) {
                        log("getRemainFreeTime -> " + p.getResult());
                    }
                });
            }

            log("hooked FreeModeManager observers");
        } catch (Throwable t) {
            log("FreeModeManager hook failed: " + Log.getStackTraceString(t));
        }
    }

    private static Method findMethod(Class<?> c, String name, int parameterCount) {
        for (Method m : c.getDeclaredMethods()) {
            if (name.equals(m.getName()) && m.getParameterTypes().length == parameterCount) {
                return m;
            }
        }
        return null;
    }

    private static String describeSong(Object song) {
        if (song == null) return "null";
        try {
            String id = String.valueOf(XposedHelpers.callMethod(song, "getQQSongId"));
            String name;
            try {
                name = String.valueOf(XposedHelpers.callMethod(song, "getSongName"));
            } catch (Throwable ignored) {
                name = "?";
            }
            String ppurl;
            try {
                ppurl = String.valueOf(XposedHelpers.callMethod(song, "getFreeModePpUrl"));
            } catch (Throwable ignored) {
                ppurl = "?";
            }
            return "songId=" + id + ", name=" + name + ", freeModePpUrl=" + ppurl;
        } catch (Throwable t) {
            return song.getClass().getName() + ", describeError=" + t.getClass().getSimpleName();
        }
    }

    private static String shortStack() {
        StackTraceElement[] s = Thread.currentThread().getStackTrace();
        int start = Math.min(4, s.length);
        int end = Math.min(start + 8, s.length);
        return Arrays.toString(Arrays.copyOfRange(s, start, end));
    }
}
