package lms;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

/** Console menu for the Library Management System. */
public class Main {
    private static final Scanner in = new Scanner(System.in);

    private static String ask(String prompt) {
        System.out.print(prompt);
        return in.hasNextLine() ? in.nextLine().trim() : "0";
    }

    private static int askInt(String prompt) {
        while (true) {
            String s = ask(prompt);
            try { return Integer.parseInt(s); }
            catch (NumberFormatException e) { System.out.println("Please enter a whole number."); }
        }
    }

    private static void menu() {
        System.out.println("\n===== LIBRARY MANAGEMENT SYSTEM =====");
        System.out.println(" 1. Add book              2. Remove book           3. Add member");
        System.out.println(" 4. Issue book            5. Return book           6. Reserve book");
        System.out.println(" 7. Search by id          8. Search title prefix   9. Search by author");
        System.out.println("10. List books by title  11. List books by author 12. Overdue report");
        System.out.println("13. Pay fine             14. Undo last action   15. Member loans");
        System.out.println("16. Set today's date     17. Search by category 18. List all members");
        System.out.println("19. System statistics     0. Save and exit");
    }

    public static void main(String[] args) throws IOException {
        Path dir = Paths.get(args.length > 0 ? args[0] : "data");
        Library lib = new Library();
        if (lib.load(dir)) System.out.println("Loaded " + lib.bookCount() + " books and " + lib.memberCount() + " members from " + dir);
        else { lib = SampleData.create(); System.out.println("No saved data found. Sample data created."); }
        boolean running = true;
        while (running) {
            menu();
            int choice = askInt("Enter choice: ");
            try {
                switch (choice) {
                    case 0: lib.save(dir); System.out.println("Data saved. Goodbye."); running = false; break;
                    case 1: {
                        int id = askInt("Book id: "); String t = ask("Title: "); String a = ask("Author: ");
                        String c = ask("Category: "); int n = askInt("Copies: ");
                        System.out.println(lib.addBook(new Book(id, t, a, c, n)) ? "Book added." : "A book with this id already exists.");
                        break; }
                    case 2: System.out.println(lib.removeBook(askInt("Book id: ")) ? "Book removed." : "Cannot remove: not found or copies are on loan."); break;
                    case 3: { int id = askInt("Member id: "); String n = ask("Name: ");
                        System.out.println(lib.addMember(new Member(id, n)) ? "Member added." : "A member with this id already exists."); break; }
                    case 4: { int m = askInt("Member id: "); int b = askInt("Book id: ");
                        Library.IssueResult r = lib.issueBook(m, b);
                        System.out.println(r == Library.IssueResult.ISSUED ? "Issued. Due date: " + lib.getToday().plusDays(Library.LOAN_DAYS) : "Not issued: " + r);
                        break; }
                    case 5: { int m = askInt("Member id: "); int b = askInt("Book id: ");
                        Transaction t = lib.returnBook(m, b);
                        System.out.println(t == null ? "No such active loan." : "Returned. Fine charged: Rs. " + t.getFine());
                        break; }
                    case 6: { int m = askInt("Member id: "); int b = askInt("Book id: ");
                        int r = lib.reserveBook(m, b);
                        if (r > 0) System.out.println("Reserved. Queue position: " + r);
                        else System.out.println(r == Library.RES_AVAILABLE ? "A copy is available; issue it directly."
                                : r == Library.RES_DUPLICATE ? "Already in the queue."
                                : r == Library.RES_HOLDS_COPY ? "Member already holds a copy." : "Member or book not found.");
                        break; }
                    case 7: { Book b = lib.searchById(askInt("Book id: ")); System.out.println(b == null ? "Not found." : b); break; }
                    case 8: print(lib.searchByTitlePrefix(ask("Title starts with: "))); break;
                    case 9: print(lib.searchByAuthor(ask("Author contains: "))); break;
                    case 10: print(lib.booksSortedByTitle()); break;
                    case 11: print(lib.booksSortedByAuthor()); break;
                    case 12: { List<Transaction> l = lib.overdueReport();
                        if (l.isEmpty()) System.out.println("No overdue loans.");
                        for (Transaction t : l) System.out.println(t); break; }
                    case 13: { int m = askInt("Member id: "); int amt = askInt("Amount: ");
                        System.out.println(lib.payFine(m, amt) ? "Fine updated." : "Invalid member or amount."); break; }
                    case 14: { String s = lib.undoLast(); System.out.println(s == null ? "Nothing to undo." : s); break; }
                    case 15: { int m = askInt("Member id: ");
                        if (lib.getMember(m) == null) { System.out.println("Member not found."); break; }
                        System.out.println(lib.getMember(m));
                        for (Transaction t : lib.activeLoans(m)) System.out.println("  " + t); break; }
                    case 16: try { lib.setToday(LocalDate.parse(ask("Date (yyyy-mm-dd): "))); System.out.println("Date set to " + lib.getToday()); }
                        catch (DateTimeParseException e) { System.out.println("Invalid date."); } break;
                    case 17: print(lib.searchByCategory(ask("Category contains: "))); break;
                    case 18: {
                        List<Member> ms = lib.membersSortedById();
                        if (ms.isEmpty()) System.out.println("No members registered.");
                        for (Member m : ms) System.out.println(m);
                        break;
                    }
                    case 19: {
                        Map<String, Object> stats = lib.getStatistics();
                        System.out.println("--- Library Statistics Summary ---");
                        System.out.println("Total Book Titles:    " + stats.get("totalTitles"));
                        System.out.println("Total Physical Copies:" + stats.get("totalCopies"));
                        System.out.println("Available Copies:     " + stats.get("availableCopies"));
                        System.out.println("Issued Copies:        " + stats.get("issuedCopies"));
                        System.out.println("Total Members:        " + stats.get("totalMembers"));
                        System.out.println("Active Loans:         " + stats.get("activeLoans"));
                        System.out.println("Members With Fines:   " + stats.get("membersWithFines"));
                        System.out.println("Total Fines Due:      Rs. " + stats.get("totalFinesDue"));
                        System.out.println("Queued Reservations:  " + stats.get("activeReservations"));
                        break;
                    }
                    default: System.out.println("Invalid choice.");
                }
            } catch (IllegalArgumentException e) {
                System.out.println("Input error: " + e.getMessage());
            }
        }
    }

    private static void print(List<Book> l) {
        if (l.isEmpty()) System.out.println("No matching books.");
        for (Book b : l) System.out.println(b);
    }
}
