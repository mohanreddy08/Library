package lms;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Core logic of the Library Management System.
 * Data structures: HashMap (id lookup), ArrayList (history), LinkedList used as a Queue (reservations),
 * ArrayDeque used as a Stack (undo), PriorityQueue (overdue report), Trie (title prefix search),
 * merge sort and binary search (sorted listing and title lookup).
 */
public class Library {
    public static final int LOAN_DAYS = 14;
    public static final int MAX_BOOKS = 3;
    public static final int FINE_PER_DAY = 2;

    public enum IssueResult { ISSUED, MEMBER_NOT_FOUND, BOOK_NOT_FOUND, LIMIT_REACHED, FINE_PENDING,
        ALREADY_HAS_COPY, NO_COPY_AVAILABLE, RESERVED_FOR_OTHER }

    public static final int RES_NOT_FOUND = -1, RES_AVAILABLE = -2, RES_DUPLICATE = -3, RES_HOLDS_COPY = -4;

    private enum Type { ISSUE, RETURN, PAYMENT }
    private static class Action {
        final Type type; final Transaction txn; final int queueIndex; // queueIndex >= 0 if a reservation was consumed
        final int memberId, amount;                                   // used by PAYMENT actions
        Action(Type t, Transaction x, int qi) { type = t; txn = x; queueIndex = qi; memberId = 0; amount = 0; }
        Action(int memberId, int amount) { type = Type.PAYMENT; txn = null; queueIndex = -1; this.memberId = memberId; this.amount = amount; }
    }

    private final Map<Integer, Book> books = new HashMap<>();
    private final Map<Integer, Member> members = new HashMap<>();
    private final Map<Integer, List<Transaction>> active = new HashMap<>();
    private final Map<Integer, LinkedList<Integer>> reservations = new HashMap<>();
    private final List<Transaction> history = new ArrayList<>();
    private final Deque<Action> undoStack = new ArrayDeque<>();
    private final Trie titleTrie = new Trie();
    private int nextTxnId = 1;
    private LocalDate today = LocalDate.now();

    // ---------- date control (needed for repeatable tests and demonstrations)
    public void setToday(LocalDate d) { this.today = Objects.requireNonNull(d); }
    public LocalDate getToday() { return today; }

    // ---------- catalogue and members
    public boolean addBook(Book b) {
        if (books.containsKey(b.getId())) return false;
        books.put(b.getId(), b);
        titleTrie.insert(b.getTitle(), b.getId());
        return true;
    }

    /** A book can be removed only when no copy is on loan. */
    public boolean removeBook(int id) {
        Book b = books.get(id);
        if (b == null || b.getAvailableCopies() != b.getTotalCopies()) return false;
        books.remove(id);
        titleTrie.remove(b.getTitle(), id);
        reservations.remove(id);
        return true;
    }

    public boolean addMember(Member m) {
        if (members.containsKey(m.getId())) return false;
        members.put(m.getId(), m);
        return true;
    }

    public Book getBook(int id) { return books.get(id); }
    public Member getMember(int id) { return members.get(id); }
    public int bookCount() { return books.size(); }
    public int memberCount() { return members.size(); }
    public List<Transaction> getHistory() { return Collections.unmodifiableList(history); }

    // ---------- issue / return / reserve / undo
    public IssueResult issueBook(int memberId, int bookId) {
        Member m = members.get(memberId);
        if (m == null) return IssueResult.MEMBER_NOT_FOUND;
        Book b = books.get(bookId);
        if (b == null) return IssueResult.BOOK_NOT_FOUND;
        if (m.getFineDue() > 0) return IssueResult.FINE_PENDING;
        List<Transaction> mine = active.computeIfAbsent(memberId, k -> new ArrayList<>());
        if (mine.size() >= MAX_BOOKS) return IssueResult.LIMIT_REACHED;
        for (Transaction t : mine)
            if (t.getBookId() == bookId) return IssueResult.ALREADY_HAS_COPY;
        if (b.getAvailableCopies() == 0) return IssueResult.NO_COPY_AVAILABLE;

        int consumed = -1;
        LinkedList<Integer> q = reservations.get(bookId);
        if (q != null && !q.isEmpty()) {
            int idx = q.indexOf(memberId);
            if (idx >= 0 && idx < b.getAvailableCopies()) {
                q.remove(idx);
                consumed = idx;
            }
            else if (!(idx < 0 && b.getAvailableCopies() > q.size()))
                return IssueResult.RESERVED_FOR_OTHER;
        }
        Transaction t = new Transaction(nextTxnId++, memberId, bookId,
                                        today, today.plusDays(LOAN_DAYS));
        history.add(t);
        mine.add(t);
        b.setAvailableCopies(b.getAvailableCopies() - 1);
        undoStack.push(new Action(Type.ISSUE, t, consumed));
        return IssueResult.ISSUED;
    }

