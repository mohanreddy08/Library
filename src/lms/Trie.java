package lms;

import java.util.*;

/** Prefix tree over lower-case titles; each word-end node keeps the ids of the books having that title. */
public class Trie {
    private static class Node {
        final Map<Character, Node> next = new HashMap<>();
        final List<Integer> ids = new ArrayList<>();
    }
    private final Node root = new Node();

    public void insert(String key, int id) {
        Node cur = root;
        for (char ch : key.toLowerCase().toCharArray())
            cur = cur.next.computeIfAbsent(ch, k -> new Node());
        cur.ids.add(id);
    }

    public void remove(String key, int id) {
        Node cur = root;
        for (char ch : key.toLowerCase().toCharArray()) {
            cur = cur.next.get(ch);
            if (cur == null) return;
        }
        cur.ids.remove(Integer.valueOf(id));
    }

    /** Returns the ids of every book whose title starts with the prefix. */
    public List<Integer> withPrefix(String prefix) {
        Node cur = root;
        for (char ch : prefix.toLowerCase().toCharArray()) {
            cur = cur.next.get(ch);
            if (cur == null) return new ArrayList<>();
        }
        List<Integer> out = new ArrayList<>();
        Deque<Node> stack = new ArrayDeque<>();
        stack.push(cur);
        while (!stack.isEmpty()) {
            Node n = stack.pop();
            out.addAll(n.ids);
            for (Node c : n.next.values()) stack.push(c);
        }
        return out;
    }
}
