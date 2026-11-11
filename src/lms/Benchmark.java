package lms;

import java.nio.file.*;
import java.util.*;

/** Measures the cost of searching and sorting with different data structures. Writes benchmark_results.json. */
public class Benchmark {
    static volatile long sink; // prevents the JIT from removing the work

    interface Job { long run(); }

    static double medianMs(Job j) {
        for (int i = 0; i < 3; i++) sink += j.run();
        double[] t = new double[5];
        for (int i = 0; i < t.length; i++) { long s = System.nanoTime(); sink += j.run(); t[i] = (System.nanoTime() - s) / 1e6; }
        Arrays.sort(t);
        return t[t.length / 2];
    }

    static String word(Random r) {
        int len = 6 + r.nextInt(7);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++) sb.append((char) ('a' + r.nextInt(26)));
        return sb.toString();
    }

    public static void main(String[] args) throws Exception {
        int[] sizes = {1000, 10000, 100000};
        StringBuilder json = new StringBuilder("{\"java\":\"" + System.getProperty("java.version") + "\",\"cores\":" + Runtime.getRuntime().availableProcessors() + ",\"rows\":[");
        for (int si = 0; si < sizes.length; si++) {
            int n = sizes[si];
            Random r = new Random(42);
            List<Book> list = new ArrayList<>();
            Map<Integer, Book> map = new HashMap<>();
            Trie trie = new Trie();
            Set<String> used = new HashSet<>();
            for (int id = 1; id <= n; id++) {
                String t; do { t = word(r); } while (!used.add(t));
                Book b = new Book(id, t, "Author", "Cat", 1);
                list.add(b); map.put(id, b); trie.insert(t, id);
            }
            List<Book> sorted = new ArrayList<>(list);
            Sorting.mergeSort(sorted, Comparator.comparing(Book::getTitle, String.CASE_INSENSITIVE_ORDER));

            int q = 2000;
            int[] ids = new int[q]; String[] titles = new String[q];
            for (int i = 0; i < q; i++) { ids[i] = 1 + r.nextInt(n); titles[i] = list.get(r.nextInt(n)).getTitle(); }
            int pq = 200; String[] prefixes = new String[pq];
            for (int i = 0; i < pq; i++) prefixes[i] = list.get(r.nextInt(n)).getTitle().substring(0, 3);

            double idLinear = medianMs(() -> { long c = 0; for (int id : ids) { for (Book b : list) if (b.getId() == id) { c++; break; } } return c; });
            double idHash = medianMs(() -> { long c = 0; for (int id : ids) if (map.get(id) != null) c++; return c; });
            double tLinear = medianMs(() -> { long c = 0; for (String t : titles) { for (Book b : list) if (b.getTitle().equalsIgnoreCase(t)) { c++; break; } } return c; });
            double tBinary = medianMs(() -> { long c = 0; for (String t : titles) if (Sorting.binarySearchByTitle(sorted, t) >= 0) c++; return c; });
            double pLinear = medianMs(() -> { long c = 0; for (String p : prefixes) for (Book b : list) if (b.getTitle().regionMatches(true, 0, p, 0, p.length())) c++; return c; });
            double pTrie = medianMs(() -> { long c = 0; for (String p : prefixes) c += trie.withPrefix(p).size(); return c; });
            Comparator<Book> cmp = Comparator.comparing(Book::getTitle, String.CASE_INSENSITIVE_ORDER);
            double sMerge = medianMs(() -> { List<Book> c = new ArrayList<>(list); Collections.shuffle(c, new Random(7)); Sorting.mergeSort(c, cmp); return c.get(0).getId(); });
            double sTim = medianMs(() -> { List<Book> c = new ArrayList<>(list); Collections.shuffle(c, new Random(7)); Collections.sort(c, cmp); return c.get(0).getId(); });

            json.append(si > 0 ? "," : "").append(String.format(Locale.ROOT,
                "{\"n\":%d,\"q\":%d,\"pq\":%d,\"idLinear\":%.4f,\"idHash\":%.4f,\"titleLinear\":%.4f,\"titleBinary\":%.4f,\"prefixLinear\":%.4f,\"prefixTrie\":%.4f,\"sortMerge\":%.4f,\"sortTim\":%.4f}",
                n, q, pq, idLinear, idHash, tLinear, tBinary, pLinear, pTrie, sMerge, sTim));
            System.out.printf(Locale.ROOT, "n=%d done%n", n);
        }
        json.append("]}");
        Files.write(Paths.get(args.length > 0 ? args[0] : "benchmark_results.json"), json.toString().getBytes("UTF-8"));
    }
}
