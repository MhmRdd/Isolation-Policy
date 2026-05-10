package io.github.mhmrdd.isolationpolicy;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class PolicyStore {
    private static final Set<String> sDenied = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private static volatile long sVersion = 0L;

    public static boolean isDenied(String pkg) {
        return pkg != null && sDenied.contains(pkg);
    }

    public static void replace(Set<String> next) {
        sDenied.clear();
        if (next != null) sDenied.addAll(next);
        sVersion++;
    }

    public static Set<String> snapshot() {
        return new HashSet<>(sDenied);
    }

    public static long version() {
        return sVersion;
    }
}
