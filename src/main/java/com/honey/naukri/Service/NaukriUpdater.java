package com.honey.naukri.Service;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.WaitUntilState;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.locks.ReentrantLock;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class NaukriUpdater {
    @Value("${naukri.username}") private String username;
    @Value("${naukri.password}") private String password;
    @Value("${naukri.resume-path}") private String resumePath;
    @Value("${naukri.profile-url}") private String profileUrl;
    @Value("${naukri.headless:true}") private boolean headless;
    // Keep Playwright authentication data in the project's persisted data directory.
    @Value("${naukri.storage-state-path:./data/naukri-storage-state.json}")
    private String storageStatePath;

    @Value("${naukri.selectors.login-email}") private String emailSelector;
    @Value("${naukri.selectors.login-password}") private String passwordSelector;
    @Value("${naukri.selectors.login-submit}") private String submitSelector;
    @Value("${naukri.selectors.resume-input}") private String resumeSelector;

    // Scheduled and manual triggers must share one browser session at a time.
    private final ReentrantLock updateLock = new ReentrantLock();

    public void update() {
        // Serialize updates so concurrent requests cannot cause duplicate logins.
        updateLock.lock();
        try {
            updateWithPersistentSession();
        } finally {
            updateLock.unlock();
        }
    }

    private void updateWithPersistentSession() {
        System.out.println("Updating Naukri profile...");
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw new IllegalStateException("Set NAUKRI_USERNAME and NAUKRI_PASSWORD.");
        }

        try (Playwright playwright = Playwright.create()) {
            try (Browser browser = playwright.chromium().launch(
                    new BrowserType.LaunchOptions().setHeadless(headless))) {
            Path statePath = Paths.get(storageStatePath).toAbsolutePath().normalize();
            BrowserContext context = createContext(browser, statePath);
            Page page = context.newPage();

            // Validate the saved cookies before deciding whether credentials are needed.
            if (!isAuthenticated(page)) {
                context.close();
                deleteState(statePath);
                context = browser.newContext();
                page = context.newPage();
                login(page);
                saveState(context, statePath);
            } else {
                System.out.println("Reusing persisted Naukri session.");
            }

            // Use the authenticated page for the existing profile update workflow.
            final Page updatePage = page;
            navigateToProfile(updatePage);
            updatePage.waitForTimeout(1500);

            /*
             * IMPORTANT:
             * This is the UI-specific part. Naukri may change selectors and page
             * structure. The selectors are configurable in application.properties.
             *
             * We read the existing value and write the exact same value back.
             */

            FileChooser fileChooser = updatePage.waitForFileChooser(
                    () -> updatePage.locator(resumeSelector).click()
            );
            try {
                String path = resumePath == null ? "" : resumePath.trim();
                System.out.println("path"+ path);
                Path resume = Paths.get(path).toAbsolutePath().normalize();
                System.out.println("Resume path: " + resume);
                fileChooser.setFiles(resume);
            } catch (IllegalStateException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalStateException("Unable to attach resume file to the upload dialog.", e);
            }


            System.out.println("✅ Resume uploaded");

//            String current = editor.inputValue();
//            editor.fill(current);
//            page.locator(saveSelector).first().click();

            page.waitForTimeout(1500);
            // Refresh cookies and other session data for the next scheduled run.
            saveState(context, statePath);
            context.close();
            }
        }
    }

    private BrowserContext createContext(Browser browser, Path statePath) {
        if (!Files.isRegularFile(statePath)) {
            // The first run has no saved authentication state.
            return browser.newContext();
        }

        try {
            // Load cookies/local storage from the previous successful run.
            return browser.newContext(new Browser.NewContextOptions()
                    .setStorageStatePath(statePath));
        } catch (PlaywrightException e) {
            // Corrupt state must not prevent a controlled fresh login.
            System.out.println("Persisted Naukri session could not be loaded; creating a fresh session.");
            deleteState(statePath);
            return browser.newContext();
        }
    }

    private boolean isAuthenticated(Page page) {
        try {
            // Naukri redirects expired sessions to its login route.
            navigateToProfile(page);
            String url = page.url().toLowerCase();
            return !url.contains("/nlogin/");
        } catch (PlaywrightException e) {
            return false;
        }
    }

    private void login(Page page) {
        // Only this controlled path submits credentials; reused sessions skip it.
        page.navigate("https://www.naukri.com/nlogin/login",
                new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
        System.out.println("Login Page Loaded");

        Locator emailField = page.locator(emailSelector).first();
        Locator passwordField = page.locator(passwordSelector).first();
        Locator submitButton = page.locator(submitSelector).first();

        try {
            emailField.fill(username);
            passwordField.fill(password);
            submitButton.click();
        } catch (PlaywrightException e) {
            throw new IllegalStateException(
                    "Login form not found at " + page.url() + " (title: " + page.title() + "). "
                            + "Check the configured selectors or whether Naukri displayed a block page.",
                    e);
        }

        page.waitForURL("**/homepage");
        page.waitForTimeout(2500);

        String body = page.locator("body").innerText().toLowerCase();
        // Never attempt to automate CAPTCHA, OTP, or other verification challenges.
        if (body.contains("captcha") || body.contains("verification code")
                || body.contains("otp")) {
            throw new IllegalStateException(
                    "Naukri requested CAPTCHA/OTP/verification. Automation stopped.");
        }
        System.out.println("Login successful; session state will be persisted.");
    }

    private void navigateToProfile(Page page) {
        page.navigate(profileUrl,
                new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
    }

    private void saveState(BrowserContext context, Path statePath) {
        try {
            Path parent = statePath.getParent();
            if (parent != null) {
                // Ensure the project-local data directory exists on a clean install.
                Files.createDirectories(parent);
            }
            // Storage state contains sensitive cookies and must never be logged.
            context.storageState(new BrowserContext.StorageStateOptions().setPath(statePath));
        } catch (IOException | PlaywrightException e) {
            throw new IllegalStateException("Unable to persist the Naukri browser session.", e);
        }
    }

    private void deleteState(Path statePath) {
        try {
            // Remove expired state so the next attempt performs one clean login.
            Files.deleteIfExists(statePath);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to remove the expired Naukri browser session.", e);
        }
    }
}
