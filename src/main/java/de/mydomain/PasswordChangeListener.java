package de.mydomain;

import org.keycloak.events.Event;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.EventType;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.email.EmailException;
import org.keycloak.email.EmailTemplateProvider;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;

import java.util.HashMap;
import java.util.Map;

public class PasswordChangeListener implements EventListenerProvider {

    private final KeycloakSession session;

    public PasswordChangeListener(KeycloakSession session) {
        this.session = session;
    }

    @Override
    public void onEvent(Event event) {
        // Prüfen, ob ein Passwort-Update-Event vorliegt
        if (event.getType() == EventType.UPDATE_PASSWORD) {
            RealmModel realm = session.realms().getRealm(event.getRealmId());
            UserModel user = session.users().getUserById(realm, event.getUserId());

            if (user != null && user.getEmail() != null) {
                sendPasswordChangeNotification(realm, user);
            }
        }
    }

    private void sendPasswordChangeNotification(RealmModel realm, UserModel user) {
        try {
            EmailTemplateProvider emailProvider = session.getProvider(EmailTemplateProvider.class);
            emailProvider.setRealm(realm);
            emailProvider.setUser(user);

            // Parameter für E-Mail-Templates vorbereiten
            Map<String, Object> attributes = new HashMap<>();

            attributes.put("email", user.getEmail());

            // Versendet die Mail unter Nutzung der konfigurierten SMTP-Einstellungen
            // (Subject-Key, Template-Name für HTML/Text, Attribute)
            emailProvider.send("passwordUpdatedSubject", "password-updated.ftl", attributes);

        } catch (EmailException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onEvent(AdminEvent adminEvent, boolean includeRepresentation) {
        // Wird aufgerufen, wenn der Admin das Passwort im Admin-Interface ändert
    }

    @Override
    public void close() {
        // Aufräumarbeiten, falls notwendig
    }
}