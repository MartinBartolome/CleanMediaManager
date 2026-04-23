# CleanMediaManager

A Java-based tool to automatically rename and organize media files (movies, TV shows, and anime) using metadata from online databases, with a focus on modular design and extensibility.

## Requirements

- Java 25 or higher
- Maven 3.6+

## Installation and Build

1. Clone the repository:
```bash
git clone <repository-url>
cd CleanMediaManager
```

2. Build the project:
```bash
mvn clean install
```

This compiles the project and runs all tests.

## Running the Application

### Using Maven:
```bash
mvn exec:java -Dexec.mainClass="com.cleanmediamanager.Main"
```

### Using JAR file:
```bash
mvn package
java -jar target/clean-media-manager-1.0-SNAPSHOT.jar
```

## Features

- Automatic renaming of media files based on metadata
- Integration with online databases (TMDB)
- GUI for file management
- Support for movies, TV shows, and anime

## Project Structure

```
src/main/java/com/cleanmediamanager/
├── Main.java                 # Entry point
├── api/                      # API integration (TMDB)
├── core/                     # Core logic
│   ├── FilenameParser.java
│   ├── FileScanner.java
│   ├── FormatService.java
│   ├── MovieMatcher.java
│   └── RenameService.java
├── model/                    # Data models
└── ui/                       # User interface
```

## Application Icon

- **Included asset:** `src/main/resources/icons/app-icon.svg` — a scalable SVG icon used for packaging and documentation.
- **Runtime icon:** The application draws a programmatic icon by default; see `AppIcon` and `MainWindow` for implementation.
- **To provide platform icons:** add PNG files of recommended sizes to `src/main/resources/icons/` with names like `app-icon-16.png`, `app-icon-32.png`, `app-icon-64.png`, and `app-icon-256.png`. The application will prefer those resources when present.

