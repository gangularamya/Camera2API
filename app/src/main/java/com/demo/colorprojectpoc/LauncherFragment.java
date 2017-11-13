package com.demo.colorprojectpoc;

import android.Manifest;
import android.content.Context;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.jakewharton.rxbinding.view.RxView;
import com.tbruyelle.rxpermissions.Permission;
import com.tbruyelle.rxpermissions.RxPermissions;
import java.util.Arrays;
import rx.functions.Action0;
import rx.functions.Action1;

/**
 * Created by ramya on 11/10/17.
 */

public class LauncherFragment extends Fragment {
    private static final String TAG = LauncherFragment.class.getSimpleName();
    RxPermissions rxPermissions;
    private TextureView mTextureView;
    private Size previewsize;
    private CaptureRequest.Builder previewBuilder;
    private CameraCaptureSession previewSession;
    private CameraDevice cameraDevice;
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }


    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_launcher, container, false);
        rxPermissions = new RxPermissions(getActivity());
        mTextureView = view.findViewById(R.id.surfaceView);
        mTextureView.setSurfaceTextureListener(textureListener);
        RxView.clicks(view.findViewById(R.id.launch_camera_btn))
                // Ask for permissions when button is clicked
                .compose(rxPermissions.ensureEach(Manifest.permission.CAMERA))
                .subscribe(new Action1<Permission>() {
                               @Override
                               public void call(Permission permission) {
                                   Log.i(TAG, "Permission result " + permission);
                                   if (permission.granted) {
                                       openCamera();
                                   } else if (permission.shouldShowRequestPermissionRationale) {
                                       // Denied permission without ask never again
                                       Toast.makeText(getActivity(),
                                               "Denied permission without ask never again",
                                               Toast.LENGTH_SHORT).show();
                                   } else {
                                       // Denied permission with ask never again
                                       // Need to go to the settings
                                       Toast.makeText(getActivity(),
                                               "Permission denied, can't enable the camera",
                                               Toast.LENGTH_SHORT).show();
                                   }
                               }
                           },
                        new Action1<Throwable>() {
                            @Override
                            public void call(Throwable t) {
                                Log.e(TAG, "onError", t);
                            }
                        },
                        new Action0() {
                            @Override
                            public void call() {
                                Log.i(TAG, "OnComplete");
                            }
                        });
        return view;

    }

    @Override
    public void onResume() {
        super.onResume();
    }

    TextureView.SurfaceTextureListener textureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {

        }
    };

    @SuppressWarnings({"MissingPermission"})
    protected void openCamera() {
        CameraManager manager = (CameraManager) getActivity().getSystemService(Context.CAMERA_SERVICE);
        try {
            String cameraId = manager.getCameraIdList()[0];
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            previewsize = map.getOutputSizes(SurfaceTexture.class)[0];
            manager.openCamera("0", stateCallback, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }


    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice camera) {
            //This is called when the camera is open
            Log.e(TAG, "onOpened");
            cameraDevice = camera;
            createCameraPreview();
        }

        @Override
        public void onDisconnected(CameraDevice camera) {
            cameraDevice.close();
        }

        @Override
        public void onError(CameraDevice camera, int error) {
            cameraDevice.close();
            cameraDevice = null;
        }
    };

    protected void createCameraPreview() {
        SurfaceTexture texture = mTextureView.getSurfaceTexture();
        if (texture != null) {
            texture.setDefaultBufferSize(previewsize.getWidth(), previewsize.getHeight());
            Surface surface = new Surface(texture);
            try {
                previewBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
            previewBuilder.addTarget(surface);
            try {
                cameraDevice.createCaptureSession(Arrays.asList(surface), new CameraCaptureSession.StateCallback() {
                    @Override
                    public void onConfigured(@NonNull CameraCaptureSession session) {
                        previewSession = session;
                        getChangedPreview();

                    }
                    @Override
                    public void onConfigureFailed(@NonNull CameraCaptureSession session) {

                    }
                }, null);
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }

    }

    private void getChangedPreview() {
        if (cameraDevice == null) return;
        previewBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
        HandlerThread thread = new HandlerThread("changed Preview");
        thread.start();
        Handler handler = new Handler(thread.getLooper());
        try {
            previewSession.setRepeatingRequest(previewBuilder.build(), null, handler);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (cameraDevice != null) {
            cameraDevice.close();
        }
    }
}
