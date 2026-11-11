package lms;

import java.util.*;

/** Hand-written merge sort and binary search used by the catalogue. */
public final class Sorting {
    private Sorting() {}

    public static <T> void mergeSort(List<T> list, Comparator<T> cmp) {
        if (list.size() < 2) return;
        @SuppressWarnings("unchecked")
        T[] a = (T[]) list.toArray();
        @SuppressWarnings("unchecked")
        T[] tmp = (T[]) new Object[a.length];
        sort(a, tmp, 0, a.length - 1, cmp);
        for (int i = 0; i < a.length; i++) list.set(i, a[i]);
    }

    private static <T> void sort(T[] a, T[] tmp, int lo, int hi, Comparator<T> cmp) {
        if (lo >= hi) return;
        int mid = (lo + hi) >>> 1;
        sort(a, tmp, lo, mid, cmp);
        sort(a, tmp, mid + 1, hi, cmp);
        int i = lo, j = mid + 1, k = lo;
        while (i <= mid && j <= hi)
            tmp[k++] = cmp.compare(a[i], a[j]) <= 0 ? a[i++] : a[j++];
        while (i <= mid) tmp[k++] = a[i++];
        while (j <= hi) tmp[k++] = a[j++];
        for (k = lo; k <= hi; k++) a[k] = tmp[k];
    }

    /** Index of the first book whose title equals the key (ignoring case) in a list sorted by title, or -1. */
    public static int binarySearchByTitle(List<Book> sortedByTitle, String title) {
        int lo = 0, hi = sortedByTitle.size() - 1;
        int result = -1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            int c = sortedByTitle.get(mid).getTitle().compareToIgnoreCase(title);
            if (c == 0) {
                result = mid;
                hi = mid - 1; // keep searching to the left for the first occurrence
            } else if (c < 0) {
                lo = mid + 1;
            } else {
                hi = mid - 1;
            }
        }
        return result;
    }
}
