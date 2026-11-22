package com.digitallife.sensor;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Platform;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;

import java.util.OptionalDouble;

/** 在 NVIDIA 驱动可用时，从 NVML 读取本机 GPU 实测温度。 */
final class NvidiaTemperature {

    private static final int NVML_SUCCESS = 0;
    private static final int NVML_TEMPERATURE_GPU = 0;
    private final Nvml nvml = load();

    OptionalDouble readCelsius() {
        try {
            if (nvml == null || nvml.nvmlInit_v2() != NVML_SUCCESS) {
                return OptionalDouble.empty();
            }
            try {
                IntByReference count = new IntByReference();
                if (nvml.nvmlDeviceGetCount_v2(count) != NVML_SUCCESS) {
                    return OptionalDouble.empty();
                }

                int hottest = 0;
                PointerByReference gpu = new PointerByReference();
                IntByReference degrees = new IntByReference();
                for (int index = 0; index < count.getValue(); index++) {
                    if (nvml.nvmlDeviceGetHandleByIndex_v2(index, gpu) == NVML_SUCCESS
                            && nvml.nvmlDeviceGetTemperature(gpu.getValue(), NVML_TEMPERATURE_GPU, degrees)
                            == NVML_SUCCESS) {
                        hottest = Math.max(hottest, degrees.getValue());
                    }
                }
                return hottest > 0 ? OptionalDouble.of(hottest) : OptionalDouble.empty();
            } finally {
                nvml.nvmlShutdown();
            }
        } catch (LinkageError unavailable) {
            return OptionalDouble.empty();
        }
    }

    private static Nvml load() {
        try {
            return Native.load(Platform.isWindows() ? "nvml" : "nvidia-ml", Nvml.class);
        } catch (LinkageError unavailable) {
            return null;
        }
    }

    public interface Nvml extends Library {
        int nvmlInit_v2();
        int nvmlShutdown();
        int nvmlDeviceGetCount_v2(IntByReference count);
        int nvmlDeviceGetHandleByIndex_v2(int index, PointerByReference device);
        int nvmlDeviceGetTemperature(Pointer device, int sensorType, IntByReference degrees);
    }
}