    /** Returns the completed transaction, or null when the member does not hold the book. */
    public Transaction returnBook(int memberId, int bookId) {
        List<Transaction> mine = active.get(memberId);
        if (mine == null) return null;
        Transaction found = null;
        for (Transaction t : mine) if (t.getBookId() == bookId) { found = t; break; }
        if (found == null) return null;
        long late = ChronoUnit.DAYS.between(found.getDueDate(), today);
        int fine = late > 0 ? (int) late * FINE_PER_DAY : 0;
        found.setReturnDate(today);
        found.setFine(fine);
        mine.remove(found);
        Member m = members.get(memberId);
        m.setFineDue(m.getFineDue() + fine);
        Book b = books.get(bookId);
        b.setAvailableCopies(b.getAvailableCopies() + 1);
        undoStack.push(new Action(Type.RETURN, found, -1));
        return found;
    }

    /** Joins the waiting queue of a book. Returns the 1-based position or a negative RES_* code. */
    public int reserveBook(int memberId, int bookId) {
        Member m = members.get(memberId);
        Book b = books.get(bookId);
        if (m == null || b == null) return RES_NOT_FOUND;
        if (b.getAvailableCopies() > 0) return RES_AVAILABLE;
        for (Transaction t : active.getOrDefault(memberId, Collections.emptyList()))
            if (t.getBookId() == bookId) return RES_HOLDS_COPY;
        LinkedList<Integer> q = reservations.computeIfAbsent(bookId, k -> new LinkedList<>());
        if (q.contains(memberId)) return RES_DUPLICATE;
        q.add(memberId);
        return q.size();
    }

    public List<Integer> reservationQueue(int bookId) {
        return new ArrayList<>(reservations.getOrDefault(bookId, new LinkedList<>()));
    }

    /** Reverses the most recent issue, return or fine payment. Returns a description, or null when there is nothing to undo. */
    public String undoLast() {
        Action a = undoStack.poll();
        if (a == null) return null;
        if (a.type == Type.PAYMENT) {
            Member pm = members.get(a.memberId);
            pm.setFineDue(pm.getFineDue() + a.amount);
            return "Undid payment of Rs. " + a.amount + " by member " + a.memberId;
        }
        Transaction t = a.txn;
        Book b = books.get(t.getBookId());
        Member m = members.get(t.getMemberId());
        if (a.type == Type.ISSUE) {
            history.remove(t);
            active.get(t.getMemberId()).remove(t);
            b.setAvailableCopies(b.getAvailableCopies() + 1);
            if (a.queueIndex >= 0)
                reservations.computeIfAbsent(t.getBookId(), k -> new LinkedList<>())
                            .add(a.queueIndex, t.getMemberId());
            return "Undid ISSUE of book " + t.getBookId() + " to member " + t.getMemberId();
        }
        m.setFineDue(m.getFineDue() - t.getFine());
        t.setFine(0);
        t.setReturnDate(null);
        active.computeIfAbsent(t.getMemberId(), k -> new ArrayList<>()).add(t);
        b.setAvailableCopies(b.getAvailableCopies() - 1);
        return "Undid RETURN of book " + t.getBookId() + " by member " + t.getMemberId();
    }

