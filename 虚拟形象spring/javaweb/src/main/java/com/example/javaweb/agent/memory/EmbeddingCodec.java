package com.example.javaweb.agent.memory;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * float32 向量与 BLOB 的互转。统一小端序，写入前做 L2 归一化。
 */
public final class EmbeddingCodec {

    private EmbeddingCodec() {
    }

    public static byte[] toBytes(float[] vector) {
        if (vector == null || vector.length == 0) {
            return null;
        }
        ByteBuffer buffer = ByteBuffer.allocate(vector.length * Float.BYTES)
                .order(ByteOrder.LITTLE_ENDIAN);
        for (float value : vector) {
            buffer.putFloat(value);
        }
        return buffer.array();
    }

    public static float[] toVector(byte[] bytes) {
        if (bytes == null || bytes.length < Float.BYTES) {
            return null;
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        float[] vector = new float[bytes.length / Float.BYTES];
        for (int i = 0; i < vector.length; i++) {
            vector[i] = buffer.getFloat();
        }
        return vector;
    }

    public static float[] normalize(float[] vector) {
        if (vector == null || vector.length == 0) {
            return vector;
        }
        double norm = Math.sqrt(dot(vector, vector));
        if (norm <= 1e-9) {
            return vector;
        }
        float[] result = new float[vector.length];
        for (int i = 0; i < vector.length; i++) {
            result[i] = (float) (vector[i] / norm);
        }
        return result;
    }

    public static double cosine(float[] left, byte[] rightBytes) {
        float[] right = toVector(rightBytes);
        if (left == null || right == null || left.length != right.length) {
            return 0;
        }
        double denominator = Math.sqrt(dot(left, left)) * Math.sqrt(dot(right, right));
        if (denominator <= 1e-9) {
            return 0;
        }
        return dot(left, right) / denominator;
    }

    public static double cosine(byte[] leftBytes, byte[] rightBytes) {
        return cosine(toVector(leftBytes), rightBytes);
    }

    private static double dot(float[] left, float[] right) {
        double sum = 0;
        for (int i = 0; i < left.length; i++) {
            sum += left[i] * right[i];
        }
        return sum;
    }
}
