package com.honey.naukri.Service;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.WaitUntilState;

import java.nio.file.Files;
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
    @Value("${naukri.selectors.resume-save}") private String saveSelector;

    private Path resolveResumePath() {
        String configuredPath = resumePath == null ? "" : resumePath.trim();
        Path[] candidates = new Path[] {
                configuredPath.isEmpty() ? null : Paths.get(configuredPath),
                Paths.get("src/main/resources/Honey_SDE_Resume_1.pdf"),
                Paths.get("target/classes/Honey_SDE_Resume_1.pdf"),
                Paths.get("/app/data/Honey_SDE_Resume_1.pdf")
        };

        for (Path candidate : candidates) {
            if (candidate == null) {
                continue;
            }
            Path normalized = candidate.toAbsolutePath().normalize();
            if (Files.isRegularFile(normalized)) {
                return normalized;
            }
            if (Files.isRegularFile(candidate)) {
                return candidate.toAbsolutePath().normalize();
            }
        }

        throw new IllegalStateException(
                "Resume file not found. Set naukri.resume-path to a valid PDF file. "
                        + "Configured value: '" + resumePath + "'. Checked candidates: "
                        + java.util.Arrays.toString(candidates));
    }

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
            System.out.println("CONTENT: " + page.locator("body").innerText());
            page.screenshot(new Page.ScreenshotOptions()
                .setPath(Paths.get("access-denied.png"))
                .setFullPage(true));

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
//            Locator editor = page.locator(resumeSelector).first();
//            if (editor.count() == 0) {
//
//                throw new IllegalStateException(
//                        "Resume/profile editor not found. Update the selectors.");
//            }
            FileChooser fileChooser = page.waitForFileChooser(
                    () -> page.locator(resumeSelector).click()
            );
            try {
                Path resume = resolveResumePath();
                System.out.println("Resume path: " + resume);
                System.out.println("Absolute path: " + resume.toAbsolutePath());
                System.out.println("Exists: " + Files.exists(resume));
                System.out.println("Is regular file: " + Files.isRegularFile(resume));
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
