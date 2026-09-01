package com.honey.naukri.Service;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.WaitUntilState;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class NaukriUpdater {
    @Value("${naukri.username}") private String username;
    @Value("${naukri.password}") private String password;
    @Value("${naukri.resume-path}") private String resumePath;
    @Value("${naukri.profile-url}") private String profileUrl;
    @Value("${naukri.headless:true}") private boolean headless;

    @Value("${naukri.selectors.login-email}") private String emailSelector;
    @Value("${naukri.selectors.login-password}") private String passwordSelector;
    @Value("${naukri.selectors.login-submit}") private String submitSelector;
    @Value("${naukri.selectors.resume-input}") private String resumeSelector;



    public void update() {
        System.out.println("Updating Naukri profile...");
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw new IllegalStateException("Set NAUKRI_USERNAME and NAUKRI_PASSWORD.");
        }

        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(
                    new BrowserType.LaunchOptions().setHeadless(headless));
            BrowserContext context = browser.newContext();
            Page page = context.newPage();

            page.navigate("https://www.naukri.com/nlogin/login",
                    new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
            System.out.println("Login Page Loaded");

            System.out.println("URL: " + page.url());
            System.out.println("TITLE: " + page.title());

            Locator emailField = page.locator(emailSelector)
                    .first();
            Locator passwordField = page.locator(passwordSelector)
                    .first();
            Locator submitButton = page.locator(submitSelector)
                    .first();

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

            System.out.println("Login successful!");
            page.waitForTimeout(2500);

            // Never attempt to bypass CAPTCHA/OTP/MFA. If one appears, fail safely.
            String body = page.locator("body").innerText().toLowerCase();
            if (body.contains("captcha") || body.contains("verification code")
                    || body.contains("otp")) {
                throw new IllegalStateException(
                        "Naukri requested CAPTCHA/OTP/verification. Automation stopped.");
            }

            page.navigate(profileUrl,
                    new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
            page.waitForTimeout(1500);

            /*
             * IMPORTANT:
             * This is the UI-specific part. Naukri may change selectors and page
             * structure. The selectors are configurable in application.properties.
             *
             * We read the existing value and write the exact same value back.
             */

            FileChooser fileChooser = page.waitForFileChooser(
                    () -> page.locator(resumeSelector).click()
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
            browser.close();
        }
    }
}