    public boolean payFine(int memberId, int amount) {
        Member m = members.get(memberId);
        if (m == null || amount <= 0 || amount > m.getFineDue()) return false;
        m.setFineDue(m.getFineDue() - amount);
        undoStack.push(new Action(memberId, amount));
        return true;
    }

    public List<Transaction> activeLoans(int memberId) {
        return new ArrayList<>(active.getOrDefault(memberId, Collections.emptyList()));
    }

    // ---------- searching and sorting
    public Book searchById(int id) { return books.get(id); }

    public List<Book> searchByTitlePrefix(String prefix) {
        List<Book> out = new ArrayList<>();
        for (int id : titleTrie.withPrefix(prefix)) out.add(books.get(id));
        Sorting.mergeSort(out, Comparator.comparing(Book::getTitle, String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    public List<Book> searchByAuthor(String text) {
        List<Book> out = new ArrayList<>();
        String q = text.toLowerCase();
        for (Book b : books.values()) if (b.getAuthor().toLowerCase().contains(q)) out.add(b);
        Sorting.mergeSort(out, Comparator.comparing(Book::getTitle, String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    public List<Book> searchByCategory(String text) {
        List<Book> out = new ArrayList<>();
        String q = text.toLowerCase();
        for (Book b : books.values()) if (b.getCategory().toLowerCase().contains(q)) out.add(b);
        Sorting.mergeSort(out, Comparator.comparing(Book::getTitle, String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    public List<Member> membersSortedById() {
        List<Member> out = new ArrayList<>(members.values());
        out.sort(Comparator.comparingInt(Member::getId));
        return out;
    }

    public List<Member> membersSortedByName() {
        List<Member> out = new ArrayList<>(members.values());
        out.sort(Comparator.comparing(Member::getName, String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new LinkedHashMap<>();
        int totalTitles = books.size();
        int totalCopies = 0;
        int availableCopies = 0;
        for (Book b : books.values()) {
            totalCopies += b.getTotalCopies();
            availableCopies += b.getAvailableCopies();
        }
        int activeLoansCount = 0;
        for (List<Transaction> list : active.values()) {
            activeLoansCount += list.size();
        }
        int totalFineDue = 0;
        int membersWithFines = 0;
        for (Member m : members.values()) {
            if (m.getFineDue() > 0) {
                membersWithFines++;
                totalFineDue += m.getFineDue();
            }
        }
        int totalReservations = 0;
        for (LinkedList<Integer> q : reservations.values()) {
            totalReservations += q.size();
        }
        stats.put("totalTitles", totalTitles);
        stats.put("totalCopies", totalCopies);
        stats.put("availableCopies", availableCopies);
        stats.put("issuedCopies", totalCopies - availableCopies);
        stats.put("totalMembers", members.size());
        stats.put("activeLoans", activeLoansCount);
        stats.put("membersWithFines", membersWithFines);
        stats.put("totalFinesDue", totalFineDue);
        stats.put("activeReservations", totalReservations);
        return stats;
    }

    public List<Book> booksSortedByTitle() {
        List<Book> out = new ArrayList<>(books.values());
        Sorting.mergeSort(out, Comparator.comparing(Book::getTitle, String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    public List<Book> booksSortedByAuthor() {
        List<Book> out = new ArrayList<>(books.values());
        Sorting.mergeSort(out, Comparator.comparing(Book::getAuthor, String.CASE_INSENSITIVE_ORDER));
        return out;
    }

    /** Overdue loans, most overdue first (a PriorityQueue ordered by due date). */
    public List<Transaction> overdueReport() {
        PriorityQueue<Transaction> pq =
                new PriorityQueue<>(Comparator.comparing(Transaction::getDueDate));
        for (List<Transaction> l : active.values())
            for (Transaction t : l) if (t.getDueDate().isBefore(today)) pq.add(t);
        List<Transaction> out = new ArrayList<>();
        while (!pq.isEmpty()) out.add(pq.poll());
        return out;
    }

    // ---------- persistence (pipe-separated text files)
    public void save(Path dir) throws IOException {
        Files.createDirectories(dir);
        List<String> l = new ArrayList<>();
        for (Book b : booksSortedById()) l.add(String.join("|", "" + b.getId(), b.getTitle(), b.getAuthor(), b.getCategory(), "" + b.getTotalCopies(), "" + b.getAvailableCopies()));
        Files.write(dir.resolve("books.txt"), l, StandardCharsets.UTF_8);
        l = new ArrayList<>();
        List<Member> ms = new ArrayList<>(members.values());
        ms.sort(Comparator.comparingInt(Member::getId));
        for (Member m : ms) l.add(m.getId() + "|" + m.getName() + "|" + m.getFineDue());
        Files.write(dir.resolve("members.txt"), l, StandardCharsets.UTF_8);
        l = new ArrayList<>();
        for (Transaction t : history) l.add(String.join("|", "" + t.getId(), "" + t.getMemberId(), "" + t.getBookId(), "" + t.getIssueDate(),
                "" + t.getDueDate(), t.getReturnDate() == null ? "-" : "" + t.getReturnDate(), "" + t.getFine()));
        Files.write(dir.resolve("transactions.txt"), l, StandardCharsets.UTF_8);
        l = new ArrayList<>();
        for (Map.Entry<Integer, LinkedList<Integer>> e : reservations.entrySet())
            if (!e.getValue().isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (int id : e.getValue()) sb.append(sb.length() == 0 ? "" : ",").append(id);
                l.add(e.getKey() + "|" + sb);
            }
        Files.write(dir.resolve("reservations.txt"), l, StandardCharsets.UTF_8);
    }

    public List<Book> booksSortedById() {
        List<Book> out = new ArrayList<>(books.values());
        out.sort(Comparator.comparingInt(Book::getId));
        return out;
    }

    /** Loads the four files if they exist; returns true when books.txt was found. */
    public boolean load(Path dir) throws IOException {
        Path bp = dir.resolve("books.txt");
        if (!Files.exists(bp)) return false;
        for (String s : Files.readAllLines(bp, StandardCharsets.UTF_8)) {
            if (s.isBlank()) continue;
            String[] p = s.split("\\|", -1);
            Book b = new Book(Integer.parseInt(p[0]), p[1], p[2], p[3], Integer.parseInt(p[4]));
            b.setAvailableCopies(Integer.parseInt(p[5]));
            addBook(b);
        }
        Path mp = dir.resolve("members.txt");
        if (Files.exists(mp)) for (String s : Files.readAllLines(mp, StandardCharsets.UTF_8)) {
            if (s.isBlank()) continue;
            String[] p = s.split("\\|", -1);
            Member m = new Member(Integer.parseInt(p[0]), p[1]);
            m.setFineDue(Integer.parseInt(p[2]));
            addMember(m);
        }
        Path tp = dir.resolve("transactions.txt");
        if (Files.exists(tp)) for (String s : Files.readAllLines(tp, StandardCharsets.UTF_8)) {
            if (s.isBlank()) continue;
            String[] p = s.split("\\|", -1);
            Transaction t = new Transaction(Integer.parseInt(p[0]), Integer.parseInt(p[1]), Integer.parseInt(p[2]),
                    LocalDate.parse(p[3]), LocalDate.parse(p[4]));
            if (!p[5].equals("-")) t.setReturnDate(LocalDate.parse(p[5]));
            t.setFine(Integer.parseInt(p[6]));
            history.add(t);
            if (t.isActive()) active.computeIfAbsent(t.getMemberId(), k -> new ArrayList<>()).add(t);
            nextTxnId = Math.max(nextTxnId, t.getId() + 1);
        }
        Path rp = dir.resolve("reservations.txt");
        if (Files.exists(rp)) for (String s : Files.readAllLines(rp, StandardCharsets.UTF_8)) {
            if (s.isBlank()) continue;
            String[] p = s.split("\\|", -1);
            LinkedList<Integer> q = new LinkedList<>();
            if (p.length > 1 && !p[1].isBlank()) {
                for (String id : p[1].split(",")) {
                    if (!id.trim().isEmpty()) q.add(Integer.parseInt(id.trim()));
                }
            }
            reservations.put(Integer.parseInt(p[0]), q);
        }
        return true;
    }
}
