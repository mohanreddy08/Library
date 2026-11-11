Library Management System using Java - Web Application & CLI

Requirements:
  JDK 21 or later (Installed on this system at C:\Program Files\Java\jdk-21.0.11)

Quick Start (Windows Scripts):
  run_web.bat                   Launch Web Application (starts server & opens browser at http://localhost:8080)
  run_web.bat demo_data 8080    Launch Web Application with demo dataset on custom port
  compile.bat                   Compile all Java source files into 'out/'
  run.bat                       Run interactive console CLI with data/ directory
  run.bat demo_data             Run interactive console CLI with demo dataset
  test.bat                      Run test suite (41/41 tests)
  benchmark.bat                 Run data structure performance benchmarks

Manual Commands:
  Compile:
    javac -d out src/lms/*.java

  Run Web Application:
    java -cp out lms.WebServer data 8080
    (or with demo data: java -cp out lms.WebServer demo_data 8080)
    Then open http://localhost:8080 in your web browser.

  Run Console CLI:
    java -cp out lms.Main data
    java -cp out lms.Main demo_data

  Tests & Benchmarks:
    java -cp out lms.TestRunner tests_results.json
    java -cp out lms.Benchmark benchmark_results.json

Web Application Features:
  - Responsive Dashboard with real-time KPI metrics (titles, copies, active loans, fines)
  - Book Catalogue with live Trie prefix search, author/category filters, and sorting
  - Member Management with loan history view and fine payment processing
  - Full Circulation Workflow: Issue book (14-day limit), Return book with automatic fine calculation, FIFO Waitlist Reservations
  - Overdue Loans Report (PriorityQueue ordered by due date)
  - Interactive System Calendar simulation (advance date by +1/+7/+14 days to test fine accruals)
  - Transactional Undo functionality (Stack-based undo)
  - One-click Save to Disk persistence

Project Structure:
  src/lms/
    WebServer.java     Embedded HTTP server (com.sun.net.httpserver) & REST API
    Library.java       Core business logic & persistence
    Book.java          Book entity (id, title, author, category, copies)
    Member.java        Member entity with fine tracking
    Transaction.java   Borrowing/return transaction with dates & fines
    Trie.java          Prefix search trie for book titles
    Sorting.java       Merge sort & binary search implementations
    SampleData.java    Default seed data generator
    Main.java          Interactive console menu
    TestRunner.java    Automated unit/integration test suite
    Benchmark.java     Data structure performance benchmark suite
  web/
    index.html         Modern, responsive Single-Page Application (HTML/CSS/JS)
  demo_data/           Sample persistent data files (.txt)
  tests_results.json   JSON output of test runner results
  benchmark_results.json Performance benchmark metrics
