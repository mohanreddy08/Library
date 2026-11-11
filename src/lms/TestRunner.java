package lms;

import java.nio.file.*;
import java.time.LocalDate;
import java.util.*;

/** Plain-Java test runner (no external framework). Writes tests_results.json. */
public class TestRunner {
    static final List<String[]> rows = new ArrayList<>();
    static int pass = 0, fail = 0;

    static void check(String id, String scenario, String input, String expected, Object actual, boolean ok) {
        rows.add(new String[]{id, scenario, input, expected, String.valueOf(actual), ok ? "Pass" : "Fail"});
        if (ok) pass++; else fail++;
    }

    static String esc(String s) { return s.replace("\\", "\\\\").replace("\"", "\\\""); }

    public static void main(String[] args) throws Exception {
        LocalDate d0 = LocalDate.of(2026, 1, 1);
        Library lib = SampleData.create(); lib.setToday(d0);

        check("T1", "Add a new book", "id 200, 2 copies", "true; count 16", lib.addBook(new Book(200, "Test Book", "A. Author", "Test", 2)) + "; count " + lib.bookCount(), lib.bookCount() == 16);
        check("T2", "Reject a duplicate book id", "id 200 again", "false", lib.addBook(new Book(200, "Other", "B", "T", 1)), !lib.addBook(new Book(200, "Other", "B", "T", 1)));
        String msg = ""; try { new Book(201, " ", "A", "T", 1); } catch (IllegalArgumentException e) { msg = e.getMessage(); }
        check("T3", "Reject an empty title", "title = blank", "IllegalArgumentException", msg, msg.equals("Title is required"));
        msg = ""; try { new Book(202, "A|B", "A", "T", 1); } catch (IllegalArgumentException e) { msg = e.getMessage(); }
        check("T4", "Reject the separator character in text", "title = A|B", "IllegalArgumentException", msg, !msg.isEmpty());

        Library.IssueResult r = lib.issueBook(1, 105);
        check("T5", "Issue an available book", "member 1, book 105", "ISSUED; copies 0", r + "; copies " + lib.getBook(105).getAvailableCopies(), r == Library.IssueResult.ISSUED && lib.getBook(105).getAvailableCopies() == 0);
        check("T6", "Due date is 14 days after issue", "issued 2026-01-01", "2026-01-15", lib.activeLoans(1).get(0).getDueDate(), lib.activeLoans(1).get(0).getDueDate().equals(d0.plusDays(14)));
        r = lib.issueBook(2, 105);
        check("T7", "Refuse when no copy is available", "member 2, book 105", "NO_COPY_AVAILABLE", r, r == Library.IssueResult.NO_COPY_AVAILABLE);
        r = lib.issueBook(1, 105);
        check("T8", "Refuse a second copy of the same book", "member 1, book 105", "ALREADY_HAS_COPY", r, r == Library.IssueResult.ALREADY_HAS_COPY);
        r = lib.issueBook(99, 101);
        check("T9", "Unknown member", "member 99", "MEMBER_NOT_FOUND", r, r == Library.IssueResult.MEMBER_NOT_FOUND);
        r = lib.issueBook(1, 999);
        check("T10", "Unknown book", "book 999", "BOOK_NOT_FOUND", r, r == Library.IssueResult.BOOK_NOT_FOUND);

        lib.issueBook(3, 101); lib.issueBook(3, 102); lib.issueBook(3, 103);
        r = lib.issueBook(3, 104);
        check("T11", "Borrowing limit of 3 books", "member 3, 4th book", "LIMIT_REACHED", r, r == Library.IssueResult.LIMIT_REACHED);

        int pos1 = lib.reserveBook(2, 105), pos2 = lib.reserveBook(4, 105);
        check("T12", "Reservation queue positions (FIFO)", "members 2 then 4 reserve book 105", "1 and 2", pos1 + " and " + pos2, pos1 == 1 && pos2 == 2);
        check("T13", "Duplicate reservation refused", "member 2 again", "RES_DUPLICATE", lib.reserveBook(2, 105), lib.reserveBook(2, 105) == Library.RES_DUPLICATE);
        check("T14", "Reservation refused when a copy is free", "book 101 has a free copy", "RES_AVAILABLE", lib.reserveBook(5, 101), lib.reserveBook(5, 101) == Library.RES_AVAILABLE);

        lib.setToday(d0.plusDays(10));
        Transaction t = lib.returnBook(1, 105);
        check("T15", "On-time return has no fine", "returned on day 10", "fine 0", "fine " + (t == null ? "null" : t.getFine()), t != null && t.getFine() == 0);
        r = lib.issueBook(5, 105);
        check("T16", "Returned copy is held for the first reservation", "member 5 tries book 105", "RESERVED_FOR_OTHER", r, r == Library.IssueResult.RESERVED_FOR_OTHER);
        r = lib.issueBook(2, 105);
        check("T17", "First in the queue can issue", "member 2, book 105", "ISSUED; queue [4]", r + "; queue " + lib.reservationQueue(105), r == Library.IssueResult.ISSUED && lib.reservationQueue(105).equals(Arrays.asList(4)));

        lib.setToday(d0.plusDays(20));
        List<Transaction> od = lib.overdueReport();
        check("T18", "Overdue report lists late loans, most overdue first", "today = day 20", "member 3 loans (3 items) first",
              od.size() + " items; first member " + (od.isEmpty() ? "-" : od.get(0).getMemberId()), od.size() >= 3 && od.get(0).getMemberId() == 3 && !od.get(0).getDueDate().isAfter(od.get(od.size() - 1).getDueDate()));
        lib.setToday(d0.plusDays(24));
        t = lib.returnBook(3, 101);
        check("T19", "Fine = days late x Rs. 2", "due day 14, returned day 24", "fine 20", "fine " + (t == null ? "null" : t.getFine()), t != null && t.getFine() == 20 && lib.getMember(3).getFineDue() == 20);
        r = lib.issueBook(3, 104);
        check("T20", "Issue blocked while a fine is due", "member 3 owes Rs. 20", "FINE_PENDING", r, r == Library.IssueResult.FINE_PENDING);
        check("T21", "Pay part of a fine", "Rs. 5 of 20", "true; due 15", lib.payFine(3, 5) + "; due " + lib.getMember(3).getFineDue(), lib.getMember(3).getFineDue() == 15);
        check("T22", "Overpaying is refused", "Rs. 100 of 15", "false", lib.payFine(3, 100), !lib.payFine(3, 100));

        String u1 = lib.undoLast();
        check("T23", "Undo the last fine payment", "undo after paying Rs. 5", "fine back to 20", "fine " + lib.getMember(3).getFineDue(), u1 != null && lib.getMember(3).getFineDue() == 20);
        String u = lib.undoLast();
        check("T24", "Undo the last return (fine removed, loan active again)", "undo the return of book 101", "fine 0; 3 active loans", "fine " + lib.getMember(3).getFineDue() + "; " + lib.activeLoans(3).size() + " active loans", u != null && lib.getMember(3).getFineDue() == 0 && lib.activeLoans(3).size() == 3);
        Library fresh = SampleData.create(); fresh.setToday(d0);
        fresh.issueBook(1, 101);
        String u2 = fresh.undoLast();
        check("T25", "Undo the last issue", "issue then undo", "copy returned to shelf; no history", "copies " + fresh.getBook(101).getAvailableCopies() + ", history " + fresh.getHistory().size(), u2 != null && fresh.getBook(101).getAvailableCopies() == 2 && fresh.getHistory().isEmpty());
        check("T26", "Undo with empty stack", "undo on a new library", "null", SampleData.create().undoLast(), SampleData.create().undoLast() == null);

        Library s = SampleData.create();
        List<Book> p = s.searchByTitlePrefix("alg");
        check("T27", "Title prefix search (Trie), case-insensitive", "prefix \"alg\"", "1 book: Algorithms", p.size() + " book(s): " + (p.isEmpty() ? "-" : p.get(0).getTitle()), p.size() == 1 && p.get(0).getTitle().equals("Algorithms"));
        p = s.searchByTitlePrefix("java");
        check("T28", "Title prefix with a punctuation character", "prefix \"Java:\"", "1 book", s.searchByTitlePrefix("Java:").size() + " book(s)", s.searchByTitlePrefix("Java:").size() == 1 && p.size() == 1);
        check("T29", "Prefix with no match", "prefix \"zzz\"", "empty list", s.searchByTitlePrefix("zzz").size() + " items", s.searchByTitlePrefix("zzz").isEmpty());
        check("T30", "Author search (contains)", "\"silberschatz\"", "2 books", s.searchByAuthor("silberschatz").size() + " books", s.searchByAuthor("silberschatz").size() == 2);
        List<Book> sorted = s.booksSortedByTitle();
        boolean ok = true; for (int i = 1; i < sorted.size(); i++) if (sorted.get(i - 1).getTitle().compareToIgnoreCase(sorted.get(i).getTitle()) > 0) ok = false;
        check("T31", "Catalogue sorted by title (merge sort)", "15 sample books", "non-decreasing order", ok ? "ordered" : "not ordered", ok && sorted.size() == 15);
        int idx = Sorting.binarySearchByTitle(sorted, "clean code");
        check("T32", "Binary search by title", "\"clean code\"", "found at a valid index", "index " + idx, idx >= 0 && sorted.get(idx).getTitle().equals("Clean Code"));
        check("T33", "Binary search, missing title", "\"No Such Book\"", "-1", Sorting.binarySearchByTitle(sorted, "No Such Book"), Sorting.binarySearchByTitle(sorted, "No Such Book") == -1);
        List<Integer> nums = new ArrayList<>(Arrays.asList(5, 3, 9, 1, 3, 7, 2));
        Sorting.mergeSort(nums, Comparator.naturalOrder());
        check("T34", "Merge sort on integers with a duplicate", "5,3,9,1,3,7,2", "1,2,3,3,5,7,9", nums, nums.equals(Arrays.asList(1, 2, 3, 3, 5, 7, 9)));

        Library rm = SampleData.create(); rm.setToday(d0); rm.issueBook(1, 101);
        check("T35", "Cannot remove a book that is on loan", "book 101", "false", rm.removeBook(101), !rm.removeBook(101));
        check("T36", "Remove an unborrowed book; prefix search no longer finds it", "book 102", "true; prefix \"data struc\" empty", rm.removeBook(102) + "; " + rm.searchByTitlePrefix("data struc").size() + " found", rm.getBook(102) == null && rm.searchByTitlePrefix("data struc").isEmpty());

        Path tmp = Files.createTempDirectory("lms-test");
        Library a = SampleData.create(); a.setToday(d0); a.issueBook(1, 101); a.issueBook(2, 111); a.reserveBook(3, 111);
        a.setToday(d0.plusDays(20)); a.returnBook(2, 111);
        a.save(tmp);
        Library b = new Library(); boolean loaded = b.load(tmp);
        boolean same = loaded && b.bookCount() == a.bookCount() && b.memberCount() == a.memberCount()
                && b.getHistory().size() == a.getHistory().size() && b.getMember(2).getFineDue() == a.getMember(2).getFineDue()
                && b.reservationQueue(111).equals(a.reservationQueue(111)) && b.activeLoans(1).size() == 1
                && b.getBook(101).getAvailableCopies() == a.getBook(101).getAvailableCopies();
        check("T37", "Save to files and load back", "save, then load into a new object", "same books, members, history, fines, queue", same ? "identical" : "different", same);
        check("T38", "Loading from a folder with no data", "empty folder", "false", new Library().load(Files.createTempDirectory("lms-empty")), !new Library().load(Files.createTempDirectory("lms-empty")));

        Library ext = SampleData.create();
        List<Book> javaBooks = ext.searchByCategory("java");
        check("T39", "Search by category (contains)", "category \"java\"", "3 books", javaBooks.size() + " books", javaBooks.size() == 3);
        List<Member> mList = ext.membersSortedById();
        check("T40", "List all members sorted by id", "6 members", "ids 1 through 6", mList.size() == 6 && mList.get(0).getId() == 1 && mList.get(5).getId() == 6, mList.size() == 6 && mList.get(0).getId() == 1 && mList.get(5).getId() == 6);
        Map<String, Object> stats = ext.getStatistics();
        check("T41", "Library statistics summary", "sample catalogue", "15 titles, 6 members", stats.get("totalTitles") + " titles, " + stats.get("totalMembers") + " members", (Integer) stats.get("totalTitles") == 15 && (Integer) stats.get("totalMembers") == 6);

        StringBuilder sb = new StringBuilder("{\"pass\":" + pass + ",\"fail\":" + fail + ",\"tests\":[");
        for (int i = 0; i < rows.size(); i++) {
            String[] x = rows.get(i);
            sb.append(i > 0 ? "," : "").append("{\"id\":\"").append(esc(x[0])).append("\",\"scenario\":\"").append(esc(x[1])).append("\",\"input\":\"").append(esc(x[2]))
              .append("\",\"expected\":\"").append(esc(x[3])).append("\",\"actual\":\"").append(esc(x[4])).append("\",\"status\":\"").append(x[5]).append("\"}");
        }
        sb.append("]}");
        Files.write(Paths.get(args.length > 0 ? args[0] : "tests_results.json"), sb.toString().getBytes("UTF-8"));
        System.out.println("Tests passed: " + pass + ", failed: " + fail);
        for (String[] x : rows) if (x[5].equals("Fail")) System.out.println("FAILED " + x[0] + " " + x[1] + " -> " + x[4]);
    }
}
