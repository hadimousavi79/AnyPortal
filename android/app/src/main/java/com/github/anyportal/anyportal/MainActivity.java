package com.github.anyportal.anyportal;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.net.VpnService;
import android.os.Bundle;
import android.os.FileObserver;
import android.os.IBinder;
import android.os.Looper;
import android.os.Handler;

import androidx.annotation.NonNull;

import io.flutter.embedding.android.FlutterActivity;
import io.flutter.embedding.engine.FlutterEngine;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;

public class MainActivity extends FlutterActivity {
    private static final String CHANNEL = "com.github.anyportal.anyportal";
    private static MethodChannel methodChannel;

    private BroadcastReceiver broadcastReceiver;

    @Override
    public void configureFlutterEngine(@NonNull FlutterEngine flutterEngine) {
        super.configureFlutterEngine(flutterEngine);
        final var messenger = flutterEngine.getDartExecutor().getBinaryMessenger();
        methodChannel = new MethodChannel(messenger, CHANNEL);
        methodChannel.setMethodCallHandler(this::onMethodCall);
    }

    @Override
    protected void onStart() {
        super.onStart();
        Intent intent = new Intent(this, TProxyService.class);
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		/* Request VPN permission */
		Intent intent = VpnService.prepare(MainActivity.this);
		if (intent != null) {
            startActivityForResult(intent, 0);
        } else {
            onActivityResult(0, RESULT_OK, null);
        }
        // Copy geoip.dat and geosite.dat to the documents directory if needed
        AssetUtils.copyAssetsIfNeeded(this);
        
        // listen for broadcast
        registerBroadcastReceiver();
	}

    @Override
    protected void onResume() {
        super.onResume();
        if (fileObserver != null) {
            fileObserver.startWatching();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (fileObserver != null) {
            fileObserver.stopWatching();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Unregister the BroadcastReceiver
        unregisterReceiver(broadcastReceiver);
    }


    
    /// bind TProxyService
    private TProxyService tProxyService;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            TProxyService.LocalBinder binder = (TProxyService.LocalBinder) service;
            tProxyService = binder.getService();

            // Set the status listener to receive updates
            tProxyService.setStatusUpdateListener(isCoreActive -> {
                // Handle VPN status update in MainActivity
                if (isCoreActive){
                    methodChannel.invokeMethod("onCoreActivated", null);
                } else {
                    methodChannel.invokeMethod("onCoreDeactivated", null);
                }
            });
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
        }
    };



    ///
    private FileObserver fileObserver;

    private void onMethodCall(MethodCall call, MethodChannel.Result result) {
        if (call.method.equals("vpn.startAll")){
            tProxyService.startAll();
            result.success(null);
        } else if (call.method.equals("vpn.stopAll")){
            tProxyService.stopAll();
            result.success(null);
        } else if (call.method.equals("vpn.startTun")){
            tProxyService.startTun();
            result.success(null);
        } else if (call.method.equals("vpn.stopTun")){
            tProxyService.stopTun();
            result.success(null);
        } else if (call.method.equals("vpn.isCoreActive")){
            result.success(tProxyService.isCoreActive);
        } else if (call.method.equals("vpn.isTunActive")){
            result.success(tProxyService.isTunActive);
        } else if (call.method.equals("log.core.startWatching")){
            String filePath = call.argument("filePath");
            fileObserver = new FileObserver(filePath) {
                @Override
                public void onEvent(int event, String path) {
                    new Handler(Looper.getMainLooper()).post(new Runnable() {
                        @Override
                        public void run() {
                            if (event == FileObserver.MODIFY) {
                                new MethodChannel(getFlutterEngine().getDartExecutor().getBinaryMessenger(), CHANNEL)
                                        .invokeMethod("onFileChange", null);
                            }
                        }
                    });
                }
            };
            fileObserver.startWatching();
        } else {
            result.notImplemented();
        }
    }
    
    private void registerBroadcastReceiver() {
        MethodChannel methodChannel = new MethodChannel(getFlutterEngine().getDartExecutor().getBinaryMessenger(), CHANNEL);
        broadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (action.equals(TProxyTileService.ACTION_TILE_TOGGLED)) {
                    boolean isCoreActive = intent.getBooleanExtra(TProxyTileService.EXTRA_IS_ACTIVE, false);
                    methodChannel.invokeMethod("onTileToggled", isCoreActive);
                }
            }
        };

        registerReceiver(broadcastReceiver, new IntentFilter(TProxyTileService.ACTION_TILE_TOGGLED));
    }
}