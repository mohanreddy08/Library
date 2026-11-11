package lms;

/** A library member. */
public class Member {
    private final int id;
    private final String name;
    private int fineDue;

    public Member(int id, String name) {
        if (id <= 0) throw new IllegalArgumentException("Member id must be positive");
        if (name == null || name.trim().isEmpty()) throw new IllegalArgumentException("Name is required");
        if (name.contains("|")) throw new IllegalArgumentException("The character '|' is not allowed");
        this.id = id;
        this.name = name.trim();
    }

    public int getId() { return id; }
    public String getName() { return name; }
    public int getFineDue() { return fineDue; }
    void setFineDue(int f) { this.fineDue = f; }

    @Override
    public String toString() { return String.format("[%d] %s (fine due: Rs. %d)", id, name, fineDue); }
}
