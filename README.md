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

Icons in allen Standardgrößen (16–512 px) sind in `src/main/resources/icons/` enthalten und werden automatisch in das JAR gebündelt. Das Fenster-Icon wird über `AppIcon.getIconImages()` gesetzt; Swing wählt dabei automatisch die optimale Auflösung je nach Display.

Um das Icon im Ubuntu-Launcher anzuzeigen, nutze die beiliegende `.desktop`-Vorlage (`src/main/resources/cleanmediamanager.desktop`):

```bash
mkdir -p ~/.local/share/icons/hicolor/256x256/apps
cp src/main/resources/icons/app-icon-256.png \
   ~/.local/share/icons/hicolor/256x256/apps/cleanmediamanager.png

sed "s|/path/to/clean-media-manager.jar|$(pwd)/target/clean-media-manager-1.3.9-SNAPSHOT-uber.jar|g; \
     s|/path/to/app-icon-256.png|$HOME/.local/share/icons/hicolor/256x256/apps/cleanmediamanager.png|g" \
  src/main/resources/cleanmediamanager.desktop \
  > ~/.local/share/applications/cleanmediamanager.desktop
```

Icons neu generieren (nach Designänderungen):

```bash
mvn -q compile exec:java -Dexec.mainClass="com.cleanmediamanager.tools.GenerateIcons"
```


