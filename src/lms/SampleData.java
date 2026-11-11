package lms;

/** Creates the small sample catalogue and member list used for testing and demonstration. */
public final class SampleData {
    private SampleData() {}

    public static Library create() {
        Library lib = new Library();
        String[][] b = {
            {"Introduction to Algorithms", "Cormen, Leiserson, Rivest, Stein", "Algorithms", "2"},
            {"Data Structures and Algorithms in Java", "Goodrich, Tamassia, Goldwasser", "Data Structures", "3"},
            {"Algorithms", "Sedgewick, Wayne", "Algorithms", "2"},
            {"Effective Java", "Joshua Bloch", "Java", "2"},
            {"Clean Code", "Robert C. Martin", "Software Engineering", "1"},
            {"Java: The Complete Reference", "Herbert Schildt", "Java", "3"},
            {"Head First Java", "Kathy Sierra, Bert Bates", "Java", "2"},
            {"The C Programming Language", "Kernighan, Ritchie", "Programming", "2"},
            {"Operating System Concepts", "Silberschatz, Galvin, Gagne", "Operating Systems", "2"},
            {"Database System Concepts", "Silberschatz, Korth, Sudarshan", "Databases", "2"},
            {"Computer Networks", "Andrew S. Tanenbaum", "Networks", "1"},
            {"Design Patterns", "Gamma, Helm, Johnson, Vlissides", "Software Engineering", "1"},
            {"Cracking the Coding Interview", "Gayle Laakmann McDowell", "Interview Preparation", "2"},
            {"Artificial Intelligence: A Modern Approach", "Russell, Norvig", "Artificial Intelligence", "1"},
            {"Discrete Mathematics and Its Applications", "Kenneth H. Rosen", "Mathematics", "2"}
        };
        for (int i = 0; i < b.length; i++) lib.addBook(new Book(101 + i, b[i][0], b[i][1], b[i][2], Integer.parseInt(b[i][3])));
        String[] m = {"Asha", "Ravi", "Kiran", "Meena", "Suresh", "Divya"};
        for (int i = 0; i < m.length; i++) lib.addMember(new Member(1 + i, m[i]));
        return lib;
    }
}
