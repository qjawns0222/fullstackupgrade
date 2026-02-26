import { chromium } from 'playwright';
import * as fs from 'fs';
import * as path from 'path';
import * as dotenv from 'dotenv';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);

// Load environment variables from .env.local
dotenv.config({ path: path.join(__dirname, '../.env.local') });

async function postToTistory() {
    const tistoryId = process.env.TISTORY_ID;
    const tistoryPassword = process.env.TISTORY_PASSWORD;

    if (!tistoryId || !tistoryPassword) {
        console.error('Error: TISTORY_ID and TISTORY_PASSWORD must be set in .env.local');
        process.exit(1);
    }

    const draftPath = path.join(__dirname, '../../blog_draft.md');
    if (!fs.existsSync(draftPath)) {
        console.error(`Error: Draft file not found at ${draftPath}`);
        process.exit(1);
    }

    const draftContent = fs.readFileSync(draftPath, 'utf-8');
    // Parsing: find first non-empty line as title, rest is content
    const lines = draftContent.split('\n');
    let title = 'New Blog Post';
    let content = draftContent;

    const firstLineIdx = lines.findIndex(line => line.trim() !== '');
    if (firstLineIdx !== -1) {
        title = lines[firstLineIdx].replace(/^#+\s*/, '').trim();
        content = lines.slice(firstLineIdx + 1).join('\n').trim();
    }

    console.log(`Starting Tistory posting...`);
    console.log(`Title: ${title}`);

    const browser = await chromium.launch({ headless: false }); // Headless: false to solve captchas if needed
    const context = await browser.newContext();
    const page = await context.newPage();

    try {
        // 1. Login
        console.log('Navigating to Tistory login...');
        await page.goto('https://www.tistory.com/auth/login');

        // Click 'Login with Kakao' (Assuming most users are on Kakao login now)
        // The selector might change, but usually it's a link with 'Kakao' text or specific class.
        // Try to find the Kakao login button.
        const kakaoLoginBtn = page.locator('.btn_login.link_kakao_id'); // Common selector, needs verification
        if (await kakaoLoginBtn.count() > 0) {
            await kakaoLoginBtn.click();
        } else {
            // Fallback: Check if we are already on a Kakao login page or just click the generic 'login' button
            // Sometimes it redirects immediately.
            console.log('Kakao login button not found immediately, checking page state...');
        }

        // Wait for the actual login form (Kakao or Tistory)
        await page.waitForLoadState('networkidle');

        // Check if we are on Kakao login page
        if (page.url().includes('accounts.kakao.com')) {
            console.log('On Kakao login page. Entering credentials...');
            await page.fill('#loginId--1', tistoryId);
            await page.fill('#password--2', tistoryPassword);
            await page.click('.btn_g.highlight.submit');
        } else {
            // Assume Tistory Native Login (less common now but exists)
            console.log('On Tistory native login page. Entering credentials...');
            await page.fill('#loginId', tistoryId);
            await page.fill('#loginPw', tistoryPassword);
            await page.click('.btn_login');
        }

        // Wait for login to complete (redirect to Tistory main)
        await page.waitForURL('https://www.tistory.com/', { timeout: 30000 });
        console.log('Login successful.');

        // 2. Go to Write Page
        console.log('Navigating to Write page...');

        // Strategy: Use explicit URL from env or look for the specific "Write" button in the blog list
        await page.waitForTimeout(2000); // Wait for potential redirects

        let writeUrl = process.env.TISTORY_WRITE_URL || '';

        if (!writeUrl) {
            // Try finding a direct "Write" button first (Global header)
            const headerWriteBtn = page.locator('a[href*="/manage/newpost"], a[href*="/manage/post"]');
            if (await headerWriteBtn.count() > 0 && await headerWriteBtn.first().isVisible()) {
                console.log('Found write button in header.');
                writeUrl = await headerWriteBtn.first().getAttribute('href') || '';
            }
        }

        if (!writeUrl || writeUrl === '#') {
            // Fallback: Find the user's blog URL from the list
            console.log('Searching for blog URL...');
            const blogLinks = page.locator('a[href*=".tistory.com"]');
            const count = await blogLinks.count();

            for (let i = 0; i < count; i++) {
                const href = await blogLinks.nth(i).getAttribute('href');
                if (href && !href.includes('www.tistory.com') && !href.includes('auth') && !href.includes('kakao') && !href.includes('notice.tistory.com')) {
                    // Found a likely blog URL (e.g., https://myblog.tistory.com)
                    // Construct write URL
                    writeUrl = href.replace(/\/$/, '') + '/manage/newpost';
                    console.log(`Found blog URL: ${href}, Target Write URL: ${writeUrl}`);
                    break;
                }
            }
        }

        if (writeUrl) {
            console.log(`Navigating to Write URL: ${writeUrl}`);
            await page.goto(writeUrl);
        } else {
            console.log('Could not determine Write URL automatically. Trying default button click...');
            const writeButton = page.locator('.btn_write').first();
            if (await writeButton.isVisible()) {
                await writeButton.click();
            } else {
                throw new Error('Could not find any way to navigate to Write page.');
            }
        }

        await page.waitForLoadState('domcontentloaded');

        // Handle "New Editor" (Standard Tistory Editor)
        // Use 'markdown' mode if possible, but default is usually WYSIWYG
        // We will paste as raw text or HTML.
        // Tistory editor often has a 'Markdown' mode switch.

        console.log('Waiting for editor...');
        // Check for popup or overlay
        try {
            await page.locator('.btn_close.close_modal').click({ timeout: 2000 }); // Close any popups
        } catch (e) { }

        // Title Selector Strategy for New Editor
        try {
            await page.waitForSelector('textarea[placeholder*="제목"], .textarea_tit, #post-title', { timeout: 5000 });
            const titleInput = page.locator('textarea[placeholder*="제목"], .textarea_tit, #post-title').first();
            await titleInput.fill(title);
        } catch (e) {
            console.log('Could not find title input standard selectors. Trying to click generic position...');
            await page.mouse.click(100, 200); // Blind click fallback
            await page.keyboard.type(title);
        }
        console.log('Title entered.');

        // 3. Content Insertion
        console.log('Attempting to switch to Markdown mode...');

        // Wait for the editor toolbar to load
        try {
            await page.waitForSelector('#editor-mode-layer-btn-open', { timeout: 5000 });
        } catch (e) {
            console.log('Editor toolbar not found or timed out');
        }

        let modeSwitched = false;

        try {
            // Click "Basic Mode" button
            await page.click('#editor-mode-layer-btn-open');

            // Wait for dropdown and click "Markdown"
            const markdownBtn = page.locator('#editor-mode-markdown');
            if (await markdownBtn.isVisible()) {

                // Setup dialog handler before clicking
                page.once('dialog', async dialog => {
                    console.log(`Dialog message: ${dialog.message()}`);
                    await dialog.accept();
                });

                await markdownBtn.click();
                modeSwitched = true;
                console.log('Clicked Markdown button.');

                // Wait for CodeMirror to appear
                // Use specific selector for Markdown editor container
                const markdownEditorSelector = '#markdown-editor-container .CodeMirror';
                await page.waitForSelector(markdownEditorSelector, { state: 'visible', timeout: 10000 });
                console.log('Markdown editor (CodeMirror) loaded.');

            } else {
                console.log('Markdown option (#editor-mode-markdown) not visible.');
            }

        } catch (e) {
            console.log('Error switching to Markdown mode:', e);
        }

        if (modeSwitched) {
            try {
                // Focus specific CodeMirror for Markdown
                const markdownScroll = page.locator('#markdown-editor-container .CodeMirror-scroll');

                if (await markdownScroll.count() > 0 && await markdownScroll.isVisible()) {
                    await markdownScroll.click();
                    await page.waitForTimeout(500);

                    // Use evaluate to set content directly to CodeMirror instance
                    // This avoids issues with typing speed, newlines, and auto-formatting
                    await page.evaluate((text) => {
                        const cmDom = document.querySelector('#markdown-editor-container .CodeMirror') as any;
                        if (cmDom && cmDom.CodeMirror) {
                            cmDom.CodeMirror.setValue(text);
                            cmDom.CodeMirror.save(); // Ensure it syncs to textarea just in case
                            // Force a change event just in case
                            cmDom.CodeMirror.refresh();
                        } else {
                            throw new Error('CodeMirror instance not found on DOM element');
                        }
                    }, content);

                    console.log('Content set via CodeMirror.setValue().');

                    // Trigger input events by typing a space and backspace
                    // This creates a "user action" that frameworks often listen to
                    await page.keyboard.press('Space');
                    await page.keyboard.press('Backspace');
                    console.log('Triggered input events.');

                } else {
                    throw new Error('Markdown editor scroll container not found or not visible');
                }

            } catch (e) {
                console.log('Error typing in Markdown mode:', e);
            }
        } else {
            console.log('Failed to switch to Markdown mode. Fallback: Formatting code blocks for WYSIWYG.');

            // Fallback to WYSIWYG - Selector from layer.html seems to be iframe '#editor-tistory_ifr' 
            // BUT layer.html also shows <textarea id="editor-tistory" ... style="display: none;"> which suggests
            // we might be in a state where WYSIWYG is an iframe.

            const iframe = page.frameLocator('#editor-tistory_ifr');
            const editorBody = iframe.locator('body#tinymce');

            if (await editorBody.count() > 0) {
                await editorBody.click();
                await page.keyboard.type(content);
            } else {
                console.log('WYSIWYG iframe body not found. Trying generic contenteditable.');
                const editorDiv = page.locator('[contenteditable="true"]');
                if (await editorDiv.count() > 0) {
                    await editorDiv.first().click();
                    await page.keyboard.type(content);
                } else {
                    // Last resort: Tab from title?
                    await page.keyboard.press('Tab');
                    await page.keyboard.type(content);
                }
            }
        }

        console.log('Content inserted.');

        // 4. Publish
        console.log('Publishing...');

        // Click "Complete" (opens the settings layer)
        await page.click('#publish-layer-btn');
        await page.waitForTimeout(2000); // Wait for animation
        console.log('Publish layer opened. Saving debug info...');

        // DEBUG: Save page HTML to inspect selectors
        try {
            // Dump full content to be safe
            const pageHtml = await page.content();
            fs.writeFileSync(path.join(__dirname, 'layer.html'), pageHtml);
            console.log('Saved page HTML to layer.html');
        } catch (e) {
            console.log('Could not save page HTML', e);
        }

        // Select "Public" (공개)
        try {
            // 1. Try finding the label for 'public' logic
            // Found from logs: label for="open20"
            const publicLabel = page.locator('label').filter({ hasText: '공개' }).first();
            if (await publicLabel.count() > 0) {
                console.log('Found Public label with text "공개". Forcing click...');
                await publicLabel.click({ force: true });

                // Also try clicking the input associated if possible
                const publicRadio = page.locator('input[type="radio"][id*="open"][value="3"]');
                if (await publicRadio.count() > 0) {
                    await publicRadio.dispatchEvent('click');
                }
                const publicInput2 = page.locator('input[type="radio"][id*="public"]'); // Another guess
                if (await publicInput2.count() > 0) {
                    await publicInput2.first().dispatchEvent('click');
                }

            } else {
                console.log('Public label "공개" not found. Trying generic visibility radio...');
                await page.locator('input[type="radio"][value="3"]').dispatchEvent('click');
            }
        } catch (e) {
            console.log('Error selecting Public option:', e);
        }

        // Wait for the final publish button to be interactable
        const finalPublishBtn = page.locator('#publish-btn');
        await finalPublishBtn.waitFor({ state: 'visible', timeout: 5000 });

        // Take screenshot of settings layer
        await page.screenshot({ path: 'publish-layer.png' });

        // Click Final "Publish"
        await finalPublishBtn.click();
        console.log('Clicked final publish button. Waiting for completion...');

        // Wait for navigation (successful publish usually redirects to the post)
        // or wait for specific success network response
        try {
            // Wait for URL to not contain 'newpost' or 'manage'
            await page.waitForURL((url) => {
                return !url.toString().includes('/manage/newpost') && !url.toString().includes('/manage/post');
            }, { timeout: 15000 });
            console.log('Redirected to new post URL:', page.url());
        } catch (e) {
            console.log('Warning: No redirect detected. Checking for success message...');
            await page.screenshot({ path: 'after-publish-timeout.png' });
        }

        console.log('Published successfully!');
        await page.waitForTimeout(3000); // Verify time

    } catch (error) {
        console.error('Error during automation:', error);
        // Take screenshot on error
        await page.screenshot({ path: 'error-screenshot.png' });
    } finally {
        await browser.close();
    }
}

postToTistory();
