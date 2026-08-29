package com.jarvis.mobile.actions;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;

import java.util.Locale;

/** Typed Android flashlight capability with explicit state validation and truthful outcomes. */
public final class AndroidFlashlightActions {
    private final Context context;

    public AndroidFlashlightActions(Context context) {
        this.context = context.getApplicationContext();
    }

    public String setState(String state) {
        String normalized = state == null ? "" : state.trim().toLowerCase(Locale.ROOT);
        final boolean enabled;
        if ("on".equals(normalized)) enabled = true;
        else if ("off".equals(normalized)) enabled = false;
        else return "Tell me whether to turn the flashlight on or off.";

        if (context.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            return "Enable Camera permission so I can control the flashlight.";
        }

        try {
            CameraManager cameras = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
            if (cameras == null) return "Flashlight control is unavailable on this device.";

            String fallback = null;
            for (String cameraId : cameras.getCameraIdList()) {
                CameraCharacteristics characteristics = cameras.getCameraCharacteristics(cameraId);
                Boolean hasFlash = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                if (!Boolean.TRUE.equals(hasFlash)) continue;
                if (fallback == null) fallback = cameraId;
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) {
                    cameras.setTorchMode(cameraId, enabled);
                    return enabled ? "Flashlight on." : "Flashlight off.";
                }
            }
            if (fallback != null) {
                cameras.setTorchMode(fallback, enabled);
                return enabled ? "Flashlight on." : "Flashlight off.";
            }
            return "This device does not expose a controllable flashlight.";
        } catch (SecurityException denied) {
            return "Android blocked flashlight control because Camera permission is off.";
        } catch (Exception unavailable) {
            return "Flashlight control is unavailable right now.";
        }
    }
}
