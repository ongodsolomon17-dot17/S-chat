// Terms and Privacy Policy text. Values come from js/config.js; anything left blank renders as a
// highlighted placeholder so nothing is silently invented. THIS IS A TEMPLATE, NOT LEGAL ADVICE —
// have a qualified lawyer review it for the places where you operate before launch.
import { legalValue, appName } from "../core/util.js";

const L = () => ({
  app: appName(),
  company: legalValue("COMPANY_NAME", "Company / operator name"),
  support: legalValue("SUPPORT_EMAIL", "Support email"),
  privacy: legalValue("PRIVACY_EMAIL", "Privacy contact email"),
  address: legalValue("COMPANY_ADDRESS", "Postal address"),
  law: legalValue("GOVERNING_LAW", "Governing law / jurisdiction"),
  age: legalValue("MINIMUM_AGE", "Minimum age"),
});

export const TERMS_SUMMARY = () => {
  const { app } = L();
  return `<p>Before you create your account, please read the key points of our Terms and Conditions:</p>
  <ul class="legal">
    <li>You must be old enough to use ${app} where you live, and give accurate information.</li>
    <li>You are responsible for your account, your password and what you send.</li>
    <li>No harassment, spam, illegal content, impersonation or abuse. Accounts that break the rules can be suspended.</li>
    <li>Messages are protected in transit, but they are <b>not end-to-end encrypted</b> — see the Privacy Policy.</li>
    <li>You can block and report users, and delete your account at any time.</li>
  </ul>`;
};

export function termsHtml() {
  const t = L();
  return `<div class="legal">
<p class="muted small">Template provided with the software. The operator and a qualified lawyer must review it before it is relied on.</p>
<h3>1. Agreement</h3>
<p>These Terms and Conditions ("Terms") are an agreement between you and ${t.company} ("we", "us") for your use of ${t.app} (the "Service"). By ticking the agreement box and creating an account, or by using the Service, you accept these Terms and our Privacy Policy. If you do not accept them, do not use the Service.</p>
<h3>2. Eligibility</h3>
<p>You must be at least ${t.age} years old, or the minimum age required in your country to consent to use an online messaging service, whichever is higher. You must be able to form a binding agreement and must not be barred from using the Service under any applicable law.</p>
<h3>3. Your account</h3>
<ul><li>Provide accurate registration information and keep it up to date.</li><li>Choose a unique username. Usernames that impersonate others, the Service or its staff, or that are offensive, may be refused or removed.</li><li>Keep your password confidential. You are responsible for activity under your account. Tell us promptly if you suspect unauthorised access.</li><li>One person per account. You may not sell, transfer or share your account.</li></ul>
<h3>4. Chat links and public profile</h3>
<p>Each account has a personal chat link of the form <code>/chat/your-username</code>. Anyone who opens the link can see your username, profile picture and short bio. Signed-in visitors can send a friend request. You can switch your chat link off in Settings. Do not put sensitive personal information in your bio or picture.</p>
<h3>5. Acceptable use</h3>
<p>You agree not to use the Service to:</p>
<ul><li>harass, threaten, bully, defame or discriminate against anyone;</li><li>send spam, scams, phishing, malware or unsolicited bulk messages;</li><li>share unlawful content, including content that exploits or endangers children, or that promotes violence or terrorism;</li><li>share another person's private information or intimate images without consent;</li><li>infringe intellectual-property or privacy rights;</li><li>probe, scan, overload or attempt to break the security of the Service, scrape it, or bypass rate limits or access controls;</li><li>create accounts by automated means or evade a suspension or block.</li></ul>
<h3>6. Your content</h3>
<p>You own the messages, files and other content you send ("Your Content"). You grant us a limited licence to store, transmit and display Your Content solely to operate the Service for you and the people you communicate with. You are responsible for Your Content and confirm you have the rights needed to share it. When you delete a message for everyone, it is removed from the other person's view; copies the other person already made (for example, screenshots) are outside our control.</p>
<h3>7. Attachments and calls</h3>
<p>Files you upload are stored with our file-storage provider and are served by link. Do not upload anything you are not comfortable having stored on third-party infrastructure. Voice and video calls are set up through our servers and, where possible, connect your devices directly; relay (TURN) servers may be used when a direct connection is not possible. You must obtain any consent required by law before recording any call.</p>
<h3>8. Blocking, reporting and moderation</h3>
<p>You can block users and report users or messages. When you report a message, the reported message text and the details you provide are made available to our moderators. We may remove content, suspend or terminate accounts, and cooperate with lawful requests from authorities. We are not obliged to monitor all content and do not guarantee that every report will lead to action.</p>
<h3>9. Privacy</h3>
<p>Our Privacy Policy explains what we collect and why. By using the Service you acknowledge it.</p>
<h3>10. Availability and changes to the Service</h3>
<p>We aim to keep the Service available but do not guarantee uninterrupted or error-free operation. We may change, suspend or discontinue features, with or without notice where reasonably necessary.</p>
<h3>11. Suspension and termination</h3>
<p>You may delete your account at any time in Settings. We may suspend or terminate your access if you breach these Terms, if required by law, or to protect users or the Service. Sections that by their nature should survive termination will survive.</p>
<h3>12. Intellectual property</h3>
<p>The Service, its software, design and branding belong to ${t.company} or its licensors. These Terms do not transfer any ownership to you. You may not copy, modify or reverse-engineer the Service except as the law allows.</p>
<h3>13. Disclaimers</h3>
<p>To the maximum extent permitted by law, the Service is provided "as is" and "as available" without warranties of any kind, express or implied, including fitness for a particular purpose and non-infringement. Emergency calls cannot be placed through the Service.</p>
<h3>14. Limitation of liability</h3>
<p>To the maximum extent permitted by law, we are not liable for indirect, incidental, special or consequential damages, or for loss of data, profits or goodwill, arising from your use of the Service. Nothing in these Terms excludes liability that cannot be excluded by law. <span class="placeholder">[Operator and lawyer to confirm the liability cap and local consumer-law carve-outs]</span></p>
<h3>15. Changes to these Terms</h3>
<p>We may update these Terms. When we make a material change we will change the version number and ask you to accept the new version before continuing. Continuing to use the Service after notice of a change means you accept it.</p>
<h3>16. Governing law and disputes</h3>
<p>These Terms are governed by ${t.law}. <span class="placeholder">[Operator and lawyer to confirm dispute-resolution and venue provisions]</span></p>
<h3>17. Contact</h3>
<p>${t.company}<br>Support: ${t.support}<br>Address: ${t.address}</p>
</div>`;
}

