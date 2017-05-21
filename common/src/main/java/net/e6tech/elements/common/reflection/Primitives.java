/*
 * Copyright 2017 Futeh Kao
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.e6tech.elements.common.reflection;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by futeh.
 */
public class Primitives {
    public static Map<String, Class> primitives;

    static {
        primitives =new HashMap<>();
        primitives.put(Boolean.TYPE.getName(),Boolean.TYPE);
        primitives.put(Byte.TYPE.getName(),Byte.TYPE);
        primitives.put(Character.TYPE.getName(),Character.TYPE);
        primitives.put(Double.TYPE.getName(),Double.TYPE);
        primitives.put(Float.TYPE.getName(),Float.TYPE);
        primitives.put(Integer.TYPE.getName(),Integer.TYPE);
        primitives.put(Long.TYPE.getName(),Long.TYPE);
        primitives.put(Short.TYPE.getName(),Short.TYPE);
    }

    public static boolean isPrimitive(String name) {
        return primitives.containsKey(name);
    }

    public static boolean isPrimitive(Class cls) {
        return primitives.containsKey(cls.getName());
    }

    public static Class get(String name) {
        return primitives.get(name);
    }

    public static Object defaultValue(Class cls) {
        if (isPrimitive(cls)) {
            if (cls == Boolean.TYPE) return false;
            else if (cls == Byte.TYPE) return (byte) 0;
            else if (cls == Character.TYPE) return (char) 0;
            else if (cls == Double.TYPE) return 0d;
            else if (cls == Float.TYPE) return 0f;
            else if (cls == Integer.TYPE) return (int) 0;
            else if (cls == Long.TYPE) return 0L;
            else if (cls == Short.TYPE) return (short) 0;
        } else {
            if (cls == Boolean.class) return false;
            else if (cls == Byte.class) return (byte) 0;
            else if (cls == Character.class) return (char) 0;
            else if (cls == Double.class) return 0d;
            else if (cls == Float.class) return 0f;
            else if (cls == Integer.class) return (int) 0;
            else if (cls == Long.class) return 0L;
            else if (cls == Short.class) return (short) 0;
        }
        return null;
    }

    public static int hashCode(Object value) {
        if (value == null) return 0;
        Class cls = value.getClass();
        if (!cls.isArray()) {
            return value.hashCode();
        } else if (cls == boolean[].class) {
            return Arrays.hashCode((boolean[]) value);
        } else if (cls == byte[].class) {
            return Arrays.hashCode((byte[]) value);
        } else if (cls == char[].class) {
            return Arrays.hashCode((char[]) value );
        } else if (cls == double[].class) {
            return Arrays.hashCode((double[]) value);
        } else if (cls == float[].class) {
            return Arrays.hashCode((float[]) value);
        } else if (cls == int[].class) {
            return Arrays.hashCode((int[]) value);
        } else if (cls == long[].class) {
            return Arrays.hashCode((long[]) value);
        } else if (cls == short[].class) {
            return Arrays.hashCode((short[]) value);
        } else {
            return Arrays.hashCode(((Object[]) value));
        }
    }

    public static boolean equals(Object value1, Object value2) {
        if (value1 == null && value2 == null) return true;
        else if (value1 == null || value2 == null) return false;

        Class cls = value1.getClass();
        if(!cls.isArray()) {
            return value1.equals(value2);
        } else if(value2.getClass() != cls) {
            return false;
        } else if(cls == boolean[].class) {
            return Arrays.equals((boolean[]) value1, (boolean[]) value2);
        } else if(cls == byte[].class) {
            return Arrays.equals((byte[]) value1, (byte[]) value2);
        } else if(cls == char[].class) {
            return Arrays.equals((char[]) value1, (char[]) value2);
        } else if(cls == double[].class) {
            return Arrays.equals((double[]) value1, (double[]) value2);
        } else if(cls == float[].class) {
            return Arrays.equals((float[]) value1, (float[]) value2);
        } else if(cls == int[].class) {
            return Arrays.equals((int[]) value1, (int[]) value2);
        } else if(cls == long[].class) {
            return Arrays.equals((long[]) value1, (long[]) value2);
        } else if(cls == short[].class) {
            return Arrays.equals((short[]) value1, (short[]) value2);
        } else {
            return Arrays.equals((Object[])((Object[])value1), (Object[])((Object[])value2));
        }
    }

    public static String toString(Object value) {
        if (value == null) return "null";
        Class cls = value.getClass();
        if (!cls.isArray()) {
            return value.toString();
        } else if (cls == boolean[].class) {
            return Arrays.toString((boolean[]) value);
        } else if (cls == byte[].class) {
            return Arrays.toString((byte[]) value);
        } else if (cls == char[].class) {
            return Arrays.toString((char[]) value );
        } else if (cls == double[].class) {
            return Arrays.toString((double[]) value);
        } else if (cls == float[].class) {
            return Arrays.toString((float[]) value);
        } else if (cls == int[].class) {
            return Arrays.toString((int[]) value);
        } else if (cls == long[].class) {
            return Arrays.toString((long[]) value);
        } else if (cls == short[].class) {
            return Arrays.toString((short[]) value);
        } else {
            return Arrays.toString(((Object[]) value));
        }
    }
}
