package net.kdt.pojavlaunch.customcontrols.mouse;

import android.os.Build;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewTreeObserver;

import androidx.annotation.RequiresApi;

import net.kdt.pojavlaunch.MainActivity;
import net.kdt.pojavlaunch.MinecraftGLSurface;
import net.kdt.pojavlaunch.Tools;

import org.lwjgl.glfw.CallbackBridge;

@RequiresApi(api = Build.VERSION_CODES.O)
public class AndroidPointerCapture implements ViewTreeObserver.OnWindowFocusChangeListener, View.OnCapturedPointerListener {

    private static final float TOUCHPAD_SCROLL_THRESHOLD = 1;
    private final AbstractTouchpad mTouchpad;
    private final View mHostView;
    private final float mScaleFactor;
    private final float mMousePrescale = Tools.dpToPx(1);
    private final PointerTracker mPointerTracker = new PointerTracker();
    private final Scroller mScroller = new Scroller(TOUCHPAD_SCROLL_THRESHOLD);
    private final float[] mVector = mPointerTracker.getMotionVector();

    private int mInputDeviceIdentifier = -1; // Initialize with an invalid value
    private boolean mDeviceSupportsRelativeAxis;

    public AndroidPointerCapture(AbstractTouchpad touchpad, View hostView, float scaleFactor) {
        this.mScaleFactor = scaleFactor;
        this.mTouchpad = touchpad;
        this.mHostView = hostView;
        hostView.setOnCapturedPointerListener(this);
        hostView.getViewTreeObserver().addOnWindowFocusChangeListener(this);
    }

    private void enableTouchpadIfNecessary() {
        if (!mTouchpad.getDisplayState()) {
            mTouchpad.enable(true);
        }
    }

    public void handleAutomaticCapture() {
        if (!mHostView.hasWindowFocus()) {
            mHostView.requestFocus();
        } else {
            mHostView.requestPointerCapture();
        }
    }

    @Override
    public boolean onCapturedPointer(View view, MotionEvent event) {
        // Check if the input device has changed and update the device properties if necessary.
        int newIdentifier = event.getDevice().getId();
        if (mInputDeviceIdentifier != newIdentifier) {
            reinitializeDeviceSpecificProperties(event.getDevice());
            mInputDeviceIdentifier = newIdentifier;
        }

        // Handle different input sources based on event source.
        if ((event.getSource() & InputDevice.SOURCE_CLASS_TRACKBALL) != 0) {
            // Trackball or a device reporting as trackball (relative mode).
            if (mDeviceSupportsRelativeAxis) {
                // Use relative axis values if supported.
                mVector[0] = event.getAxisValue(MotionEvent.AXIS_RELATIVE_X);
                mVector[1] = event.getAxisValue(MotionEvent.AXIS_RELATIVE_Y);
            } else {
                // Fallback to absolute coordinates if relative axis is not supported.
                mVector[0] = event.getX();
                mVector[1] = event.getY();
            }
        } else {
            // Touchpad or other non-trackball device (absolute mode).
            mPointerTracker.trackEvent(event);
        }

        // Process mouse events if GLFW is not grabbing the mouse.
        if (!CallbackBridge.isGrabbing()) {
            enableTouchpadIfNecessary();
            // Handle multi-touch scrolling.
            mVector[0] *= mMousePrescale;
            mVector[1] *= mMousePrescale;
            if (event.getPointerCount() < 2) {
                mTouchpad.applyMotionVector(mVector);
                mScroller.resetScrollOvershoot();
            } else {
                mScroller.performScroll(mVector);
            }
        } else {
            // Send mouse position updates to GLFW even if there's no change in input.
            CallbackBridge.mouseX += (mVector[0] * mScaleFactor);
            CallbackBridge.mouseY += (mVector[1] * mScaleFactor);
            CallbackBridge.sendCursorPos(CallbackBridge.mouseX, CallbackBridge.mouseY);
        }

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_MOVE:
                return true;
            case MotionEvent.ACTION_BUTTON_PRESS:
                return MinecraftGLSurface.sendMouseButtonUnconverted(event.getActionButton(), true);
            case MotionEvent.ACTION_BUTTON_RELEASE:
                return MinecraftGLSurface.sendMouseButtonUnconverted(event.getActionButton(), false);
            case MotionEvent.ACTION_SCROLL:
                // Send scroll wheel events to GLFW.
                CallbackBridge.sendScroll(
                        event.getAxisValue(MotionEvent.AXIS_HSCROLL),
                        event.getAxisValue(MotionEvent.AXIS_VSCROLL)
                );
                return true;
            case MotionEvent.ACTION_UP:
                // Stop tracking pointer events when up event occurs.
                mPointerTracker.cancelTracking();
                return true;
            default:
                return false;
        }
    }

    private void reinitializeDeviceSpecificProperties(InputDevice inputDevice) {
        mPointerTracker.cancelTracking();
        boolean relativeXSupported = inputDevice.getMotionRange(MotionEvent.AXIS_RELATIVE_X) != null;
        boolean relativeYSupported = inputDevice.getMotionRange(MotionEvent.AXIS_RELATIVE_Y) != null;
        mDeviceSupportsRelativeAxis = relativeXSupported && relativeYSupported;
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        if (hasFocus && MainActivity.isAndroid8OrHigher()) {
            mHostView.requestPointerCapture();
        }
    }

    public void detach() {
        mHostView.setOnCapturedPointerListener(null);
        mHostView.getViewTreeObserver().removeOnWindowFocusChangeListener(this);
    }
                }
