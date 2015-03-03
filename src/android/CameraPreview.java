/*
 * Copyright 2013 Bruno Carreira - Lucas Farias - Rafael Luna - Vinícius
 * Fonseca. Licensed under the Apache License, Version 2.0 (the "License"); you
 * may not use this file except in compliance with the License. You may obtain a
 * copy of the License at http://www.apache.org/licenses/LICENSE-2.0 Unless
 * required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */

package com.vmbc.customcamera;

import android.app.Activity;
import android.content.Context;
import android.hardware.Camera;
import android.hardware.Camera.CameraInfo;
import android.util.Log;
import android.view.Display;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.WindowManager;

public class CameraPreview extends SurfaceView	implements
												SurfaceHolder.Callback {
	private SurfaceHolder		mHolder;
	private Camera				mCamera;
	private Context	context;
	private static final String	TAG	= "CameraPreview";
	private OrientationEventListener	orientationEventListener;
	private int	currentOrientation;

	public CameraPreview(	Context _context,
							Camera camera) {
		super(_context);
		mCamera = camera;
		this.context = _context;

		mHolder = getHolder();
		mHolder.addCallback(this);
		orientationEventListener = new OrientationEventListener(_context) {
			
			@Override
			public void onOrientationChanged(int orientation) {
				Display display = ((WindowManager) context.getSystemService(Context.WINDOW_SERVICE))
						.getDefaultDisplay();
				if (currentOrientation != display.getRotation()) {
					currentOrientation = display.getRotation();
					refreshPreview();
				}
			}
		};
		mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
	}

	public void surfaceCreated(SurfaceHolder holder) {
		orientationEventListener.enable();
		try {
			mCamera.setPreviewDisplay(holder);
			mCamera.startPreview();
		} catch (Throwable e) {
			Log.d(TAG, "Error setting camera preview: " + e.getMessage());
		}
	}

	public void surfaceDestroyed(SurfaceHolder holder) {
	}

	public void surfaceChanged(SurfaceHolder holder, int format, int width,
								int height) {

		if (mHolder.getSurface() == null) {
			return;
		}

		refreshPreview();
	}

	public void refreshPreview() {
		try {
			mCamera.stopPreview();
		} catch (Throwable e) {
		}

		try {
			int count = Camera.getNumberOfCameras();
			int cameraId = count == 1 ?  CameraInfo.CAMERA_FACING_BACK : CameraInfo.CAMERA_FACING_FRONT;
			android.hardware.Camera.CameraInfo info = new android.hardware.Camera.CameraInfo();
            android.hardware.Camera.getCameraInfo(cameraId, info);
            int rotation = ((Activity) context).getWindowManager().getDefaultDisplay().getRotation();
            int degrees = 0;
            switch (rotation) {
            case Surface.ROTATION_0:
                    degrees = 0;
                    break;
            case Surface.ROTATION_90:
                    degrees = 90;
                    break;
            case Surface.ROTATION_180:
                    degrees = 180;
                    break;
            case Surface.ROTATION_270:
                    degrees = 270;
                    break;
            }

            int result;
            if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                    result = (info.orientation + degrees) % 360;
                    result = (360 - result) % 360; // compensate the mirror
            } else { // back-facing
                    result = (info.orientation - degrees + 360) % 360;
            }
            mCamera.setDisplayOrientation(result);
			
			mCamera.setPreviewDisplay(mHolder);
			mCamera.startPreview();

		} catch (Throwable e) {
			Log.d(TAG, "Error starting camera preview: " + e.getMessage());
		}
	}
}
