package lms;

/** A book record in the catalogue. */
public class Book {
    private final int id;
    private final String title;
    private final String author;
    private final String category;
    private final int totalCopies;
    private int availableCopies;

    public Book(int id, String title, String author, String category, int totalCopies) {
        if (id <= 0) throw new IllegalArgumentException("Book id must be positive");
        if (title == null || title.trim().isEmpty()) throw new IllegalArgumentException("Title is required");
        if (author == null || author.trim().isEmpty()) throw new IllegalArgumentException("Author is required");
        if (totalCopies <= 0) throw new IllegalArgumentException("Copies must be positive");
        if (title.contains("|") || author.contains("|") || (category != null && category.contains("|")))
            throw new IllegalArgumentException("The character '|' is not allowed");
        this.id = id;
        this.title = title.trim();
        this.author = author.trim();
        this.category = category == null ? "General" : category.trim();
        this.totalCopies = totalCopies;
        this.availableCopies = totalCopies;
    }

    public int getId() { return id; }
    public String getTitle() { return title; }
    public String getAuthor() { return author; }
    public String getCategory() { return category; }
    public int getTotalCopies() { return totalCopies; }
    public int getAvailableCopies() { return availableCopies; }
    void setAvailableCopies(int n) { this.availableCopies = n; }

    @Override
    public String toString() {
        return String.format("[%d] %s by %s (%s) - %d/%d available", id, title, author, category, availableCopies, totalCopies);
    }
}