export function privacyHtml() {
  const t = L();
  return `<div class="legal">
<div class="alert info">This policy is a template that describes how this software behaves. The operator must confirm the highlighted items and have the policy reviewed by a qualified lawyer. It makes no claim of compliance with any specific law.</div>
<h3>1. Who we are</h3>
<p>${t.company} ("we") operates ${t.app}. Contact for privacy questions: ${t.privacy}. Address: ${t.address}.</p>
<h3>2. Information we collect</h3>
<ul>
<li><b>Account information:</b> username, email address, password (stored only as a salted hash), optional phone number, public ID, optional bio and profile picture, role, and account timestamps.</li>
<li><b>Consent records:</b> the version of the Terms and Privacy Policy you accepted and when.</li>
<li><b>Messages and activity:</b> message text, attachments you send, replies, reactions, read/delivered times, edits and deletions, friend requests, blocks and reports, and call records (who called whom, type, status and times).</li>
<li><b>Technical data:</b> IP address and browser information as processed by our hosting providers and for rate limiting and security; online/typing status shown to your friends while you are connected.</li>
</ul>
<h3>3. How we use information</h3>
<ul><li>To create and secure your account and deliver messages, files and calls.</li><li>To show your friends whether you are online or typing.</li><li>To prevent abuse, enforce our Terms, handle reports and keep the Service secure.</li><li>To keep records of your acceptance of our Terms.</li><li>To respond to you and to meet legal obligations.</li></ul>
<h3>4. Messages and account privacy</h3>
<p><b>Messages are encrypted in transit (HTTPS/WSS) but are not end-to-end encrypted.</b> They are stored on our database in readable form so that history, search and moderation work. People with administrative access to the database or servers could technically read them; we limit access to what is needed to operate the Service. Only you and the other participant can read a conversation through the app, and other users cannot read your conversations. When you report a message, its text is shown to moderators.</p>
<p>Your public profile (username, picture, bio) is visible to anyone who opens your chat link unless you disable the link in Settings. Your email and phone number are never shown on your public profile.</p>
<h3>5. Uploaded files</h3>
<p>Attachments and profile pictures are stored with our storage provider and are delivered by URL. <span class="placeholder">[Operator to confirm: whether the storage bucket is public — if so, anyone who has a file's link can open it]</span></p>
<h3>6. Cookies and similar technologies</h3>
<ul><li><b>Refresh-token cookie (essential):</b> an HttpOnly cookie that keeps you signed in. It is not readable by scripts.</li><li><b>Session storage (essential):</b> a short-lived access token held in your browser tab for the current session.</li><li><b>Local storage (preference):</b> your light/dark theme choice.</li></ul>
<p>We do not use advertising or analytics cookies in the software as delivered. <span class="placeholder">[Operator to update this section if analytics or ads are added]</span></p>
<h3>7. Service providers (third parties)</h3>
<p>We use providers to run the Service. They process data on our behalf under their own terms:</p>
<ul><li>Supabase — primary database hosting and file storage.</li><li>Neon — a backup copy of the database used as a read-only fallback if the primary is unavailable.</li><li>Render — backend hosting.</li><li>Vercel (or another static host chosen by the operator) — website hosting.</li><li>Google STUN servers, and a TURN relay if the operator configures one — to set up calls.</li><li>Optional, only if enabled by the operator: Resend (email delivery), Google Gemini (AI assistant). <span class="placeholder">[Operator to delete any provider not actually used]</span></li></ul>
<p>Data may be processed in countries other than yours. <span class="placeholder">[Operator to state hosting regions and any international-transfer safeguards]</span></p>
<h3>8. How long we keep information</h3>
<p>We keep account data while your account is active. When you delete your account, your profile is removed from search and sign-in is disabled; messages you exchanged may remain visible to the other participant as part of their history. <span class="placeholder">[Operator to confirm retention periods and any scheduled permanent deletion]</span></p>
<h3>9. Security</h3>
<p>We use password hashing, short-lived access tokens, rate limiting, input validation and access checks on every request. No system is perfectly secure, and we cannot guarantee absolute security.</p>
<h3>10. Your rights and choices</h3>
<ul><li>View and edit your profile, bio and picture in Settings.</li><li>Turn your chat link on or off; block or remove users.</li><li>Delete your account in Settings.</li><li>Ask us for access to, correction, export or deletion of your data by contacting ${t.privacy}. Depending on where you live, you may have additional legal rights. <span class="placeholder">[Operator to describe how requests are verified and answered, and which legal rights apply]</span></li></ul>
<h3>11. Children</h3>
<p>The Service is not intended for people under ${t.age}. If we learn that we hold an account that does not meet the age requirement we may remove it.</p>
<h3>12. Changes to this policy</h3>
<p>If we make material changes we will update the version and ask you to review and accept it.</p>
<h3>13. Contact</h3>
<p>${t.company} — ${t.privacy} — ${t.address}</p>
</div>`;
}
