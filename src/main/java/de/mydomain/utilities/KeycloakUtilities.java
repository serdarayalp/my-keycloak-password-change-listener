package de.mydomain.utilities;

import jakarta.ws.rs.core.UriBuilder;
import org.apache.commons.lang3.StringUtils;
import org.keycloak.authentication.actiontoken.execactions.ExecuteActionsActionToken;
import org.keycloak.authentication.actiontoken.resetcred.ResetCredentialsActionToken;
import org.keycloak.authentication.actiontoken.verifyemail.VerifyEmailActionToken;
import org.keycloak.email.EmailException;
import org.keycloak.email.EmailTemplateProvider;
import org.keycloak.email.freemarker.FreeMarkerEmailTemplateProvider;
import org.keycloak.events.Event;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.ThemeManager;
import org.keycloak.models.UserModel;
import org.keycloak.theme.Theme;
import org.keycloak.theme.beans.LinkExpirationFormatterMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class KeycloakUtilities {

    public static Logger logger = LoggerFactory.getLogger(KeycloakUtilities.class);

    public static final String EXECUTE_ACTIONS_SUBJECT = "executeActionsSubject";
    public static final String EXECUTE_ACTIONS_TEMPLATE = "executeActions.ftl";

    public static final String EMAIL_VERIFICATION_SUBJECT = "emailVerificationSubject";
    public static final String EMAIL_VERIFICATION_TEMPLATE = "email-verification.ftl";

    public static final String E_MAIL_REGEX = "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$";

    public static UriBuilder getUriBuilderForActionTokens(KeycloakSession keycloakSession, RealmModel realmModel, String serializedToken) {
        return keycloakSession.getContext().getUri().getBaseUriBuilder()
                .path("realms")
                .path(realmModel.getName())
                .path("login-actions")
                .path("action-token")
                .queryParam("key", serializedToken); // Der serialisierte Token als Query-Parameter "key"
    }

    public static int getActionTokenGeneratedByUserLifespanInSeconds(RealmModel realmModel) {
        return realmModel.getActionTokenGeneratedByUserLifespan(ResetCredentialsActionToken.TOKEN_TYPE);
    }

    public static int getExpirationInSeconds(RealmModel realmModel) {
        return (int) (System.currentTimeMillis() / 1000) + getActionTokenGeneratedByUserLifespanInSeconds(realmModel);
    }

    public static int getActionTokenGeneratedByUserLifespanInMinutes(RealmModel realmModel) {
        return getActionTokenGeneratedByUserLifespanInSeconds(realmModel) / 60;
    }

    public static Theme getTheme(RealmModel realmModel, KeycloakSession keycloakSession) {
        ThemeManager themeManager = keycloakSession.theme();
        try {
            return themeManager.getTheme(realmModel.getEmailTheme(), Theme.Type.EMAIL);
        } catch (IOException e) {
            logger.error("Fehler beim Abrufen des E-Mail-Templates", e);
        }
        return null;
    }

    public static void sendVerifyEmail(KeycloakSession keycloakSession, RealmModel realmModel, UserModel userModel) throws EmailException {

        // Action Token erstellen
        VerifyEmailActionToken verifyActionToken = new VerifyEmailActionToken(
                userModel.getId(),
                getExpirationInSeconds(realmModel),
                null,
                userModel.getEmail(),
                null
        );

        // Token serialisieren
        // Keycloak serialisiert den Token-Inhalt in einen sicheren JWT-String.
        String serializedToken = verifyActionToken.serialize(
                keycloakSession,
                realmModel,
                keycloakSession.getContext().getUri()
        );

        // UriBuilder verwenden, um den Basis-URL und den Pfad zu konstruieren
        UriBuilder uriBuilder = getUriBuilderForActionTokens(keycloakSession, realmModel, serializedToken);
        // den finalen Link erstellen
        String link = uriBuilder.build(realmModel.getName()).toString();

        try {

            Theme theme = getTheme(realmModel, keycloakSession);
            if (theme != null) {
                Locale locale = getLocale(keycloakSession, userModel);

                Map<String, Object> attributes = new HashMap<>();

                attributes.put("link", link);
                attributes.put("linkExpiration", getActionTokenGeneratedByUserLifespanInMinutes(realmModel));
                attributes.put("realmName", realmModel.getName());

                addLinkExpirationFormatter(attributes, theme, locale);

                EmailTemplateProvider emailTemplateProvider = getEmailTemplateProvider(keycloakSession, realmModel, userModel);
                emailTemplateProvider.send(EMAIL_VERIFICATION_SUBJECT, EMAIL_VERIFICATION_TEMPLATE, attributes);
            } else {
                throw new EmailException("Kein Theme gefunden");
            }

        } catch (IOException | EmailException e) {
            logger.error("Fehler beim Auslesen des E-Mail-Templates: ", e);
        }
    }

    public static void addLinkExpirationFormatter(Map<String, Object> attributes, Theme theme, Locale locale) throws IOException {
        attributes.put("linkExpirationFormatter", new LinkExpirationFormatterMethod(theme.getMessages(locale), locale));
    }

    private static EmailTemplateProvider getEmailTemplateProvider(KeycloakSession keycloakSession, RealmModel realmModel, UserModel userModel) {
        EmailTemplateProvider emailTemplateProvider = keycloakSession.getProvider(EmailTemplateProvider.class);

        emailTemplateProvider.setRealm(realmModel);
        emailTemplateProvider.setUser(userModel);
        return emailTemplateProvider;
    }

    public static void sendExecuteActionsMail(KeycloakSession keycloakSession, List<String> requiredActions, UserModel userModel) {

        final RealmModel realmModel = keycloakSession.getContext().getRealm();

        try {

            ExecuteActionsActionToken executeActionsActionToken = new ExecuteActionsActionToken(
                    userModel.getId(),
                    userModel.getEmail(),
                    getExpirationInSeconds(realmModel),
                    requiredActions,
                    null,
                    null
            );

            // Token serialisieren
            // Keycloak serialisiert den Token-Inhalt in einen sicheren JWT-String.
            String serializedToken = executeActionsActionToken.serialize(
                    keycloakSession,
                    realmModel,
                    keycloakSession.getContext().getUri()
            );

            // UriBuilder verwenden, um den Basis-URL und den Pfad zu konstruieren
            UriBuilder uriBuilder = getUriBuilderForActionTokens(keycloakSession, realmModel, serializedToken);
            // den finalen Link erstellen
            String link = uriBuilder.build(realmModel.getName()).toString();

            Theme theme = getTheme(realmModel, keycloakSession);
            if (theme != null) {
                Locale locale = getLocale(keycloakSession, userModel);

                Map<String, Object> attributes = new HashMap<>();

                attributes.put("link", link);
                attributes.put("requiredActions", requiredActions);
                attributes.put("linkExpiration", getActionTokenGeneratedByUserLifespanInMinutes(realmModel));
                attributes.put("realmName", realmModel.getName());

                addLinkExpirationFormatter(attributes, theme, locale);

                EmailTemplateProvider emailTemplateProvider = getEmailTemplateProvider(keycloakSession, realmModel, userModel);
                emailTemplateProvider.send(EXECUTE_ACTIONS_SUBJECT, EXECUTE_ACTIONS_TEMPLATE, attributes);
            } else {
                throw new EmailException("Kein Theme gefunden");
            }
        } catch (Exception e) {
            logger.error("Fehler beim Senden der E-Mail: ", e);
        }
    }

    public static Locale getLocale(KeycloakSession keycloakSession, UserModel userModel) {
        return keycloakSession.getContext().resolveLocale(userModel);
    }

    public static boolean isEmail(String parameter) {
        if (StringUtils.isBlank(parameter)) {
            return false;
        }
        return parameter.matches(E_MAIL_REGEX);
    }

    public static void sendEmail(KeycloakSession keycloakSession, RealmModel realmModel, UserModel userModel, String subjectKey, String templateName) {
        FreeMarkerEmailTemplateProvider freeMarkerEmailTemplateProvider = new FreeMarkerEmailTemplateProvider(keycloakSession);

        freeMarkerEmailTemplateProvider.setRealm(realmModel);
        freeMarkerEmailTemplateProvider.setUser(userModel);

        try {
            freeMarkerEmailTemplateProvider.send(subjectKey, templateName, new HashMap<>());
        } catch (EmailException e) {
            logger.error("Fehler beim Senden der E-Mail: " + templateName, e);
        }
    }

    public static void removeUserSessionsOnEvent(KeycloakSession keycloakSession, Event event) {
        final RealmModel realmModel = keycloakSession.getContext().getRealm();
        final UserModel userModel = keycloakSession.users().getUserById(realmModel, event.getUserId());

        keycloakSession.sessions().removeUserSessions(realmModel, userModel);
    }
}
