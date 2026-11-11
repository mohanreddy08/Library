package lms;

import java.time.LocalDate;

/** One issue/return record. returnDate is null while the book is still on loan. */
public class Transaction {
    private final int id;
    private final int memberId;
    private final int bookId;
    private final LocalDate issueDate;
    private final LocalDate dueDate;
    private LocalDate returnDate;
    private int fine;

    public Transaction(int id, int memberId, int bookId, LocalDate issueDate, LocalDate dueDate) {
        this.id = id; this.memberId = memberId; this.bookId = bookId;
        this.issueDate = issueDate; this.dueDate = dueDate;
    }

    public int getId() { return id; }
    public int getMemberId() { return memberId; }
    public int getBookId() { return bookId; }
    public LocalDate getIssueDate() { return issueDate; }
    public LocalDate getDueDate() { return dueDate; }
    public LocalDate getReturnDate() { return returnDate; }
    public int getFine() { return fine; }
    void setReturnDate(LocalDate d) { this.returnDate = d; }
    void setFine(int f) { this.fine = f; }
    public boolean isActive() { return returnDate == null; }

    @Override
    public String toString() {
        return String.format("T%d: member %d, book %d, issued %s, due %s, %s", id, memberId, bookId, issueDate, dueDate,
                returnDate == null ? "not returned" : "returned " + returnDate + " (fine Rs. " + fine + ")");
    }
}
