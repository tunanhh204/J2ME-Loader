package ru.woesss.j2me.installer;

import android.app.Application;
import android.net.Uri;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

import io.reactivex.Single;
import ru.playsoftware.j2meloader.applist.AppItem;
import ru.playsoftware.j2meloader.appsdb.AppRepository;

/** Installs the game bundled inside this standalone build on first launch. */
public final class EmbeddedGameInstaller {
    private static final String TAG = "EmbeddedGameInstaller";

    private EmbeddedGameInstaller() {
    }

    public static Single<AppItem> install(Application context, AppRepository repository) {
        Log.i(TAG, "Starting bundled game installation");
        return Single.fromCallable(() -> {
                    File jar = copyBundledJar(context);
                    Log.i(TAG, "Bundled JAR copied: " + jar.length() + " bytes");
                    return jar;
                })
                .flatMap(jar -> {
                    AppInstaller installer = new AppInstaller(jar.getAbsolutePath(),
                            Uri.fromFile(jar), context, repository);
                    return Single.<Integer>create((emitter) -> {
                                Log.i(TAG, "Reading JAR manifest");
                                installer.loadInfo(emitter);
                            })
                            .doOnSuccess(status -> Log.i(TAG, "Manifest check completed with status " + status))
                            .flatMap(status -> {
                                if (status == AppInstaller.STATUS_NEW) {
                                    return Single.<Integer>create((emitter) -> {
                                                Log.i(TAG, "Starting JAR conversion and installation");
                                                installer.install(emitter);
                                            })
                                            .doOnSuccess(result -> Log.i(TAG, "JAR conversion/install returned " + result))
                                            .map(result -> {
                                                if (result != AppInstaller.STATUS_SUCCESS) {
                                                    throw new IllegalStateException("Game installation failed: " + result);
                                                }
                                                return installer.getExistsApp();
                                            });
                                }
                                if (installer.getExistsApp() != null) {
                                    Log.i(TAG, "Bundled game already installed: " + installer.getExistsApp().getTitle());
                                    return Single.just(installer.getExistsApp());
                                }
                                return Single.error(new IllegalStateException(
                                        "Unsupported embedded game installer status: " + status));
                            })
                            .doFinally(() -> {
                                Log.i(TAG, "Cleaning installer temporary files");
                                installer.clearCache();
                                installer.deleteTemp();
                                // Keep the bundled copy out of the persistent game directory.
                                // It can be recreated from APK assets if installation is retried.
                                //noinspection ResultOfMethodCallIgnored
                                jar.delete();
                            });
                });
    }

    private static File copyBundledJar(Application context) throws Exception {
        File directory = new File(context.getCacheDir(), "embedded-game");
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IllegalStateException("Cannot create embedded game cache directory");
        }
        File jar = new File(directory, "Bolacthoitiensu.jar");
        try (InputStream input = context.getAssets().open("Bolacthoitiensu.jar");
             FileOutputStream output = new FileOutputStream(jar, false)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                output.write(buffer, 0, count);
            }
            output.getFD().sync();
        }
        Log.i(TAG, "Bundled JAR copy finished");
        return jar;
    }
}
