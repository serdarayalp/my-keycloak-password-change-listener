# Keycloak Password Change Listener

Ein benutzerdefinierter **Event Listener Provider (SPI)** für Keycloak, der bei einer Passwortänderung durch den Benutzer eine automatische E-Mail-Benachrichtigung versendet.

---

## Inhaltsverzeichnis

- [Überblick](#überblick)
- [Funktionsweise](#funktionsweise)
- [Projekt- und Code-Struktur](#projekt--und-code-struktur)
  - [`pom.xml`](#pomxml)
  - [`PasswordChangeListenerFactory.java`](#passwordchangelistenerfactoryjava)
  - [`PasswordChangeListener.java`](#passwordchangelistenerjava)
  - [SPI-Registrierung (`META-INF/services`)](#spi-registrierung-meta-infservices)
- [Technische Voraussetzungen](#technische-voraussetzungen)
- [Build](#build)
- [Deployment in Keycloak](#deployment-in-keycloak)
- [Konfiguration in Keycloak](#konfiguration-in-keycloak)
- [E-Mail-Templates & Themes](#e-mail-templates--themes)
- [Hinweise & Erweiterungspotenzial](#hinweise--erweiterungspotenzial)

---

## Überblick

Dieses Projekt stellt eine Erweiterung für Keycloak bereit, die auf Ereignisse (Events) innerhalb des Identity & Access Managements reagiert. Wenn ein Benutzer sein Passwort ändert, wird dies erfasst und der Benutzer erhält zur Erhöhung der Kontosicherheit eine Informations-E-Mail über die erfolgte Passwortänderung.

---

## Funktionsweise

1. **Event-Erkennung**: Keycloak feuert bei Benutzerinteraktionen Events. Der Listener überwacht diese und filtert nach dem Ereignistyp `EventType.UPDATE_PASSWORD`.
2. **Benutzer- und Realm-Auflösung**: Anhand der im Event enthaltenen `realmId` und `userId` werden das `RealmModel` und das zugehörige `UserModel` aus der aktuellen `KeycloakSession` geladen.
3. **Validierung**: Es wird geprüft, ob der Benutzer existiert und eine gültige E-Mail-Adresse hinterlegt hat.
4. **E-Mail-Versand**: Über den `EmailTemplateProvider` von Keycloak wird eine E-Mail auf Basis des Templates `password-updated.ftl` und des Betreff-Schlüssels `passwordUpdatedSubject` an den Benutzer gesendet.

---

## Projekt- und Code-Struktur

```text
.
├── pom.xml
└── src
    └── main
        ├── java
        │   └── de
        │       └── mydomain
        │           ├── PasswordChangeListener.java
        │           └── PasswordChangeListenerFactory.java
        └── resources
            └── META-INF
                └── services
                    └── org.keycloak.events.EventListenerProviderFactory
```

### `pom.xml`
- **Maven-Konfiguration**: Definiert das Projekt als JAR-Artefakt (`de.mydomain:my-keycloak-password-change-listener:1.0.0`).
- **Java-Version**: Java 21 (`<maven.compiler.source>21</maven.compiler.source>`, `<maven.compiler.target>21</maven.compiler.target>`).
- **Keycloak-Version**: Keycloak 26.7.3.
- **Abhängigkeiten (Scope `provided`)**:
  - `org.keycloak:keycloak-server-spi`: Schnittstellen und SPI-Definitionen von Keycloak.
  - `org.keycloak:keycloak-server-spi-private`: Private SPI-Erweiterungen.
  - `org.keycloak:keycloak-services`: Kern-Services von Keycloak (inkl. `EmailTemplateProvider`).

### `PasswordChangeListenerFactory.java`
- Implementiert das Interface `org.keycloak.events.EventListenerProviderFactory`.
- Definiert die eindeutige Provider-ID `password-change-listener` (`getId()`), über welche der Listener in Keycloak identifiziert und aktiviert wird.
- Erstellt in der Methode `create(KeycloakSession session)` jeweils eine neue Instanz von `PasswordChangeListener`.
- Stellt Lifecycle-Hooks (`init`, `postInit`, `close`) bereit.

### `PasswordChangeListener.java`
- Implementiert das Interface `org.keycloak.events.EventListenerProvider`.
- Hält eine Referenz auf die aktuelle `KeycloakSession`.
- **`onEvent(Event event)`**:
  - Prüft, ob `event.getType() == EventType.UPDATE_PASSWORD`.
  - Lädt `RealmModel` und `UserModel`.
  - Ruft bei vorhandener E-Mail-Adresse `sendPasswordChangeNotification(realm, user)` auf.
- **`sendPasswordChangeNotification(RealmModel realm, UserModel user)`**:
  - Bezieht den `EmailTemplateProvider` aus der Session.
  - Setzt Realm und User.
  - Befüllt Attribute für das Template (z. B. `email`).
  - Sendet die E-Mail über `emailProvider.send("passwordUpdatedSubject", "password-updated.ftl", attributes)`.
- **`onEvent(AdminEvent adminEvent, boolean includeRepresentation)`**:
  - Schnittstelle für administrative Events (z. B. Passwortänderungen durch einen Administrator). Aktuell als Erweiterungspunkt vorbereitet.
- **`close()`**:
  - Bereinigungsmethode nach Abschluss des Requests/der Session.

### SPI-Registrierung (`META-INF/services`)
- Datei `org.keycloak.events.EventListenerProviderFactory` deklariert den vollqualifizierten Klassennamen:
  ```text
  de.mydomain.PasswordChangeListenerFactory
  ```
- Ermöglicht Keycloak das Auffinden und Laden der Factory via Java `ServiceLoader`.

---

## Technische Voraussetzungen

- **Java JDK**: Version 21 oder höher
- **Apache Maven**: Version 3.8+
- **Keycloak**: Version 26.x (kompatibel mit 26.7.3)

---

## Build

Das Projekt wird mit Maven gebaut:

```bash
mvn clean package
```

Die kompilierte JAR-Datei befindet sich anschließend im Ordner `target/`:
- `target/my-keycloak-password-change-listener-1.0.0.jar`

---

## Deployment in Keycloak

1. Kopieren Sie die generierte JAR-Datei in das `providers/`-Verzeichnis Ihrer Keycloak-Installation:
   ```bash
   cp target/my-keycloak-password-change-listener-1.0.0.jar /opt/keycloak/providers/
   ```
2. Falls Keycloak im optimierten Modus betrieben wird, führen Sie den Build-Schritt aus:
   ```bash
   kc.sh build
   ```
3. Starten Sie den Keycloak-Server neu.

---

## Konfiguration in Keycloak

Damit Keycloak den Listener für einen Realm verwendet, muss er in der Admin Console aktiviert werden:

1. Öffnen Sie die **Keycloak Admin Console**.
2. Wählen Sie den gewünschten **Realm** aus.
3. Navigieren Sie zu **Realm settings** > Reiter **Events** > Unterreiter **Event listeners**.
4. Fügen Sie `password-change-listener` zur Liste der aktiven Event-Listener hinzu.
5. Klicken Sie auf **Save**.
6. Stellen Sie sicher, dass unter **Realm settings** > Reiter **Email** ein funktionsfähiger **SMTP-Server** konfiguriert ist.

---

## E-Mail-Templates & Themes

Der Provider nutzt:
- **Betreff-Schlüssel**: `passwordUpdatedSubject`
- **Template-Datei**: `password-updated.ftl` (HTML & Text-Vorlage)

Stellen Sie sicher, dass im aktiven E-Mail-Theme von Keycloak die Vorlage `password-updated.ftl` (unter `themes/<ihr-theme>/email/html/` und `themes/<ihr-theme>/email/text/`) sowie die Übersetzung für `passwordUpdatedSubject` in den `messages_*.properties` vorhanden sind.

---

## Hinweise & Erweiterungspotenzial

- **Admin-Events**: In `PasswordChangeListener.java` kann die Methode `onEvent(AdminEvent adminEvent, ...)` erweitert werden, um auch Passwort-Resets oder -Änderungen durch Administratoren abzufangen (`ResourceType.USER` und `OperationType.ACTION` bzw. `UPDATE`).
- **Fehlerbehandlung**: Das Logging kann bei Bedarf über den Keycloak/JBoss-Logger (`org.jboss.logging.Logger`) anstelle von `e.printStackTrace()` weiter optimiert werden.
