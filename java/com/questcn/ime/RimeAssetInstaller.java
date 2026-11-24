package com.questcn.ime;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/** 把 APK 内的 rime_ice / opencc / default 配置复制到 filesDir/rime。 */
public final class RimeAssetInstaller {
    private static final String TAG = "RimeAssets";
    private static final int ASSET_VERSION = 2;

    private RimeAssetInstaller() {
    }

    public static File prepare(Context context) throws IOException {
        File rimeDir = new File(context.getFilesDir(), "rime");
        File marker = new File(rimeDir, ".questcn_assets_version");
        if (marker.isFile() && String.valueOf(ASSET_VERSION).equals(readText(marker))) {
            return rimeDir;
        }
        if (!rimeDir.exists() && !rimeDir.mkdirs()) {
            throw new IOException("cannot create " + rimeDir);
        }
        AssetManager assets = context.getAssets();
        copyDir(assets, "rime_ice", rimeDir);
        copyDir(assets, "rime_chinese/opencc", new File(rimeDir, "opencc"));
        copyFile(assets, "rime_chinese/cyime_particle_shortcuts.yaml",
                new File(rimeDir, "cyime_particle_shortcuts.yaml"));
        copyFile(assets, "rime_chinese/cyime_punctuator.yaml",
                new File(rimeDir, "cyime_punctuator.yaml"));
        copyFile(assets, "rime_chinese/cyime_tw.dict.yaml",
                new File(rimeDir, "cyime_tw.dict.yaml"));
        copyFile(assets, "rime/default.yaml", new File(rimeDir, "default.yaml"));
        copyFile(assets, "rime/default.custom.yaml", new File(rimeDir, "default.custom.yaml"));
        copyFile(assets, "rime/symbols.yaml", new File(rimeDir, "symbols.yaml"));
        copyFile(assets, "rime/custom_phrase.txt", new File(rimeDir, "custom_phrase.txt"));
        writeText(marker, String.valueOf(ASSET_VERSION));
        Log.i(TAG, "rime assets installed at " + rimeDir + " (" + dirSize(rimeDir) + " bytes)");
        return rimeDir;
    }

    private static void copyDir(AssetManager assets, String assetPath, File dest)
            throws IOException {
        String[] children = assets.list(assetPath);
        if (children == null || children.length == 0) {
            copyFile(assets, assetPath, dest);
            return;
        }
        if (!dest.exists() && !dest.mkdirs()) {
            throw new IOException("cannot create " + dest);
        }
        for (String child : children) {
            copyDir(assets, assetPath + "/" + child, new File(dest, child));
        }
    }

    private static void copyFile(AssetManager assets, String assetPath, File dest)
            throws IOException {
        File parent = dest.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("cannot create " + parent);
        }
        InputStream in = null;
        OutputStream out = null;
        try {
            in = assets.open(assetPath);
            out = new FileOutputStream(dest);
            byte[] buffer = new byte[64 * 1024];
            int n;
            while ((n = in.read(buffer)) > 0) {
                out.write(buffer, 0, n);
            }
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException ignored) {
                }
            }
            if (out != null) {
                try {
                    out.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    private static String readText(File file) {
        try {
            InputStream in = new java.io.FileInputStream(file);
            try {
                byte[] data = new byte[(int) file.length()];
                int off = 0;
                while (off < data.length) {
                    int n = in.read(data, off, data.length - off);
                    if (n < 0) {
                        break;
                    }
                    off += n;
                }
                return new String(data, 0, off, "UTF-8");
            } finally {
                in.close();
            }
        } catch (IOException e) {
            return "";
        }
    }

    private static void writeText(File file, String text) throws IOException {
        FileOutputStream out = new FileOutputStream(file);
        try {
            out.write(text.getBytes("UTF-8"));
        } finally {
            out.close();
        }
    }

    private static long dirSize(File dir) {
        long total = 0;
        File[] files = dir.listFiles();
        if (files == null) {
            return 0;
        }
        for (File f : files) {
            total += f.isDirectory() ? dirSize(f) : f.length();
        }
        return total;
    }
}
